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
import com.photonne.app.data.models.MetadataStreamEvent
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_metadata_action_cancel
import com.photonne.app.resources.admin_metadata_action_start
import com.photonne.app.resources.admin_metadata_completed
import com.photonne.app.resources.admin_metadata_overwrite
import com.photonne.app.resources.admin_metadata_stats_extracted
import com.photonne.app.resources.admin_metadata_stats_failed
import com.photonne.app.resources.admin_metadata_stats_processed
import com.photonne.app.resources.admin_metadata_stats_skipped
import com.photonne.app.resources.admin_metadata_stats_total
import com.photonne.app.resources.admin_metadata_task_explanation
import com.photonne.app.resources.admin_task_chip_completed
import com.photonne.app.resources.admin_task_chip_eta
import com.photonne.app.resources.admin_task_chip_running
import com.photonne.app.resources.admin_task_speed_metadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource

private const val MetadataPollFallbackIntervalMs = 10_000L

data class AdminMetadataUiState(
    val isRunning: Boolean = false,
    val overwrite: Boolean = false,
    val lastEvent: MetadataStreamEvent? = null,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val errorMessage: String? = null,
    val taskId: String? = null
)

/**
 * Drives the standalone metadata extraction screen. Mirrors
 * [AdminThumbnailsViewModel]: an `overwrite` toggle decides whether to
 * re-extract EXIF for assets that already have it, and the VM both
 * launches a fresh run and re-attaches to a server-side task that is
 * already in flight (started in a previous session, from the PWA, or
 * from another device).
 */
class AdminMetadataViewModel(
    private val repository: AdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AdminMetadataUiState())
    val state: StateFlow<AdminMetadataUiState> = _state.asStateFlow()
    private var streamJob: Job? = null
    private var pollJob: Job? = null

    init { refresh() }

    /** See [AdminIndexAssetsViewModel.refresh]. */
    fun refresh() {
        if (_state.value.isRunning) return
        streamJob = viewModelScope.launch { reconnectIfRunning() }
    }

    private suspend fun reconnectIfRunning() {
        val running = repository.findRunningTaskOfType("Metadata") ?: return
        if (_state.value.isRunning) return

        val startMs = runCatching { Instant.parse(running.startedAt).toEpochMilliseconds() }
            .getOrDefault(Clock.System.now().toEpochMilliseconds())
        val overwrite = running.parameters["overwrite"]?.equals("true", ignoreCase = true)
            ?: _state.value.overwrite

        _state.update {
            it.copy(
                isRunning = true,
                overwrite = overwrite,
                errorMessage = null,
                lastEvent = MetadataStreamEvent(
                    message = running.lastMessage,
                    percentage = running.percentage,
                    statistics = null
                ),
                startedAtMs = startMs,
                finishedAtMs = null,
                taskId = running.id
            )
        }
        startDtoPolling(running.id)
        collectStream(repository.resumeMetadataTaskStream(running.id), taskId = running.id)
    }

    fun setOverwrite(value: Boolean) {
        _state.update { it.copy(overwrite = value) }
    }

    fun start() {
        if (_state.value.isRunning) return
        val overwrite = _state.value.overwrite
        _state.update {
            it.copy(
                isRunning = true,
                errorMessage = null,
                lastEvent = null,
                startedAtMs = Clock.System.now().toEpochMilliseconds(),
                finishedAtMs = null,
                taskId = null
            )
        }
        streamJob = viewModelScope.launch {
            collectStream(repository.metadataStream(overwrite = overwrite), taskId = null)
        }
    }

    /** See [AdminIndexAssetsViewModel.startDtoPolling]. */
    private fun startDtoPolling(taskId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(MetadataPollFallbackIntervalMs)
                val dto = runCatching { repository.listBackgroundTasks() }
                    .getOrNull()
                    ?.firstOrNull { it.id == taskId }
                if (dto == null) break
                _state.update {
                    val prev = it.lastEvent ?: MetadataStreamEvent()
                    it.copy(
                        isRunning = dto.isRunning,
                        lastEvent = prev.copy(
                            message = dto.lastMessage,
                            percentage = dto.percentage
                        ),
                        finishedAtMs = if (!dto.isRunning && it.finishedAtMs == null)
                            Clock.System.now().toEpochMilliseconds()
                        else it.finishedAtMs
                    )
                }
                if (!dto.isRunning) break
            }
            pollJob = null
        }
    }

    private suspend fun collectStream(stream: Flow<MetadataStreamEvent>, taskId: String?) {
        if (taskId != null) _state.update { it.copy(taskId = taskId) }
        try {
            stream.collect { event ->
                _state.update {
                    it.copy(
                        lastEvent = event,
                        taskId = it.taskId ?: event.taskId
                    )
                }
                if (pollJob == null) {
                    _state.value.taskId?.let { startDtoPolling(it) }
                }
            }
            pollJob?.cancel()
            pollJob = null
            _state.update {
                it.copy(
                    isRunning = false,
                    finishedAtMs = Clock.System.now().toEpochMilliseconds()
                )
            }
        } catch (t: kotlinx.coroutines.CancellationException) {
            throw t
        } catch (e: Throwable) {
            if (pollJob?.isActive == true) {
                _state.update {
                    it.copy(errorMessage = e.message ?: "Stream disconnected; using polling fallback")
                }
            } else {
                _state.update {
                    it.copy(
                        isRunning = false,
                        errorMessage = e.message ?: "Metadata extraction failed",
                        finishedAtMs = Clock.System.now().toEpochMilliseconds()
                    )
                }
            }
        }
    }

    fun cancel() {
        val id = _state.value.taskId
        if (id != null) {
            viewModelScope.launch {
                runCatching { repository.cancelBackgroundTask(id) }
            }
        }
        streamJob?.cancel()
        streamJob = null
        pollJob?.cancel()
        pollJob = null
        _state.update {
            it.copy(
                isRunning = false,
                finishedAtMs = Clock.System.now().toEpochMilliseconds()
            )
        }
    }
}

