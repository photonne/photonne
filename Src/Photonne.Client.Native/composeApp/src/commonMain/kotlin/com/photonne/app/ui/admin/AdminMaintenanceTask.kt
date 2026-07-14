package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.admin_maintenance_action_empty_trash
import com.photonne.app.resources.admin_maintenance_action_missing
import com.photonne.app.resources.admin_maintenance_action_orphans
import com.photonne.app.resources.admin_maintenance_action_purge_missing
import com.photonne.app.resources.admin_maintenance_action_recalculate
import com.photonne.app.resources.admin_maintenance_desc_empty_trash
import com.photonne.app.resources.admin_maintenance_desc_missing
import com.photonne.app.resources.admin_maintenance_desc_orphans
import com.photonne.app.resources.admin_maintenance_desc_purge_missing
import com.photonne.app.resources.admin_maintenance_desc_recalculate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

/** Result of the most recent finished run of a maintenance action, surfaced
 *  from the server's finished [com.photonne.app.data.models.BackgroundTaskDto]. */
data class MaintenanceResultUi(val message: String, val success: Boolean)

data class AdminMaintenanceUiState(
    // Slug of the maintenance action currently running server-side (from the
    // task's `parameters["kind"]`), null when nothing is running.
    val runningKind: String? = null,
    val runningTaskId: String? = null,
    val progress: Double = 0.0,        // 0..100
    val progressMessage: String = "",
    // Kinds the client is optimistically triggering, before the server task
    // shows up in the registry — keeps the button disabled during the gap.
    val triggering: Set<String> = emptySet(),
    val cancelling: Boolean = false,
    val lastResults: Map<String, MaintenanceResultUi> = emptyMap(),
    val errorMessage: String? = null,
)

/**
 * Maintenance actions no longer run as one long synchronous POST (which tripped
 * the client's 30 s socket timeout on large libraries). Instead [run] triggers a
 * background task on the server (`GET /api/admin/maintenance/{kind}/stream`) and
 * drops the connection; progress is then read by polling `/api/tasks`, exactly
 * like the "Ejecutar tareas" hub. This makes the work survive navigation and the
 * connection dropping, and shows live progress with a cancel button.
 */
class AdminMaintenanceViewModel(
    private val repository: AdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AdminMaintenanceUiState())
    val state: StateFlow<AdminMaintenanceUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(PollIntervalMs)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    /**
     * Fire-and-forget trigger: open the streaming endpoint just long enough for
     * the server to register the task in [BackgroundTaskManager], then drop the
     * connection. [withTimeoutOrNull] bounds the wait because the first NDJSON
     * event may lag behind the initial table scan.
     */
    fun run(kind: String) {
        if (_state.value.runningKind != null || _state.value.triggering.isNotEmpty()) return
        _state.update { it.copy(triggering = it.triggering + kind, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(TriggerTimeoutMs) {
                    repository.maintenanceStream(kind).take(1).collect {}
                }
            }.onFailure { error ->
                _state.update { it.copy(errorMessage = error.message ?: "Maintenance task failed") }
            }
            refresh()
            _state.update { it.copy(triggering = it.triggering - kind) }
            startPolling()
        }
    }

    fun cancel() {
        val id = _state.value.runningTaskId ?: return
        _state.update { it.copy(cancelling = true) }
        viewModelScope.launch {
            runCatching { repository.cancelBackgroundTask(id) }
            refresh()
            _state.update { it.copy(cancelling = false) }
        }
    }

    /** Reads the maintenance tasks from `/api/tasks`: reflects the running one as
     *  live progress and records the latest finished result per kind. This both
     *  drives the progress bar and re-attaches after the user navigates back. */
    private suspend fun refresh() {
        val tasks = runCatching { repository.listBackgroundTasks() }.getOrNull() ?: return
        val maintenance = tasks.filter { it.type == "Maintenance" }

        val results = _state.value.lastResults.toMutableMap()
        maintenance.filter { !it.isRunning }.forEach { dto ->
            val kind = dto.parameters["kind"] ?: return@forEach
            results[kind] = MaintenanceResultUi(
                message = dto.lastMessage,
                success = dto.status == "Completed"
            )
        }

        val running = maintenance.firstOrNull { it.isRunning }
        _state.update {
            it.copy(
                runningKind = running?.parameters?.get("kind"),
                runningTaskId = running?.id,
                progress = running?.percentage ?: 0.0,
                progressMessage = running?.lastMessage ?: "",
                lastResults = results
            )
        }
    }

    private companion object {
        const val PollIntervalMs = 2_000L
        const val TriggerTimeoutMs = 8_000L
    }
}

private data class MaintenanceAction(
    val kind: String,
    val titleResId: org.jetbrains.compose.resources.StringResource,
    val descriptionResId: org.jetbrains.compose.resources.StringResource
)

@Composable
fun AdminMaintenanceScreen(viewModel: AdminMaintenanceViewModel) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    val actions = listOf(
        MaintenanceAction(
            "orphan-thumbnails",
            Res.string.admin_maintenance_action_orphans,
            Res.string.admin_maintenance_desc_orphans
        ),
        MaintenanceAction(
            "missing-files",
            Res.string.admin_maintenance_action_missing,
            Res.string.admin_maintenance_desc_missing
        ),
        MaintenanceAction(
            "recalculate-sizes",
            Res.string.admin_maintenance_action_recalculate,
            Res.string.admin_maintenance_desc_recalculate
        ),
        MaintenanceAction(
            "empty-trash",
            Res.string.admin_maintenance_action_empty_trash,
            Res.string.admin_maintenance_desc_empty_trash
        ),
        MaintenanceAction(
            "purge-missing",
            Res.string.admin_maintenance_action_purge_missing,
            Res.string.admin_maintenance_desc_purge_missing
        )
    )

    val busy = state.runningKind != null || state.triggering.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        actions.forEach { action ->
            val isThisRunning = state.runningKind == action.kind || action.kind in state.triggering
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(action.titleResId),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(action.descriptionResId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isThisRunning) {
                        Spacer(Modifier.size(12.dp))
                        val fraction = (state.progress / 100.0).coerceIn(0.0, 1.0).toFloat()
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.progressMessage.isNotBlank()) {
                            Spacer(Modifier.size(6.dp))
                            Text(
                                state.progressMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        state.lastResults[action.kind]?.let { result ->
                            Spacer(Modifier.size(8.dp))
                            if (result.message.isNotBlank()) {
                                Text(
                                    result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.success) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.runningKind == action.kind && state.runningTaskId != null) {
                            OutlinedButton(
                                onClick = { viewModel.cancel() },
                                enabled = !state.cancelling
                            ) {
                                Text(stringResource(Res.string.action_cancel))
                            }
                        } else {
                            Button(
                                onClick = { viewModel.run(action.kind) },
                                enabled = !busy
                            ) {
                                Text(stringResource(action.titleResId))
                            }
                        }
                    }
                }
            }
        }
    }
}
