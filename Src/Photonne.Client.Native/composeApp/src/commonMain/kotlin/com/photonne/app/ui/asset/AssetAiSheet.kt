package com.photonne.app.ui.asset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.EnrichmentTaskDto
import com.photonne.app.data.devicebackup.EnrichmentRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_run_tasks_time_d_format
import com.photonne.app.resources.admin_run_tasks_time_h_format
import com.photonne.app.resources.admin_run_tasks_time_m_format
import com.photonne.app.resources.admin_run_tasks_time_now
import com.photonne.app.resources.asset_ai_embeddings
import com.photonne.app.resources.asset_ai_faces
import com.photonne.app.resources.asset_ai_load_error
import com.photonne.app.resources.asset_ai_objects
import com.photonne.app.resources.asset_ai_rerun
import com.photonne.app.resources.asset_ai_run
import com.photonne.app.resources.asset_ai_run_all
import com.photonne.app.resources.asset_ai_scenes
import com.photonne.app.resources.asset_ai_status_done_format
import com.photonne.app.resources.asset_ai_status_failed_format
import com.photonne.app.resources.asset_ai_status_failed_retry_format
import com.photonne.app.resources.asset_ai_status_never
import com.photonne.app.resources.asset_ai_status_queued
import com.photonne.app.resources.asset_ai_status_running
import com.photonne.app.resources.asset_ai_status_suppressed
import com.photonne.app.resources.asset_ai_subtitle
import com.photonne.app.resources.asset_ai_text
import com.photonne.app.resources.asset_ai_title
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import com.photonne.app.ui.theme.Spacing

/** How often the sheet re-reads the asset while an analysis is queued or
 *  running. A single photo takes the workers seconds, not minutes. */
private const val PollIntervalMs = 3_000L