@Composable
fun AdminMetadataTaskScreen(viewModel: AdminMetadataViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) { viewModel.refresh() }

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(
                stringResource(Res.string.admin_metadata_task_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_metadata_overwrite),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.overwrite,
                onCheckedChange = viewModel::setOverwrite,
                enabled = !state.isRunning
            )
        }

        val event = state.lastEvent
        val isCompleted = event?.isCompleted == true && !state.isRunning
        val pct = (event?.percentage ?: 0.0).toFloat().coerceIn(0f, 100f) / 100f

        val startMs = state.startedAtMs
        val endMs = state.finishedAtMs ?: nowMs
        val elapsedSeconds = startMs?.let { ((endMs - it) / 1000L).coerceAtLeast(0L) } ?: 0L
        val processed = event?.statistics?.processed ?: 0
        val speed = if (elapsedSeconds > 0) processed.toDouble() / elapsedSeconds else 0.0
        val total = event?.statistics?.totalAssets ?: 0
        val remaining = (total - processed).coerceAtLeast(0)
        val etaSeconds = if (state.isRunning && speed > 0 && remaining > 0) {
            (remaining.toDouble() / speed).toLong()
        } else 0L
        val speedFmt = formatRate(speed, "")
        val etaFmt = if (etaSeconds > 0) formatDurationSeconds(etaSeconds) else null

        val failedCount = event?.statistics?.failed ?: 0
        val progressColor = if (failedCount > 0) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary

        val statusText = when {
            isCompleted -> stringResource(Res.string.admin_metadata_completed)
            event != null -> event.message
            else -> "—"
        }

        TaskProgressCard(
            statusText = statusText,
            progress = pct,
            progressColor = progressColor,
            chips = if (state.isRunning || event != null) {
                {
                    MetricPillRow {
                        if (startMs != null) {
                            MetricPill(formatDurationSeconds(elapsedSeconds))
                        }
                        speedFmt?.let { v ->
                            MetricPill(stringResource(Res.string.admin_task_speed_metadata, v))
                        }
                        etaFmt?.let { v ->
                            MetricPill(stringResource(Res.string.admin_task_chip_eta, v))
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
            val items = listOf(
                StatGridItem(
                    label = stringResource(Res.string.admin_metadata_stats_processed),
                    value = (stats.processed ?: 0).toString()
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_metadata_stats_extracted),
                    value = (stats.extracted ?: 0).toString(),
                    valueColor = MaterialTheme.colorScheme.tertiary
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_metadata_stats_skipped),
                    value = (stats.skipped ?: 0).toString(),
                    valueColor = MaterialTheme.colorScheme.secondary
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_metadata_stats_failed),
                    value = (stats.failed ?: 0).toString(),
                    valueColor = if ((stats.failed ?: 0) > 0) MaterialTheme.colorScheme.error
                    else null
                )
            )
            StatGridCard(items = items)
            stats.totalAssets?.let { totalAssets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_metadata_stats_total),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        totalAssets.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                    Text(stringResource(Res.string.admin_metadata_action_cancel))
                }
            } else {
                Button(onClick = viewModel::start) {
                    Text(stringResource(Res.string.admin_metadata_action_start))
                }
            }
        }
    }
}
