package com.photonne.app.ui.devicebackup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.devicebackup.DeviceFolderRef
import com.photonne.app.data.devicebackup.DeviceGallery
import com.photonne.app.data.devicebackup.DeviceMediaSyncState
import com.photonne.app.data.devicebackup.DeviceMediaType
import com.photonne.app.data.devicebackup.rememberDeviceFolderPicker
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.backup_action_ignore
import com.photonne.app.resources.backup_action_ignore_all_failed
import com.photonne.app.resources.backup_action_unignore
import com.photonne.app.resources.backup_section_ignored
import com.photonne.app.resources.backup_failed_dialog_close
import com.photonne.app.resources.backup_failed_dialog_no_detail
import com.photonne.app.resources.backup_failed_dialog_retry
import com.photonne.app.resources.backup_failed_dialog_title
import com.photonne.app.resources.backup_failure_reason_label
import com.photonne.app.resources.backup_section_pending
import com.photonne.app.resources.backup_section_uploaded
import com.photonne.app.resources.backup_status_queued
import com.photonne.app.resources.backup_summary_counts
import com.photonne.app.resources.backup_summary_title
import com.photonne.app.resources.backup_uploading_count
import com.photonne.app.resources.device_backup_action_pick_folder
import com.photonne.app.resources.device_backup_action_sync
import com.photonne.app.resources.device_backup_empty_folder
import com.photonne.app.resources.device_backup_folder_label
import com.photonne.app.resources.device_backup_intro
import com.photonne.app.resources.device_backup_not_supported
import com.photonne.app.resources.device_backup_progress
import com.photonne.app.resources.device_backup_total
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.theme.PhotonneColors
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupPendingScreen(
    viewModel: DeviceBackupViewModel,
    gallery: DeviceGallery,
    onOpenAsset: (TimelineItem) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    val pickFolder = rememberDeviceFolderPicker(
        gallery = gallery,
        onPicked = viewModel::onFolderPicked
    )
    var previewStartUri by remember { mutableStateOf<String?>(null) }
    var failedDialogUri by remember { mutableStateOf<String?>(null) }

    if (!state.isSupported) {
        EmptyMessage(stringResource(Res.string.device_backup_not_supported))
        return
    }

    val folder = state.folder
    if (folder == null) {
        EmptyState(
            intro = stringResource(Res.string.device_backup_intro),
            actionLabel = stringResource(Res.string.device_backup_action_pick_folder),
            onAction = pickFolder
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FolderHeader(
                folder = folder,
                totalCount = state.entries.size
            )

            state.error?.userMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            state.statusMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            state.lastSyncSummary?.takeIf { !state.isSyncing }?.let { summary ->
                SyncSummaryCard(summary = summary)
            }

            state.syncProgress?.let { progress ->
                SyncProgressCard(progress = progress, isSyncing = state.isSyncing)
            }

            if (state.isCheckingHashes) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }

            when {
                state.isLoading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.entries.isEmpty() ->
                    EmptyMessage(stringResource(Res.string.device_backup_empty_folder))
                else -> MediaGrid(
                    state = state,
                    thumbnailModel = viewModel::thumbnailModel,
                    onClick = { entry ->
                        if (state.selectedCount > 0) {
                            // Once the user has anything selected they
                            // are in "queue for sync" mode; tap toggles
                            // selection so it matches the existing
                            // multi-select flow.
                            viewModel.toggleSelection(entry.media.uri)
                        } else if (entry.syncState is DeviceMediaSyncState.Failed) {
                            // A failed item's most useful action is showing
                            // WHY it failed, with retry one tap away.
                            failedDialogUri = entry.media.uri
                        } else {
                            previewStartUri = entry.media.uri
                        }
                    },
                    onLongClick = { entry ->
                        viewModel.toggleSelection(entry.media.uri)
                    },
                    onUnignore = { entry -> viewModel.unignore(entry.media.uri) },
                    onIgnoreAllFailed = viewModel::ignoreFailed
                )
            }
        }

        if (state.syncableSelectedCount > 0 && !state.isSyncing) {
            ExtendedFloatingActionButton(
                onClick = viewModel::syncSelected,
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                text = {
                    Text(
                        stringResource(
                            Res.string.device_backup_action_sync,
                            state.syncableSelectedCount
                        )
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    failedDialogUri?.let { uri ->
        val entry = state.entries.firstOrNull { it.media.uri == uri }
        val failedState = entry?.syncState as? DeviceMediaSyncState.Failed
        if (entry == null || failedState == null) {
            failedDialogUri = null
        } else {
            FailedItemDialog(
                fileName = entry.media.displayName,
                reasonLabel = uploadErrorLabel(failedState.reason),
                detail = failedState.detail,
                onRetry = {
                    failedDialogUri = null
                    viewModel.retrySingle(uri)
                },
                onIgnore = {
                    failedDialogUri = null
                    viewModel.ignoreSingle(uri)
                },
                onDismiss = { failedDialogUri = null }
            )
        }
    }

    val startUri = previewStartUri
    if (startUri != null) {
        val startIndex = state.entries.indexOfFirst { it.media.uri == startUri }
        if (startIndex < 0) {
            previewStartUri = null
        } else {
            DeviceAssetPreviewScreen(
                entries = state.entries,
                startIndex = startIndex,
                thumbnailModel = viewModel::thumbnailModel,
                onBack = { previewStartUri = null },
                onOpenDetail = { entry ->
                    val item = viewModel.timelineItemFor(entry)
                    if (item != null) {
                        previewStartUri = null
                        onOpenAsset(item)
                    }
                }
            )
        }
    }
}

/**
 * Drill-down for a failed upload: the localized category the summary
 * already shows, plus the raw server/exception detail that explains the
 * actual cause — the difference between "server error" and a fixable
 * diagnosis.
 */
@Composable
private fun FailedItemDialog(
    fileName: String,
    reasonLabel: String,
    detail: String?,
    onRetry: () -> Unit,
    onIgnore: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.backup_failed_dialog_title)) },
        text = {
            Column {
                Text(
                    fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    reasonLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    detail ?: stringResource(Res.string.backup_failed_dialog_no_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            // Retry + Skip share the confirm slot; Close stays the dismiss.
            Row {
                TextButton(onClick = onIgnore) {
                    Text(
                        stringResource(Res.string.backup_action_ignore),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRetry) {
                    Text(stringResource(Res.string.backup_failed_dialog_retry))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.backup_failed_dialog_close))
            }
        }
    )
}

@Composable
private fun FolderHeader(
    folder: DeviceFolderRef,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.device_backup_folder_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    folder.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(Res.string.device_backup_total, totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyncSummaryCard(summary: SyncSummary) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(Res.string.backup_summary_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(
                    Res.string.backup_summary_counts,
                    summary.completed,
                    summary.skipped,
                    summary.failed
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Per-reason breakdown so the user sees WHY assets failed instead of a bare count.
            if (summary.failuresByReason.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
                summary.failuresByReason.entries
                    .sortedByDescending { it.value }
                    .forEach { (reason, count) ->
                        Text(
                            text = stringResource(
                                Res.string.backup_failure_reason_label,
                                uploadErrorLabel(reason),
                                count
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
            }
        }
    }
}

@Composable
private fun SyncProgressCard(progress: SyncProgress, isSyncing: Boolean) {
    val done = progress.completed + progress.skipped + progress.failed
    val pct = if (progress.total == 0) 0f else done.toFloat() / progress.total.toFloat()
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(
                    Res.string.device_backup_progress,
                    done,
                    progress.total
                ),
                style = MaterialTheme.typography.titleSmall
            )
            if (isSyncing && progress.inFlight > 0) {
                Text(
                    stringResource(Res.string.backup_uploading_count, progress.inFlight),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.size(4.dp))
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        }
    }
}

@Composable
private fun MediaGrid(
    state: DeviceBackupUiState,
    thumbnailModel: (com.photonne.app.data.devicebackup.DeviceMedia) -> String,
    onClick: (DeviceBackupEntry) -> Unit,
    onLongClick: (DeviceBackupEntry) -> Unit,
    onUnignore: (DeviceBackupEntry) -> Unit,
    onIgnoreAllFailed: () -> Unit
) {
    // Three blocks: still pending on top, skipped, then already backed up.
    // Each list keeps the folder order it already had.
    val pending = remember(state.entries) {
        state.entries.filter {
            it.syncState !is DeviceMediaSyncState.Synced &&
                it.syncState !is DeviceMediaSyncState.Ignored
        }
    }
    val ignored = remember(state.entries) {
        state.entries.filter { it.syncState is DeviceMediaSyncState.Ignored }
    }
    val synced = remember(state.entries) {
        state.entries.filter { it.syncState is DeviceMediaSyncState.Synced }
    }
    val failedCount = remember(state.entries) {
        state.entries.count { it.syncState is DeviceMediaSyncState.Failed }
    }
    // The backed-up and skipped blocks are collapsed by default: keeps the
    // focus on pending and avoids loading thumbnails the user rarely needs.
    var syncedExpanded by remember { mutableStateOf(false) }
    var ignoredExpanded by remember { mutableStateOf(false) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(
            start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp + floatingNavBarReservedHeight()
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (pending.isNotEmpty()) {
            item(key = "hdr-pending", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionLabel(stringResource(Res.string.backup_section_pending, pending.size))
                    // One-tap escape hatch for a folder full of stuck failures:
                    // skip them all so they stop counting as pending.
                    if (failedCount > 0) {
                        TextButton(
                            onClick = onIgnoreAllFailed,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                stringResource(
                                    Res.string.backup_action_ignore_all_failed,
                                    failedCount
                                )
                            )
                        }
                    }
                }
            }
            // Pending items render as full-width LIST rows (not grid cells) so
            // each shows its own upload progress bar. The list visibly shrinks
            // top-to-bottom as items finish and drop into the block below.
            items(
                pending,
                key = { it.media.uri },
                span = { GridItemSpan(maxLineSpan) }
            ) { entry ->
                PendingRow(
                    entry = entry,
                    thumbnailModel = thumbnailModel(entry.media),
                    onClick = { onClick(entry) },
                    onLongClick = { onLongClick(entry) }
                )
            }
        }
        if (ignored.isNotEmpty()) {
            item(key = "hdr-ignored", span = { GridItemSpan(maxLineSpan) }) {
                CollapsibleSectionLabel(
                    text = stringResource(Res.string.backup_section_ignored, ignored.size),
                    expanded = ignoredExpanded,
                    onToggle = { ignoredExpanded = !ignoredExpanded }
                )
            }
            if (ignoredExpanded) {
                // Tap a skipped item to put it back in the pipeline.
                items(ignored, key = { it.media.uri }) { entry ->
                    IgnoredCell(
                        entry = entry,
                        thumbnailModel = thumbnailModel(entry.media),
                        onUnignore = { onUnignore(entry) }
                    )
                }
            }
        }
        if (synced.isNotEmpty()) {
            item(key = "hdr-synced", span = { GridItemSpan(maxLineSpan) }) {
                CollapsibleSectionLabel(
                    text = stringResource(Res.string.backup_section_uploaded, synced.size),
                    expanded = syncedExpanded,
                    onToggle = { syncedExpanded = !syncedExpanded }
                )
            }
            if (syncedExpanded) {
                items(synced, key = { it.media.uri }) { entry ->
                    MediaCell(
                        entry = entry,
                        thumbnailModel = thumbnailModel(entry.media),
                        onClick = { onClick(entry) },
                        onLongClick = { onLongClick(entry) }
                    )
                }
            }
        }
    }
}

/** Full-span section header for the pending/backed-up blocks. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
    )
}

/** Section header that toggles the visibility of its block on tap. */
@Composable
private fun CollapsibleSectionLabel(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * A pending asset as a list row: thumbnail + name, with a per-file upload
 * progress bar while it's uploading, "Queued" before it starts, or the failure
 * reason if it failed. Tap/long-press mirror [MediaCell] (preview / select /
 * show-failure), so the row plugs into the same interaction flow.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PendingRow(
    entry: DeviceBackupEntry,
    thumbnailModel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val state = entry.syncState
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = thumbnailModel,
                contentDescription = entry.media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (entry.media.type == DeviceMediaType.Video) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(2.dp)
                        .size(14.dp)
                )
            }
            if (entry.isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.White, CircleShape)
                        .size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.media.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(4.dp))
            when (state) {
                DeviceMediaSyncState.Uploading -> {
                    val p = entry.uploadProgress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }
                is DeviceMediaSyncState.Failed -> {
                    Text(
                        text = uploadErrorLabel(state.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text(
                        text = stringResource(Res.string.backup_status_queued),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        when (state) {
            DeviceMediaSyncState.Uploading -> entry.uploadProgress?.let { p ->
                Text(
                    text = "${(p * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is DeviceMediaSyncState.Failed -> Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            else -> {}
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    entry: DeviceBackupEntry,
    thumbnailModel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        AsyncImage(
            model = thumbnailModel,
            contentDescription = entry.media.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Video glyph in the corner so users can tell videos apart at
        // a glance, mirroring the native gallery convention.
        if (entry.media.type == DeviceMediaType.Video) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(20.dp)
            )
        }

        // Sync state badge in the top-right.
        SyncBadge(
            state = entry.syncState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
        )

        if (entry.isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(6.dp)
                    )
            )
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.White, shape = CircleShape)
                    .size(20.dp)
            )
        }
    }
}

/**
 * A skipped file: dimmed thumbnail with a "Reactivar" affordance. Tapping it
 * puts the file back in the backup pipeline (the next verify re-checks it).
 */
@Composable
private fun IgnoredCell(
    entry: DeviceBackupEntry,
    thumbnailModel: String,
    onUnignore: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
            )
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onUnignore)
    ) {
        AsyncImage(
            model = thumbnailModel,
            contentDescription = entry.media.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Scrim so the skipped state reads at a glance and the label is legible.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
        Text(
            text = stringResource(Res.string.backup_action_unignore),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun SyncBadge(state: DeviceMediaSyncState, modifier: Modifier = Modifier) {
    val (bg, tint, icon) = when (state) {
        is DeviceMediaSyncState.Synced ->
            Triple(PhotonneColors.success, PhotonneColors.onSuccess, Icons.Filled.Check)
        is DeviceMediaSyncState.NotSynced -> return
        is DeviceMediaSyncState.Failed ->
            Triple(MaterialTheme.colorScheme.error, Color.White, Icons.Filled.Refresh)
        DeviceMediaSyncState.Uploading ->
            Triple(MaterialTheme.colorScheme.primary, Color.White, Icons.Filled.PlayArrow)
        DeviceMediaSyncState.Unknown -> return
        // Skipped items live in their own collapsible block, no cell badge.
        DeviceMediaSyncState.Ignored -> return
    }
    Box(
        modifier = modifier
            .size(20.dp)
            .background(bg, shape = CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp).align(Alignment.Center)
        )
    }
}

@Composable
private fun EmptyState(
    intro: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            intro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
