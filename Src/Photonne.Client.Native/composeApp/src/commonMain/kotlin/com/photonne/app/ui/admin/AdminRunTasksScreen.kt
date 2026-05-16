package com.photonne.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.PendingCountResponse
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_run_tasks_section_ml
import com.photonne.app.resources.admin_run_tasks_section_other
import com.photonne.app.resources.admin_run_tasks_summary_idle
import com.photonne.app.resources.admin_run_tasks_summary_pending_format
import com.photonne.app.resources.admin_run_tasks_summary_title
import com.photonne.app.resources.admin_run_tasks_tile_pending
import com.photonne.app.resources.admin_run_tasks_tile_queued
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
import com.photonne.app.ui.charts.AnimatedCounter
import com.photonne.app.ui.charts.ChartLegend
import com.photonne.app.ui.charts.DonutChart
import com.photonne.app.ui.charts.DonutSlice
import com.photonne.app.ui.charts.LegendItem
import com.photonne.app.ui.charts.LiveDot
import com.photonne.app.ui.charts.StackedBar
import com.photonne.app.ui.charts.StackedSegment
import com.photonne.app.ui.charts.rememberChartPalette
import com.photonne.app.ui.hub.hubPressScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Catalogue of every runnable processing task surfaced by Administración →
 * Sistema → Ejecutar tareas. Each task knows its hub icon and labels and,
 * for the five ML features, the backfill kind whose `/pending-count`
 * endpoint feeds the live counter. Index / Metadata / Thumbnails /
 * Duplicates have no per-feature counter — their detail screens own that
 * presentation.
 */
enum class AdminRunTask(
    val titleRes: StringResource,
    val subtitleRes: StringResource,
    val icon: ImageVector,
    val backfillKind: AdminBackfillKind?,
) {
    IndexAssets(
        titleRes = Res.string.admin_system_index,
        subtitleRes = Res.string.admin_system_index_subtitle,
        icon = Icons.Outlined.Sync,
        backfillKind = null,
    ),
    ExtractMetadata(
        titleRes = Res.string.admin_system_metadata,
        subtitleRes = Res.string.admin_system_metadata_subtitle,
        icon = Icons.Outlined.Info,
        backfillKind = null,
    ),
    GenerateThumbnails(
        titleRes = Res.string.admin_system_thumbnails,
        subtitleRes = Res.string.admin_system_thumbnails_subtitle,
        icon = Icons.Outlined.Image,
        backfillKind = null,
    ),
    DetectDuplicates(
        titleRes = Res.string.admin_system_duplicates,
        subtitleRes = Res.string.admin_system_duplicates_subtitle,
        icon = Icons.Outlined.ContentCopy,
        backfillKind = null,
    ),
    FaceRecognition(
        titleRes = Res.string.admin_system_face,
        subtitleRes = Res.string.admin_system_face_subtitle,
        icon = Icons.Outlined.Face,
        backfillKind = AdminBackfillKind.FaceRecognition,
    ),
    ObjectDetection(
        titleRes = Res.string.admin_system_object,
        subtitleRes = Res.string.admin_system_object_subtitle,
        icon = Icons.Outlined.Category,
        backfillKind = AdminBackfillKind.ObjectDetection,
    ),
    SceneClassification(
        titleRes = Res.string.admin_system_scene,
        subtitleRes = Res.string.admin_system_scene_subtitle,
        icon = Icons.Outlined.Landscape,
        backfillKind = AdminBackfillKind.SceneClassification,
    ),
    TextRecognition(
        titleRes = Res.string.admin_system_text,
        subtitleRes = Res.string.admin_system_text_subtitle,
        icon = Icons.Outlined.TextFields,
        backfillKind = AdminBackfillKind.TextRecognition,
    ),
    ImageEmbedding(
        titleRes = Res.string.admin_system_embedding,
        subtitleRes = Res.string.admin_system_embedding_subtitle,
        icon = Icons.Outlined.ImageSearch,
        backfillKind = AdminBackfillKind.ImageEmbedding,
    ),
}

private const val PollIntervalMs = 10_000L