/**
 * "Analizar con IA" for one photo: the five analyses with what the server
 * last did for each, a button to (re)run any one of them, and one to run
 * them all. Nothing here is new server-side — it's the per-asset retry the
 * pending-uploads screen already uses, pointed at a photo the user is
 * looking at.
 *
 * While anything is queued or running the sheet polls every few seconds;
 * the moment an analysis stops being busy, [onAnalysisFinished] fires so
 * the host can reload the detail (faces, tags) behind the sheet.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AssetAiSheet(
    assetId: String,
    onDismiss: () -> Unit,
    onAnalysisFinished: () -> Unit,
    repository: EnrichmentRepository = koinInject(),
    errorFactory: com.photonne.app.data.error.UiErrorFactory = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var tasks by remember(assetId) { mutableStateOf<List<EnrichmentTaskDto>?>(null) }
    var loadFailed by remember(assetId) { mutableStateOf(false) }
    var actionError by remember(assetId) { mutableStateOf<String?>(null) }
    var launching by remember(assetId) { mutableStateOf(setOf<AiAnalysis>()) }
    var nowMs by remember(assetId) { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    // Bumped after every launch so the polling loop restarts at once instead
    // of finishing its current sleep.
    var refreshTick by remember(assetId) { mutableStateOf(0) }

    LaunchedEffect(assetId, refreshTick) {
        var previouslyBusy: Set<AiAnalysis>? = null
        while (true) {
            val fetched = runCatching { repository.getAsset(assetId).tasks }.getOrNull()
            nowMs = Clock.System.now().toEpochMilliseconds()
            if (fetched == null) {
                if (tasks == null) loadFailed = true
                break
            }
            tasks = fetched
            loadFailed = false
            val busy = aiAnalysisRows(fetched, nowMs).filter { it.isBusy }.map { it.analysis }.toSet()
            if (previouslyBusy != null && (previouslyBusy - busy).isNotEmpty()) onAnalysisFinished()
            previouslyBusy = busy
            if (busy.isEmpty()) break
            delay(PollIntervalMs)
        }
    }

    fun launch(analyses: List<AiAnalysis>) {
        if (analyses.isEmpty()) return
        actionError = null
        launching = launching + analyses
        scope.launch {
            for (analysis in analyses) {
                runCatching { repository.retryTask(assetId, analysis.taskType) }
                    .onFailure { error ->
                        // Mensaje legible en vez del it.message técnico en crudo.
                        actionError = errorFactory
                            .from(error, "No se pudo lanzar el análisis")
                            .userMessage
                    }
            }
            launching = launching - analyses.toSet()
            refreshTick++
        }
    }

    val rows = remember(tasks, nowMs) { tasks?.let { aiAnalysisRows(it, nowMs) } }
    val anyBusy = rows?.any { it.isBusy } == true || launching.isNotEmpty()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(stringResource(Res.string.asset_ai_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.asset_ai_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val banner = actionError ?: if (loadFailed) stringResource(Res.string.asset_ai_load_error) else null
            if (banner != null) {
                Text(banner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (rows == null && !loadFailed) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            rows?.forEach { row ->
                AiAnalysisRowView(
                    row = row,
                    launching = row.analysis in launching,
                    onRun = { launch(listOf(row.analysis)) },
                )
            }

            if (rows != null) {
                Spacer(Modifier.height(Spacing.xs))
                FilledTonalButton(
                    onClick = { launch(rows.map { it.analysis }) },
                    enabled = !anyBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.asset_ai_run_all))
                }
            }
        }
    }
}

@Composable
private fun AiAnalysisRowView(
    row: AiAnalysisRow,
    launching: Boolean,
    onRun: () -> Unit,
) {
    val status = row.status
    val statusText = when (status) {
        AiAnalysisStatus.Never -> stringResource(Res.string.asset_ai_status_never)
        AiAnalysisStatus.Queued -> stringResource(Res.string.asset_ai_status_queued)
        AiAnalysisStatus.Running -> stringResource(Res.string.asset_ai_status_running)
        is AiAnalysisStatus.Done -> stringResource(Res.string.asset_ai_status_done_format, formatAgo(status.agoSeconds))
        is AiAnalysisStatus.Failed -> when {
            status.retryInSeconds != null ->
                stringResource(Res.string.asset_ai_status_failed_retry_format, formatAgo(status.retryInSeconds))
            status.message != null -> stringResource(Res.string.asset_ai_status_failed_format, status.message)
            else -> stringResource(Res.string.asset_ai_status_failed_format, "?")
        }
        AiAnalysisStatus.Suppressed -> stringResource(Res.string.asset_ai_status_suppressed)
    }
    val statusColor = when (status) {
        is AiAnalysisStatus.Failed -> MaterialTheme.colorScheme.error
        AiAnalysisStatus.Queued, AiAnalysisStatus.Running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(row.analysis.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(row.analysis.labelRes), style = MaterialTheme.typography.bodyLarge)
            // The failure message is the server's own words and can run long;
            // it wraps rather than being cut, because it's the whole point.
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
            when {
                row.isBusy || launching -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                status == AiAnalysisStatus.Never -> IconButton(onClick = onRun) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(Res.string.asset_ai_run),
                        tint = MaterialTheme.colorScheme.primary)
                }
                else -> IconButton(onClick = onRun) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(Res.string.asset_ai_rerun),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private val AiAnalysis.icon: ImageVector
    get() = when (this) {
        AiAnalysis.Faces -> Icons.Outlined.Face
        AiAnalysis.Objects -> Icons.Outlined.Category
        AiAnalysis.Scenes -> Icons.Outlined.Landscape
        AiAnalysis.Text -> Icons.Outlined.TextFields
        AiAnalysis.Embeddings -> Icons.Outlined.AutoAwesome
    }

private val AiAnalysis.labelRes
    get() = when (this) {
        AiAnalysis.Faces -> Res.string.asset_ai_faces
        AiAnalysis.Objects -> Res.string.asset_ai_objects
        AiAnalysis.Scenes -> Res.string.asset_ai_scenes
        AiAnalysis.Text -> Res.string.asset_ai_text
        AiAnalysis.Embeddings -> Res.string.asset_ai_embeddings
    }

/** "menos de un minuto" / "5 min" / "2 h" / "4 d". */
@Composable
private fun formatAgo(seconds: Long): String = when {
    seconds < 60 -> stringResource(Res.string.admin_run_tasks_time_now)
    seconds < 3600 -> stringResource(Res.string.admin_run_tasks_time_m_format, seconds / 60)
    seconds < 86400 -> stringResource(Res.string.admin_run_tasks_time_h_format, seconds / 3600)
    else -> stringResource(Res.string.admin_run_tasks_time_d_format, seconds / 86400)
}
