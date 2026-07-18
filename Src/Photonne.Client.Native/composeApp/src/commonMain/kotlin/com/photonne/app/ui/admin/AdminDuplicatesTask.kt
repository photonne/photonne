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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.DuplicatesStreamEvent
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_duplicates_action_cancel
import com.photonne.app.resources.admin_duplicates_action_start
import com.photonne.app.resources.admin_duplicates_cleanup
import com.photonne.app.resources.admin_duplicates_completed
import com.photonne.app.resources.admin_duplicates_physical
import com.photonne.app.resources.admin_duplicates_stats_assets
import com.photonne.app.resources.admin_duplicates_stats_groups
import com.photonne.app.resources.admin_duplicates_stats_reclaimed
import com.photonne.app.resources.admin_duplicates_stats_removed
import com.photonne.app.resources.admin_duplicates_stats_total
import com.photonne.app.resources.admin_duplicates_stats_unindexed
import com.photonne.app.resources.admin_task_chip_completed
import com.photonne.app.resources.admin_task_chip_running
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.stringResource

data class AdminDuplicatesUiState(
    val cleanup: Boolean = false,
    val physical: Boolean = false,
    val isRunning: Boolean = false,
    val lastEvent: DuplicatesStreamEvent? = null,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val errorMessage: String? = null
)

class AdminDuplicatesViewModel(
    private val repository: AdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AdminDuplicatesUiState())
    val state: StateFlow<AdminDuplicatesUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun setCleanup(value: Boolean) {
        _state.update { it.copy(cleanup = value) }
    }

    fun setPhysical(value: Boolean) {
        _state.update { it.copy(physical = value) }
    }

    fun start() {
        if (_state.value.isRunning) return
        val cleanup = _state.value.cleanup
        val physical = _state.value.physical
        _state.update {
            it.copy(
                isRunning = true,
                errorMessage = null,
                lastEvent = null,
                startedAtMs = Clock.System.now().toEpochMilliseconds(),
                finishedAtMs = null
            )
        }
        job = viewModelScope.launch {
            try {
                repository.duplicatesStream(cleanup = cleanup, physical = physical)
                    .collect { event -> _state.update { it.copy(lastEvent = event) } }
                _state.update {
                    it.copy(
                        isRunning = false,
                        finishedAtMs = Clock.System.now().toEpochMilliseconds()
                    )
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        isRunning = false,
                        errorMessage = e.message ?: "No se pudo completar el escaneo de duplicados",
                        finishedAtMs = Clock.System.now().toEpochMilliseconds()
                    )
                }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update {
            it.copy(
                isRunning = false,
                finishedAtMs = Clock.System.now().toEpochMilliseconds()
            )
        }
    }
}

@Composable
fun AdminDuplicatesScreen(viewModel: AdminDuplicatesViewModel) {
    val state by viewModel.state.collectAsState()

    var nowMs by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(state.isRunning) {
        while (state.isRunning) {
            nowMs = Clock.System.now().toEpochMilliseconds()
            delay(1000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + floatingNavBarReservedHeight()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_duplicates_physical),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.physical,
                onCheckedChange = viewModel::setPhysical,
                enabled = !state.isRunning
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_duplicates_cleanup),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.cleanup,
                onCheckedChange = viewModel::setCleanup,
                enabled = !state.isRunning
            )
        }

        val event = state.lastEvent
        val isCompleted = event?.isCompleted == true && !state.isRunning
        val pct = (event?.percentage ?: 0.0).toFloat().coerceIn(0f, 100f) / 100f

        val startMs = state.startedAtMs
        val endMs = state.finishedAtMs ?: nowMs
        val elapsedSeconds = startMs?.let { ((endMs - it) / 1000L).coerceAtLeast(0L) } ?: 0L

        val statusText = when {
            isCompleted -> stringResource(Res.string.admin_duplicates_completed)
            event != null -> event.message
            else -> "—"
        }

        TaskProgressCard(
            statusText = statusText,
            progress = pct,
            chips = if (state.isRunning || event != null) {
                {
                    MetricPillRow {
                        if (startMs != null) {
                            MetricPill(formatDurationSeconds(elapsedSeconds))
                        }
                        if (state.isRunning) {
                            MetricPill(
                                label = stringResource(Res.string.admin_task_chip_running),
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else if (isCompleted) {
                            MetricPill(
                                label = stringResource(Res.string.admin_task_chip_completed),
                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                content = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            } else null
        )

        event?.statistics?.let { stats ->
            val items = listOfNotNull(
                StatGridItem(
                    label = stringResource(Res.string.admin_duplicates_stats_total),
                    value = (stats.totalAssets ?: 0).toString()
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_duplicates_stats_groups),
                    value = (stats.duplicateGroups ?: 0).toString(),
                    valueColor = MaterialTheme.colorScheme.secondary
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_duplicates_stats_assets),
                    value = (stats.duplicateAssets ?: 0).toString(),
                    valueColor = MaterialTheme.colorScheme.tertiary
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_duplicates_stats_removed),
                    value = (stats.removed ?: 0).toString()
                )
            )
            StatGridCard(items = items)
            // Bytes reclaimed and unindexed counts get their own rows so
            // the reclaimed-string fits comfortably without truncation.
            stats.bytesReclaimed?.let { bytes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_duplicates_stats_reclaimed),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(humanBytes(bytes), style = MaterialTheme.typography.bodyMedium)
                }
            }
            stats.unindexedFiles?.let { unindexed ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_duplicates_stats_unindexed),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(unindexed.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = viewModel::cancel) {
                    Text(stringResource(Res.string.admin_duplicates_action_cancel))
                }
            } else {
                Button(onClick = viewModel::start) {
                    Text(stringResource(Res.string.admin_duplicates_action_start))
                }
            }
        }
    }
}
