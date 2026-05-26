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
import com.photonne.app.data.models.IndexStreamEvent
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_index_action_cancel
import com.photonne.app.resources.admin_index_action_start
import com.photonne.app.resources.admin_index_completed
import com.photonne.app.resources.admin_index_started
import com.photonne.app.resources.admin_index_stats_new
import com.photonne.app.resources.admin_index_stats_thumbnails
import com.photonne.app.resources.admin_index_stats_total
import com.photonne.app.resources.admin_index_stats_updated
import com.photonne.app.resources.admin_task_chip_completed
import com.photonne.app.resources.admin_task_chip_eta
import com.photonne.app.resources.admin_task_chip_running
import com.photonne.app.resources.admin_task_speed_files
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

private const val PollFallbackIntervalMs = 10_000L

data class AdminIndexUiState(
    val isRunning: Boolean = false,
    val lastEvent: IndexStreamEvent? = null,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val errorMessage: String? = null,
    // Server-side task id. Set when we either start a new run (captured
    // from the first stream event) or reconnect to a running task on
    // init. Lets cancel() actually stop the task on the server instead
    // of just closing the local flow.
    val taskId: String? = null
)

class AdminIndexAssetsViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminIndexUiState())
    val state: StateFlow<AdminIndexUiState> = _state.asStateFlow()
    // Real-time NDJSON subscription. Provides rich `IndexStreamStatistics`
    // when alive but may sit idle for long stretches during the scan phase.
    private var streamJob: Job? = null
    // Periodic GET /api/tasks fallback that keeps `lastMessage` + `percentage`
    // moving even when the stream is silent or has dropped (proxy idle
    // cut-off, server-side worker hung on a slow scan). Runs in parallel
    // with the stream; whichever updates first wins.
    private var pollJob: Job? = null

    init {
        refresh()
    }

    /**
     * Re-checks the server for a running index task and attaches if found.
     * Called from `init` (covers cold start) and from the screen's
     * `LaunchedEffect` (covers tasks started from another client after
     * the VM was already alive). Idempotent.
     */
    fun refresh() {
        if (_state.value.isRunning) return
        streamJob = viewModelScope.launch { reconnectIfRunning() }
    }

    private suspend fun reconnectIfRunning() {
        val running = repository.findRunningTaskOfType("IndexAssets") ?: return
        if (_state.value.isRunning) return

        val startMs = runCatching { Instant.parse(running.startedAt).toEpochMilliseconds() }
            .getOrDefault(Clock.System.now().toEpochMilliseconds())

        _state.update {
            it.copy(
                isRunning = true,
                errorMessage = null,
                lastEvent = IndexStreamEvent(
                    message = running.lastMessage,
                    percentage = running.percentage,
                    statistics = null
                ),
                startedAtMs = startMs,
                finishedAtMs = null,
                taskId = running.id
            )
        }
        // Start the DTO poll *before* attaching the stream so the UI keeps
        // refreshing even if the stream connection never establishes (or
        // dies a few seconds in to an idle network path).
        startDtoPolling(running.id)
        collectStream(repository.resumeIndexTaskStream(running.id), taskId = running.id)
    }

    fun start() {
        if (_state.value.isRunning) return
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
            collectStream(repository.indexStream(), taskId = null)
        }
    }

    /**
     * Polls `/api/tasks` every 10 s and keeps the UI's message/percentage
     * in sync with the server-side DTO. Stops itself when the task is no
     * longer Running or has been cleaned up (>1 h after finish). Doesn't
     * touch statistics — only the stream carries that level of detail.
     */
    private fun startDtoPolling(taskId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(PollFallbackIntervalMs)
                val dto = runCatching { repository.listBackgroundTasks() }
                    .getOrNull()
                    ?.firstOrNull { it.id == taskId }
                if (dto == null) break
                _state.update {
                    val prev = it.lastEvent ?: IndexStreamEvent()
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

    private suspend fun collectStream(stream: Flow<IndexStreamEvent>, taskId: String?) {
        if (taskId != null) _state.update { it.copy(taskId = taskId) }
        try {
            stream.collect { event ->
                _state.update {
                    it.copy(
                        lastEvent = event,
                        taskId = it.taskId ?: event.taskId
                    )
                }
                // Fresh `start()` doesn't pre-launch the poll. Once we
                // know the taskId, kick it off so the UI stays current
                // even if this stream later drops.
                if (pollJob == null) {
                    _state.value.taskId?.let { startDtoPolling(it) }
                }
            }
            // Stream completed normally — task finished. Stop polling.
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
            // If the poll fallback is running we know the server still
            // tracks the task; keep the hydrated state visible and let
            // the poll continue feeding updates. Otherwise this was a
            // fresh start that never got off the ground.
            if (pollJob?.isActive == true) {
                _state.update {
                    it.copy(errorMessage = e.message ?: "Stream disconnected; using polling fallback")
                }
            } else {
                _state.update {
                    it.copy(
                        isRunning = false,
                        errorMessage = e.message ?: "Indexing failed",
                        finishedAtMs = Clock.System.now().toEpochMilliseconds()
                    )
                }
            }
        }
    }

    fun cancel() {
        // Local cancel only closes our flow subscription; the server keeps
        // working. Hit DELETE /api/tasks/{id} so the actual task stops too.
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
fun AdminIndexAssetsScreen(viewModel: AdminIndexAssetsViewModel) {
    val state by viewModel.state.collectAsState()

    // Re-check on every screen entry: if a run was started elsewhere (PWA,
    // another device) since this VM was last active, hydrate from the server.
    LaunchedEffect(viewModel) { viewModel.refresh() }

    // Tick once a second while the task is running so the elapsed / ETA
    // chips stay live; we don't keep ticking once it's finished.
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
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        val event = state.lastEvent
        val isCompleted = event?.isCompleted == true && !state.isRunning
        val pct = (event?.percentage ?: 0.0).toFloat().coerceIn(0f, 100f) / 100f

        // Elapsed / speed / ETA — derived from start time, current tick
        // and the latest stream event.
        val startMs = state.startedAtMs
        val endMs = state.finishedAtMs ?: nowMs
        val elapsedSeconds = startMs?.let { ((endMs - it) / 1000L).coerceAtLeast(0L) } ?: 0L
        val processed = event?.statistics?.totalFilesFound ?: 0
        val speed = if (elapsedSeconds > 0) processed.toDouble() / elapsedSeconds else 0.0
        val etaSeconds = if (state.isRunning && pct in 0.001f..0.999f && elapsedSeconds > 0) {
            (elapsedSeconds / pct - elapsedSeconds).toLong()
        } else 0L
        val speedFmt = formatRate(speed, "")
        val etaFmt = if (etaSeconds > 0) formatDurationSeconds(etaSeconds) else null

        val statusText = when {
            isCompleted -> stringResource(Res.string.admin_index_completed)
            event != null -> event.message
            state.isRunning -> stringResource(Res.string.admin_index_started)
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
                        speedFmt?.let { v ->
                            MetricPill(stringResource(Res.string.admin_task_speed_files, v))
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
                    label = stringResource(Res.string.admin_index_stats_total),
                    value = (stats.totalFilesFound ?: 0).toString()
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_index_stats_new),
                    value = (stats.newFiles ?: 0).toString(),
                    valueColor = MaterialTheme.colorScheme.tertiary
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_index_stats_updated),
                    value = (stats.updatedFiles ?: 0).toString(),
                    valueColor = MaterialTheme.colorScheme.secondary
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_index_stats_thumbnails),
                    value = ((stats.thumbnailsGenerated ?: 0) +
                        (stats.thumbnailsRegenerated ?: 0)).toString()
                )
            )
            StatGridCard(items = items)
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
                    Text(stringResource(Res.string.admin_index_action_cancel))
                }
            } else {
                Button(onClick = viewModel::start) {
                    Text(stringResource(Res.string.admin_index_action_start))
                }
            }
        }
    }
}
