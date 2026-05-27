package com.photonne.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.BackgroundTaskDto
import com.photonne.app.data.models.PendingCountResponse
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_run_tasks_action_cancel
import com.photonne.app.resources.admin_run_tasks_action_start
import com.photonne.app.resources.admin_run_tasks_ai_progress_format
import com.photonne.app.resources.admin_run_tasks_ai_subtitle_format
import com.photonne.app.resources.admin_run_tasks_last_run_format
import com.photonne.app.resources.admin_run_tasks_last_run_never
import com.photonne.app.resources.admin_run_tasks_pending_format
import com.photonne.app.resources.admin_run_tasks_section_ai
import com.photonne.app.resources.admin_run_tasks_section_other
import com.photonne.app.resources.admin_run_tasks_section_pipeline
import com.photonne.app.resources.admin_run_tasks_status_in_progress
import com.photonne.app.resources.admin_run_tasks_time_d_format
import com.photonne.app.resources.admin_run_tasks_time_h_format
import com.photonne.app.resources.admin_run_tasks_time_m_format
import com.photonne.app.resources.admin_run_tasks_time_now
import com.photonne.app.resources.admin_system_duplicates
import com.photonne.app.resources.admin_system_duplicates_subtitle
import com.photonne.app.resources.admin_system_embedding
import com.photonne.app.resources.admin_system_embedding_subtitle
import com.photonne.app.resources.admin_system_face
import com.photonne.app.resources.admin_system_face_subtitle
import com.photonne.app.resources.admin_system_index
import com.photonne.app.resources.admin_system_index_subtitle
import com.photonne.app.resources.admin_system_metadata
import com.photonne.app.resources.admin_system_metadata_subtitle
import com.photonne.app.resources.admin_system_object
import com.photonne.app.resources.admin_system_object_subtitle
import com.photonne.app.resources.admin_system_scene
import com.photonne.app.resources.admin_system_scene_subtitle
import com.photonne.app.resources.admin_system_text
import com.photonne.app.resources.admin_system_text_subtitle
import com.photonne.app.resources.admin_system_thumbnails
import com.photonne.app.resources.admin_system_thumbnails_subtitle
import com.photonne.app.ui.charts.LiveDot
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Logical grouping for the Run Tasks hub. Order also drives the visual
 * order on the screen.
 *
 * - [Pipeline]: foundational asset processing — Index → Metadata →
 *   Thumbnails. ML can only run on assets that have completed this
 *   pipeline, so they belong at the top.
 * - [Ai]: the five ML enrichments. Each consumes already-indexed assets.
 * - [Other]: occasional maintenance tasks that aren't part of the
 *   forward pipeline (duplicates today, room for more later).
 */
enum class AdminRunTaskSection { Pipeline, Ai, Other }

/**
 * Catalogue of every runnable processing task in this hub. Each task
 * knows its hub icon, labels, [section], and (for ML) the
 * [AdminBackfillKind] whose `/pending-count` endpoint feeds its inline
 * counter. Entry order matches the visual order within each section.
 */