data class AdminRunTasksUiState(
    val pending: Map<AdminRunTask, PendingCountResponse> = emptyMap(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Loads `/pending-count` for the five ML tasks in parallel. Once
 * [startPolling] is called, the same fetch repeats every [PollIntervalMs]
 * milliseconds until [stopPolling] (or the ViewModel is cleared) — so the
 * dashboard reflects backfill progress without the admin having to refresh
 * by hand. Errors per task are swallowed and only show on the row that
 * failed; one slow endpoint does not block the others.
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

    /** Refreshes once now, then loops every [PollIntervalMs] until cancelled. */
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
     * [showLoading] only flips the spinner state on the very first fetch —
     * subsequent polls keep `isLoading = false` so the dashboard doesn't
     * flash every 10 seconds.
     */
    private suspend fun refresh(showLoading: Boolean) {
        if (showLoading) _state.update { it.copy(isLoading = true, errorMessage = null) }
        val results = coroutineScope {
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
        _state.update { it.copy(pending = results, isLoading = false) }
    }
}

@Composable
fun AdminRunTasksScreen(
    viewModel: AdminRunTasksViewModel,
    onOpenTask: (AdminRunTask) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    // Auto-poll while the screen is visible; cancel on dispose so we
    // don't hammer the API from a background view-model that still
    // exists somewhere in the back stack.
    DisposableEffect(viewModel) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }
    LaunchedEffect(Unit) { viewModel.load() }

    val mlTasks = remember { AdminRunTask.values().filter { it.backfillKind != null } }
    val otherTasks = remember { AdminRunTask.values().filter { it.backfillKind == null } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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

        item { MlSummaryCard(state.pending) }

        item {
            SectionLabel(stringResource(Res.string.admin_run_tasks_section_ml))
        }
        items2x2(mlTasks) { task ->
            TaskTile(
                task = task,
                pending = state.pending[task],
                onClick = { onOpenTask(task) }
            )
        }

        item {
            SectionLabel(stringResource(Res.string.admin_run_tasks_section_other))
        }
        items2x2(otherTasks) { task ->
            TaskTile(
                task = task,
                pending = null,
                onClick = { onOpenTask(task) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Renders [items] as a 2-column grid by chunking pairs into Rows. Used
 * instead of LazyVerticalGrid because we want the rows to live inside the
 * outer LazyColumn so all sections share one scrolling axis.
 */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items2x2(
    items: List<T>,
    content: @Composable (T) -> Unit
) {
    items.chunked(2).forEach { row ->
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) { content(item) }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MlSummaryCard(pending: Map<AdminRunTask, PendingCountResponse>) {
    val palette = rememberChartPalette()
    val totalUnprocessed = pending.values.sumOf { it.unprocessed }
    val totalQueued = pending.values.sumOf { it.inQueue }
    val anyActive = totalQueued > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.admin_run_tasks_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                LiveDot(active = anyActive)
            }

            if (totalUnprocessed == 0 && totalQueued == 0) {
                Text(
                    stringResource(Res.string.admin_run_tasks_summary_idle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                AnimatedCounter(
                    value = totalUnprocessed,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    formatter = { stringResourceFormat(it) }
                )
                Text(
                    text = stringResource(
                        Res.string.admin_run_tasks_summary_pending_format,
                        totalUnprocessed
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Two-segment bar matching the per-tile mini donut so the
                // dashboard speaks one visual language: gold = pending work
                // that isn't actively being processed yet, blue = items
                // currently in the active queue. API treats queued as a
                // subset of unprocessed, so we clamp to keep the math sane.
                val queued = totalQueued.coerceIn(0, totalUnprocessed.coerceAtLeast(0))
                val rest = (totalUnprocessed - queued).coerceAtLeast(0)
                StackedBar(
                    segments = listOf(
                        StackedSegment(rest.toFloat(), palette.photos),
                        StackedSegment(queued.toFloat(), palette.videos)
                    ),
                    trackColor = MaterialTheme.colorScheme.surface,
                    barHeight = 8.dp
                )

                ChartLegend(
                    items = listOf(
                        LegendItem(
                            palette.photos,
                            stringResource(Res.string.admin_run_tasks_tile_pending),
                            value = rest.toString()
                        ),
                        LegendItem(
                            palette.videos,
                            stringResource(Res.string.admin_run_tasks_tile_queued),
                            value = queued.toString()
                        )
                    )
                )
            }
        }
    }
}

/**
 * Per-task tile. ML tasks display the unprocessed count as a big animated
 * number with a mini donut to the right showing the queue fraction
 * (queued / unprocessed). Non-ML tasks just show the icon + title — their
 * detail screens own progress visualisation.
 */
@Composable
private fun TaskTile(
    task: AdminRunTask,
    pending: PendingCountResponse?,
    onClick: () -> Unit
) {
    val palette = rememberChartPalette()
    val interaction = remember { MutableInteractionSource() }
    val isActive = (pending?.inQueue ?: 0) > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hubPressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                // Icon container with the LiveDot anchored to its top-right
                // corner as a badge — keeps the activity signal tied to the
                // task glyph and frees the full row width for the title so
                // long Spanish labels ("Clasificación de escenas") can wrap
                // without truncating.
                Box(modifier = Modifier.size(36.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = task.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LiveDot(active = true, coreSize = 6.dp)
                        }
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(task.titleRes),
                    style = MaterialTheme.typography.titleSmall.copy(
                        // Heading break uses a balanced algorithm and won't
                        // split inside a word, so "Reconocimiento" stays
                        // intact instead of becoming "Reconoci‑miento".
                        lineBreak = LineBreak.Heading
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            if (pending != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AnimatedCounter(
                            value = pending.unprocessed,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(Res.string.admin_run_tasks_tile_pending),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        // Mini donut showing the queued fraction. We clamp
                        // queued ≤ unprocessed because the API treats them
                        // as overlapping sets (queued ⊆ unprocessed).
                        val unprocessed = pending.unprocessed.coerceAtLeast(0)
                        val queued = pending.inQueue.coerceIn(0, unprocessed.coerceAtLeast(1))
                        val rest = (unprocessed - queued).coerceAtLeast(0)
                        DonutChart(
                            slices = listOf(
                                DonutSlice(queued.toFloat(), palette.videos),
                                DonutSlice(rest.toFloat(), palette.free)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            strokeWidth = 6.dp,
                            gapDegrees = 1.5f
                        )
                        AnimatedCounter(
                            value = pending.inQueue,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.admin_run_tasks_tile_queued) +
                        " · ${pending.inQueue}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(task.subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Thousand-separated formatter — for the big pending counter on the summary. */
private fun stringResourceFormat(value: Int): String {
    val s = value.toString()
    val sb = StringBuilder()
    var c = 0
    for (i in s.lastIndex downTo 0) {
        sb.append(s[i])
        c++
        if (c == 3 && i != 0) { sb.append('.'); c = 0 }
    }
    return sb.reverse().toString()
}
