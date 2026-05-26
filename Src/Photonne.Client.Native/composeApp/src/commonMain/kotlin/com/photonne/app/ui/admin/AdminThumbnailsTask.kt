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
import com.photonne.app.data.models.ThumbnailStreamEvent
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_task_chip_completed
import com.photonne.app.resources.admin_task_chip_eta
import com.photonne.app.resources.admin_task_chip_running
import com.photonne.app.resources.admin_task_speed_assets
import com.photonne.app.resources.admin_thumbnails_action_cancel
import com.photonne.app.resources.admin_thumbnails_action_start
import com.photonne.app.resources.admin_thumbnails_completed
import com.photonne.app.resources.admin_thumbnails_regenerate
import com.photonne.app.resources.admin_thumbnails_stats_failed
import com.photonne.app.resources.admin_thumbnails_stats_generated
import com.photonne.app.resources.admin_thumbnails_stats_processed
import com.photonne.app.resources.admin_thumbnails_stats_skipped
import com.photonne.app.resources.admin_thumbnails_stats_total
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource

data class AdminThumbnailsUiState(
    val isRunning: Boolean = false,
    val regenerate: Boolean = false,
    val lastEvent: ThumbnailStreamEvent? = null,
    val startedAtMs: Long? = null,
    val finishedAtMs: Long? = null,
    val errorMessage: String? = null,
    val taskId: String? = null
)

class AdminThumbnailsViewModel(
    private val repository: AdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AdminThumbnailsUiState())
    val state: StateFlow<AdminThumbnailsUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        // Re-attach to a server-side thumbnail task if one is still running
        // when the screen is first opened. Mirrors AdminIndexAssetsViewModel.
        refresh()
    }

    /** See [AdminIndexAssetsViewModel.refresh]. */
    fun refresh() {
        if (_state.value.isRunning) return
        job = viewModelScope.launch { reconnectIfRunning() }
    }

    private suspend fun reconnectIfRunning() {
        val running = repository.findRunningTaskOfType("Thumbnails") ?: return
        if (_state.value.isRunning) return

        val startMs = runCatching { Instant.parse(running.startedAt).toEpochMilliseconds() }
            .getOrDefault(Clock.System.now().toEpochMilliseconds())

        // The "regenerate" flag is also persisted in task.parameters so the
        // toggle stays in sync with what's actually running on the server.
        val regenerate = running.parameters["regenerate"]?.equals("true", ignoreCase = true)
            ?: _state.value.regenerate

        _state.update {
            it.copy(
                isRunning = true,
                regenerate = regenerate,
                errorMessage = null,
                lastEvent = ThumbnailStreamEvent(
                    message = running.lastMessage,
                    percentage = running.percentage,
                    statistics = null
                ),
                startedAtMs = startMs,
                finishedAtMs = null,
                taskId = running.id
            )
        }
        collectStream(repository.resumeThumbnailsTaskStream(running.id), taskId = running.id)
    }

    fun setRegenerate(value: Boolean) {
        _state.update { it.copy(regenerate = value) }
    }

    fun start() {
        if (_state.value.isRunning) return
        val regenerate = _state.value.regenerate
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
        job = viewModelScope.launch {
            collectStream(
                repository.thumbnailsStream(regenerate = regenerate),
                taskId = null
            )
        }
    }

    private suspend fun collectStream(
        stream: Flow<ThumbnailStreamEvent>,
        taskId: String?
    ) {
        if (taskId != null) _state.update { it.copy(taskId = taskId) }
        try {
            stream.collect { event ->
                _state.update {
                    it.copy(
                        lastEvent = event,
                        taskId = it.taskId ?: event.taskId
                    )
                }
            }
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
                    errorMessage = e.message ?: "Thumbnail generation failed",
                    finishedAtMs = Clock.System.now().toEpochMilliseconds()
                )
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
fun AdminThumbnailsScreen(viewModel: AdminThumbnailsViewModel) {
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
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_thumbnails_regenerate),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.regenerate,
                onCheckedChange = viewModel::setRegenerate,
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
            isCompleted -> stringResource(Res.string.admin_thumbnails_completed)
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
                            MetricPill(stringResource(Res.string.admin_task_speed_assets, v))
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
                    label = stringResource(Res.string.admin_thumbnails_stats_processed),
                    value = (stats.processed ?: 0).toString()
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_thumbnails_stats_generated),
                    value = (stats.generated ?: 0).toString(),
                    valueColor = MaterialTheme.colorScheme.tertiary
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_thumbnails_stats_skipped),
                    value = (stats.skipped ?: 0).toString(),
                    valueColor = MaterialTheme.colorScheme.secondary
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_thumbnails_stats_failed),
                    value = (stats.failed ?: 0).toString(),
                    valueColor = if ((stats.failed ?: 0) > 0) MaterialTheme.colorScheme.error
                    else null
                )
            )
            StatGridCard(items = items)
            // The "Total" counter is useful but secondary; render it as a
            // single full-width row so the 4-up grid stays balanced.
            stats.totalAssets?.let { totalAssets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_thumbnails_stats_total),
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
                    Text(stringResource(Res.string.admin_thumbnails_action_cancel))
                }
            } else {
                Button(onClick = viewModel::start) {
                    Text(stringResource(Res.string.admin_thumbnails_action_start))
                }
            }
        }
    }
}