enum class AdminRunTask(
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val icon: ImageVector,
    val backfillKind: AdminBackfillKind?,
    val section: AdminRunTaskSection,
    // Server-side BackgroundTaskType string for pipeline tasks (matches
    // the enum on `BackgroundTaskManager`). Null for ML / Other tasks
    // that don't register with that manager.
    val backgroundType: String?,
) {
    IndexAssets(
        titleRes = Res.string.admin_system_index,
        subtitleRes = Res.string.admin_system_index_subtitle,
        icon = Icons.Outlined.Sync,
        backfillKind = null,
        section = AdminRunTaskSection.Pipeline,
        backgroundType = "IndexAssets",
    ),
    ExtractMetadata(
        titleRes = Res.string.admin_system_metadata,
        subtitleRes = Res.string.admin_system_metadata_subtitle,
        icon = Icons.Outlined.Info,
        backfillKind = null,
        section = AdminRunTaskSection.Pipeline,
        backgroundType = "Metadata",
    ),
    GenerateThumbnails(
        titleRes = Res.string.admin_system_thumbnails,
        subtitleRes = Res.string.admin_system_thumbnails_subtitle,
        icon = Icons.Outlined.Image,
        backfillKind = null,
        section = AdminRunTaskSection.Pipeline,
        backgroundType = "Thumbnails",
    ),
    FaceRecognition(
        titleRes = Res.string.admin_system_face,
        subtitleRes = Res.string.admin_system_face_subtitle,
        icon = Icons.Outlined.Face,
        backfillKind = AdminBackfillKind.FaceRecognition,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    ObjectDetection(
        titleRes = Res.string.admin_system_object,
        subtitleRes = Res.string.admin_system_object_subtitle,
        icon = Icons.Outlined.Category,
        backfillKind = AdminBackfillKind.ObjectDetection,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    SceneClassification(
        titleRes = Res.string.admin_system_scene,
        subtitleRes = Res.string.admin_system_scene_subtitle,
        icon = Icons.Outlined.Landscape,
        backfillKind = AdminBackfillKind.SceneClassification,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    TextRecognition(
        titleRes = Res.string.admin_system_text,
        subtitleRes = Res.string.admin_system_text_subtitle,
        icon = Icons.Outlined.TextFields,
        backfillKind = AdminBackfillKind.TextRecognition,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    ImageEmbedding(
        titleRes = Res.string.admin_system_embedding,
        subtitleRes = Res.string.admin_system_embedding_subtitle,
        icon = Icons.Outlined.ImageSearch,
        backfillKind = AdminBackfillKind.ImageEmbedding,
        section = AdminRunTaskSection.Ai,
        backgroundType = null,
    ),
    DetectDuplicates(
        titleRes = Res.string.admin_system_duplicates,
        subtitleRes = Res.string.admin_system_duplicates_subtitle,
        icon = Icons.Outlined.ContentCopy,
        backfillKind = null,
        section = AdminRunTaskSection.Other,
        backgroundType = null,
    ),
}

private const val PollIntervalMs = 10_000L

// Maximum time we wait for the streaming endpoint's first event after a
// Start tap before falling through to refresh. The server registers the
// task entry as soon as the request hits the handler, well before any
// data is emitted, so the next /api/tasks poll will reliably see it.
private const val TriggerTimeoutMs = 3_000L

data class AdminRunTasksUiState(
    val pending: Map<AdminRunTask, PendingCountResponse> = emptyMap(),
    /** Every task entry the server reports (running + recently finished).
     *  Used to derive both "running now" rows and "Última ejecución hace X"
     *  subtitles. */
    val backgroundTasks: List<BackgroundTaskDto> = emptyList(),
    /** Distinct count of image assets missing at least one ML completion.
     *  Honest headline figure for the AI section — not a sum of the per-task
     *  counters, which would double-count assets pending several ML jobs. */
    val mlPendingTotal: Int? = null,
    /** Tasks whose inline Start button was just tapped. The row shows an
     *  intermediate "starting" state with a spinner until the next refresh
     *  promotes it to a real running task (or rolls it back to idle if the
     *  request failed silently). */
    val triggering: Set<AdminRunTask> = emptySet(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Drives the hub. Polls three pieces of state every [PollIntervalMs]:
 * per-ML pending counters, the global ML "any missing" total, and the
 * background-task registry (used for both running indicators and
 * last-run timestamps). Also exposes fire-and-forget triggers so the
 * inline Start buttons on each row don't need the detail VMs to be in
 * the composition.
 */
class AdminRunTasksViewModel(
    private val repository: AdminRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminRunTasksUiState())
    val state: StateFlow<AdminRunTasksUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun load() {
        if (_state.value.isLoading) return
        viewModelScope.launch { refresh(showLoading = true) }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            refresh(showLoading = _state.value.pending.isEmpty())
            while (isActive) {
                delay(PollIntervalMs)
                refresh(showLoading = false)
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
     * Starts a pipeline / Other task on the server. Opens the streaming
     * endpoint just long enough for the server to register the task entry
     * in [BackgroundTaskManager], then drops the connection — the worker
     * keeps running independently (on `Task.Run`) and shows up in the
     * next [refresh] via `/api/tasks`.
     *
     * The connection-open uses [withTimeoutOrNull] because some endpoints
     * may delay the first NDJSON event by several seconds (e.g. the
     * metadata extractor scans the assets table before emitting); without
     * a bound, the UI would sit on the optimistic spinner waiting for an
     * event the user doesn't even care about.
     */
    fun triggerTask(task: AdminRunTask) {
        _state.update { it.copy(triggering = it.triggering + task) }
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(TriggerTimeoutMs) {
                    when (task) {
                        AdminRunTask.IndexAssets ->
                            repository.indexStream().take(1).collect {}
                        AdminRunTask.ExtractMetadata ->
                            repository.metadataStream(overwrite = false).take(1).collect {}
                        AdminRunTask.GenerateThumbnails ->
                            repository.thumbnailsStream(regenerate = false).take(1).collect {}
                        AdminRunTask.DetectDuplicates ->
                            repository.duplicatesStream(cleanup = false, physical = false).take(1).collect {}
                        else -> {}
                    }
                }
            }
            refresh(showLoading = false)
            _state.update { it.copy(triggering = it.triggering - task) }
        }
    }

    /** Enqueues an ML backfill. Defaults to "only missing" so the admin
     *  can keep tapping without re-encoding completed assets. */
    fun triggerMlBackfill(task: AdminRunTask) {
        val kind = task.backfillKind?.apiPath ?: return
        _state.update { it.copy(triggering = it.triggering + task) }
        viewModelScope.launch {
            runCatching {
                repository.backfill(kind = kind, batchSize = null, onlyMissing = true)
            }
            refresh(showLoading = false)
            _state.update { it.copy(triggering = it.triggering - task) }
        }
    }

    /** Cancels the matching server-side task. Pipeline / Other rows are
     *  the only ones with a cancel affordance (ML backfills enqueue then
     *  drop the HTTP handle — they finish on their own). */
    fun cancelTask(taskId: String) {
        viewModelScope.launch {
            runCatching { repository.cancelBackgroundTask(taskId) }
            refresh(showLoading = false)
        }
    }

    /** Clears the Pending queue for an ML task type. Jobs already
     *  Processing are owned by workers and run to completion — there's
     *  no safe way to abort an in-flight inference from here. */
    fun cancelMlQueue(task: AdminRunTask) {
        val kind = task.backfillKind?.apiPath ?: return
        viewModelScope.launch {
            runCatching { repository.cancelMlQueue(kind) }
            refresh(showLoading = false)
        }
    }

    private suspend fun refresh(showLoading: Boolean) {
        if (showLoading) _state.update { it.copy(isLoading = true, errorMessage = null) }
        val pendingDeferred: kotlinx.coroutines.Deferred<Map<AdminRunTask, PendingCountResponse>>
        val tasksDeferred: kotlinx.coroutines.Deferred<List<BackgroundTaskDto>>
        val totalDeferred: kotlinx.coroutines.Deferred<Int?>
        coroutineScope {
            pendingDeferred = async {
                AdminRunTask.values()
                    .filter { it.backfillKind != null }
                    .map { task ->
                        async {
                            runCatching {
                                repository.pendingCount(task.backfillKind!!.apiPath)
                            }.getOrNull()?.let { task to it }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                    .toMap()
            }
            tasksDeferred = async {
                runCatching { repository.listBackgroundTasks() }
                    .getOrNull()
                    ?: _state.value.backgroundTasks
            }
            totalDeferred = async {
                runCatching { repository.mlPendingTotal().count }
                    .getOrNull()
                    ?: _state.value.mlPendingTotal
            }
        }
        _state.update {
            it.copy(
                pending = pendingDeferred.await(),
                backgroundTasks = tasksDeferred.await(),
                mlPendingTotal = totalDeferred.await(),
                isLoading = false
            )
        }
    }
}

@Composable
fun AdminRunTasksScreen(
    viewModel: AdminRunTasksViewModel,
    onOpenTask: (AdminRunTask) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }
    LaunchedEffect(Unit) { viewModel.load() }

    // Snapshot Clock.now whenever the polled task list changes so every
    // "hace X" label re-evaluates against the same instant. Coarser
    // granularity (minutes/hours/days) means we don't need a per-second
    // ticker here.
    val nowMs = remember(state.backgroundTasks) {
        Clock.System.now().toEpochMilliseconds()
    }

    val pipelineTasks = remember { AdminRunTask.values().filter { it.section == AdminRunTaskSection.Pipeline } }
    val aiTasks = remember { AdminRunTask.values().filter { it.section == AdminRunTaskSection.Ai } }
    val otherTasks = remember { AdminRunTask.values().filter { it.section == AdminRunTaskSection.Other } }

    // Index server-side task DTOs by type for O(1) lookup per row.
    val runningByType = state.backgroundTasks.filter { it.isRunning }.associateBy { it.type }
    val lastFinishedByType = state.backgroundTasks
        .filter { !it.isRunning && it.finishedAt != null }
        .groupBy { it.type }
        .mapValues { (_, list) -> list.maxByOrNull { it.finishedAt!! } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        state.errorMessage?.let { msg ->
            item {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item { SectionHeader(stringResource(Res.string.admin_run_tasks_section_pipeline)) }
        items(pipelineTasks) { task ->
            TaskRow(
                task = task,
                running = task.backgroundType?.let { runningByType[it] },
                aiInProgress = false,
                lastFinished = task.backgroundType?.let { lastFinishedByType[it] },
                nowMs = nowMs,
                pending = null,
                isTriggering = task in state.triggering,
                onOpen = { onOpenTask(task) },
                onStart = { viewModel.triggerTask(task) },
                onCancelAi = null,
                onCancel = { dto -> viewModel.cancelTask(dto.id) }
            )
        }

        item {
            SectionHeader(
                title = stringResource(Res.string.admin_run_tasks_section_ai),
                subtitle = state.mlPendingTotal?.let { total ->
                    stringResource(Res.string.admin_run_tasks_ai_subtitle_format, total)
                }
            )
        }
        items(aiTasks) { task ->
            val pending = state.pending[task]
            TaskRow(
                task = task,
                running = null,
                // ML "running" = at least one job Pending/Processing in the
                // enrichment queue. The row picks up the azul tint + LiveDot
                // and shows a determinate bar driven by `completed /
                // (completed + inQueue)` so the user sees real movement as
                // workers drain the queue.
                aiInProgress = (pending?.inQueue ?: 0) > 0,
                lastFinished = null,
                nowMs = nowMs,
                pending = pending,
                isTriggering = task in state.triggering,
                onOpen = { onOpenTask(task) },
                onStart = { viewModel.triggerMlBackfill(task) },
                onCancelAi = { viewModel.cancelMlQueue(task) },
                onCancel = null
            )
        }

        item { SectionHeader(stringResource(Res.string.admin_run_tasks_section_other)) }
        items(otherTasks) { task ->
            TaskRow(
                task = task,
                running = task.backgroundType?.let { runningByType[it] },
                aiInProgress = false,
                lastFinished = task.backgroundType?.let { lastFinishedByType[it] },
                nowMs = nowMs,
                pending = null,
                isTriggering = task in state.triggering,
                onOpen = { onOpenTask(task) },
                onStart = { viewModel.triggerTask(task) },
                onCancelAi = null,
                onCancel = { dto -> viewModel.cancelTask(dto.id) }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Full-width row for a single task. Three visual states keyed off the
 * card's container colour so the section list scans at a glance:
 *
 * - Idle (`surfaceVariant`): icon · title · contextual subtitle ·
 *   ▶ Start button.
 * - Triggering (`secondaryContainer`): same layout, ▶ replaced by a
 *   small spinner while the trigger request flies out. Lasts at most
 *   [TriggerTimeoutMs] before the next refresh promotes it to Running
 *   or rolls back to Idle.
 * - Running (`primaryContainer`): ● LiveDot · "45 % — Procesando 234/520"
 *   subtitle · linear progress bar · ⏹ Cancel button.
 *
 * Tapping the card body opens the dedicated detail screen. Tapping the
 * trailing icon button executes the inline action without leaving the
 * hub.
 */
@Composable
private fun TaskRow(
    task: AdminRunTask,
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    lastFinished: BackgroundTaskDto?,
    nowMs: Long,
    pending: PendingCountResponse?,
    isTriggering: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onCancelAi: (() -> Unit)?,
    onCancel: ((BackgroundTaskDto) -> Unit)?,
) {
    val isActive = running != null || aiInProgress
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        isTriggering -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimaryContainer
        isTriggering -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = task.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(task.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor
                    )
                    TaskRowSubtitle(
                        task = task,
                        running = running,
                        aiInProgress = aiInProgress,
                        lastFinished = lastFinished,
                        nowMs = nowMs,
                        pending = pending,
                        contentColor = contentColor
                    )
                }
                TaskRowAction(
                    running = running,
                    aiInProgress = aiInProgress,
                    pending = pending,
                    isTriggering = isTriggering,
                    onStart = onStart,
                    onCancel = onCancel,
                    onCancelAi = onCancelAi
                )
            }
            if (running != null) {
                Spacer(Modifier.size(8.dp))
                val pct = (running.percentage / 100.0).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            } else if (aiInProgress && pending != null) {
                // `completed / (completed + inQueue)` reads as "of what's
                // in motion, fraction done". Naturally lands at 100 % when
                // the queue drains, regardless of how many unprocessed
                // assets remain in the library — those will only enter
                // the metric if the admin enqueues another backfill.
                Spacer(Modifier.size(8.dp))
                val denom = pending.completed + pending.inQueue
                val pct = if (denom > 0)
                    pending.completed.toFloat() / denom.toFloat()
                else 0f
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                )
            }
        }
    }
}

@Composable
private fun TaskRowSubtitle(
    task: AdminRunTask,
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    lastFinished: BackgroundTaskDto?,
    nowMs: Long,
    pending: PendingCountResponse?,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val mutedColor = contentColor.copy(alpha = 0.75f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (running != null || aiInProgress) {
            LiveDot(active = true)
        }
        val text: String = when {
            running != null -> {
                val pct = running.percentage.toInt().coerceIn(0, 100)
                if (running.lastMessage.isNotBlank()) "$pct % — ${running.lastMessage}"
                else stringResource(Res.string.admin_run_tasks_status_in_progress, pct)
            }
            // ML row with workers draining the queue: lead with the
            // computed progress percentage and the queue size; the
            // "unprocessed" counter is omitted to keep the line short.
            aiInProgress && pending != null -> {
                val denom = pending.completed + pending.inQueue
                val pct = if (denom > 0)
                    (pending.completed * 100 / denom).coerceIn(0, 100)
                else 0
                stringResource(
                    Res.string.admin_run_tasks_ai_progress_format,
                    pct,
                    pending.inQueue
                )
            }
            // ML idle: just the pending counters.
            pending != null -> stringResource(
                Res.string.admin_run_tasks_pending_format,
                pending.unprocessed,
                pending.inQueue
            )
            lastFinished?.finishedAt != null -> {
                val finishedMs = runCatching { Instant.parse(lastFinished.finishedAt!!).toEpochMilliseconds() }
                    .getOrNull()
                if (finishedMs != null) {
                    stringResource(
                        Res.string.admin_run_tasks_last_run_format,
                        formatRelativeTime((nowMs - finishedMs) / 1000L)
                    )
                } else stringResource(Res.string.admin_run_tasks_last_run_never)
            }
            task.section == AdminRunTaskSection.Other ||
                task.section == AdminRunTaskSection.Pipeline ->
                stringResource(Res.string.admin_run_tasks_last_run_never)
            else -> ""
        }
        if (text.isNotEmpty()) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaskRowAction(
    running: BackgroundTaskDto?,
    aiInProgress: Boolean,
    pending: PendingCountResponse?,
    isTriggering: Boolean,
    onStart: () -> Unit,
    onCancel: ((BackgroundTaskDto) -> Unit)?,
    onCancelAi: (() -> Unit)?,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
        when {
            // Pipeline/Other running with cancel affordance.
            running != null && onCancel != null -> {
                IconButton(onClick = { onCancel(running) }) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = stringResource(Res.string.admin_run_tasks_action_cancel),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            // ML in progress with a cancel handler: clears Pending rows
            // server-side. In-flight Processing inferences finish on
            // their own — we can't kill a worker mid-batch.
            aiInProgress && onCancelAi != null -> {
                IconButton(onClick = onCancelAi) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = stringResource(Res.string.admin_run_tasks_action_cancel),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            isTriggering -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            // Idle ML row with nothing left to enqueue: hide the Play
            // button so the user isn't tempted to fire an empty backfill.
            pending != null && pending.unprocessed == 0 -> Unit
            running == null -> {
                IconButton(onClick = onStart) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(Res.string.admin_run_tasks_action_start),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * "ahora" / "5 min" / "2 h" / "4 d" — coarse-grain humanized duration
 * for the "Última ejecución hace X" subtitle. Coarser than
 * [formatDurationSeconds] (which is for live elapsed timers in
 * sub-minute precision).
 */
@Composable
private fun formatRelativeTime(seconds: Long): String = when {
    seconds < 60 -> stringResource(Res.string.admin_run_tasks_time_now)
    seconds < 3600 -> stringResource(Res.string.admin_run_tasks_time_m_format, seconds / 60)
    seconds < 86400 -> stringResource(Res.string.admin_run_tasks_time_h_format, seconds / 3600)
    else -> stringResource(Res.string.admin_run_tasks_time_d_format, seconds / 86400)
}
