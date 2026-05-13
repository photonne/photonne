package com.photonne.app.ui.devicesync

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.devicesync.DeviceFolderRef
import com.photonne.app.data.devicesync.DeviceGallery
import com.photonne.app.data.devicesync.DeviceMediaSyncState
import com.photonne.app.data.devicesync.DeviceMediaType
import com.photonne.app.data.devicesync.rememberDeviceFolderPicker
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.device_sync_action_free_space
import com.photonne.app.resources.device_sync_action_pick_folder
import com.photonne.app.resources.device_sync_action_refresh_states
import com.photonne.app.resources.device_sync_action_select_all
import com.photonne.app.resources.device_sync_action_sync
import com.photonne.app.resources.device_sync_change_folder
import com.photonne.app.resources.device_sync_empty_folder
import com.photonne.app.resources.device_sync_folder_label
import com.photonne.app.resources.device_sync_free_space_cancel
import com.photonne.app.resources.device_sync_free_space_confirm
import com.photonne.app.resources.device_sync_free_space_dialog_message
import com.photonne.app.resources.device_sync_free_space_dialog_title
import com.photonne.app.resources.device_sync_free_space_in_progress
import com.photonne.app.resources.device_sync_intro
import com.photonne.app.resources.device_sync_not_supported
import com.photonne.app.resources.device_sync_progress
import com.photonne.app.resources.device_sync_total
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSyncScreen(
    viewModel: DeviceSyncViewModel,
    gallery: DeviceGallery,
    onOpenAsset: (TimelineItem) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    val pickFolder = rememberDeviceFolderPicker(
        gallery = gallery,
        onPicked = viewModel::onFolderPicked
    )
    var showFreeSpaceConfirm by remember { mutableStateOf(false) }
    var previewStartUri by remember { mutableStateOf<String?>(null) }

    if (!state.isSupported) {
        EmptyMessage(stringResource(Res.string.device_sync_not_supported))
        return
    }

    val folder = state.folder
    if (folder == null) {
        EmptyState(
            intro = stringResource(Res.string.device_sync_intro),
            actionLabel = stringResource(Res.string.device_sync_action_pick_folder),
            onAction = pickFolder
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FolderHeader(
                folder = folder,
                totalCount = state.entries.size,
                onChangeFolder = pickFolder
            )

            state.errorMessage?.let { msg ->
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

            state.syncProgress?.let { progress ->
                SyncProgressCard(progress = progress, isSyncing = state.isSyncing)
            }

            if (state.isCheckingHashes) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }

            ActionBar(
                state = state,
                onRefreshSyncStates = viewModel::refreshSyncStates,
                onStopRefresh = viewModel::stopHashCheck,
                onSelectAll = viewModel::selectAllNotSynced,
                onClearSelection = viewModel::clearSelection,
                onFreeUpSpace = { showFreeSpaceConfirm = true }
            )

            when {
                state.isLoading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.entries.isEmpty() ->
                    EmptyMessage(stringResource(Res.string.device_sync_empty_folder))
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
                        } else {
                            previewStartUri = entry.media.uri
                        }
                    },
                    onLongClick = { entry ->
                        viewModel.toggleSelection(entry.media.uri)
                    }
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
                            Res.string.device_sync_action_sync,
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

    if (showFreeSpaceConfirm) {
        FreeUpSpaceDialog(
            count = state.syncedCount,
            onDismiss = { showFreeSpaceConfirm = false },
            onConfirm = {
                showFreeSpaceConfirm = false
                viewModel.freeUpSyncedSpace()
            }
        )
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

@Composable
private fun FreeUpSpaceDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(stringResource(Res.string.device_sync_free_space_dialog_title)) },
        text = {
            Text(stringResource(Res.string.device_sync_free_space_dialog_message, count))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(Res.string.device_sync_free_space_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.device_sync_free_space_cancel))
            }
        }
    )
}

@Composable
private fun FolderHeader(
    folder: DeviceFolderRef,
    totalCount: Int,
    onChangeFolder: () -> Unit
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
                    stringResource(Res.string.device_sync_folder_label),
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
                    stringResource(Res.string.device_sync_total, totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onChangeFolder) {
                Text(stringResource(Res.string.device_sync_change_folder))
            }
        }
    }
}

@Composable
private fun ActionBar(
    state: DeviceSyncUiState,
    onRefreshSyncStates: () -> Unit,
    onStopRefresh: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onFreeUpSpace: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = if (state.isCheckingHashes) onStopRefresh else onRefreshSyncStates,
                modifier = Modifier.weight(1f),
                enabled = !state.isSyncing && !state.isFreeingSpace
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.device_sync_action_refresh_states))
            }
            OutlinedButton(
                onClick = if (state.selectedCount > 0) onClearSelection else onSelectAll,
                modifier = Modifier.weight(1f),
                enabled = !state.isSyncing && !state.isFreeingSpace
            ) {
                Text(
                    if (state.selectedCount > 0) "× ${state.selectedCount}"
                    else stringResource(Res.string.device_sync_action_select_all)
                )
            }
        }
        if (state.syncedCount > 0) {
            Spacer(Modifier.size(4.dp))
            OutlinedButton(
                onClick = onFreeUpSpace,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSyncing && !state.isFreeingSpace
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(
                        Res.string.device_sync_action_free_space,
                        state.syncedCount
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (state.isFreeingSpace) {
                Spacer(Modifier.size(4.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                Text(
                    stringResource(Res.string.device_sync_free_space_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Res.string.device_sync_progress,
                    done,
                    progress.total
                ),
                style = MaterialTheme.typography.titleSmall
            )
            progress.currentName?.takeIf { isSyncing }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    state: DeviceSyncUiState,
    thumbnailModel: (com.photonne.app.data.devicesync.DeviceMedia) -> String,
    onClick: (DeviceSyncEntry) -> Unit,
    onLongClick: (DeviceSyncEntry) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(
            start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(state.entries, key = { it.media.uri }) { entry ->
            MediaCell(
                entry = entry,
                thumbnailModel = thumbnailModel(entry.media),
                onClick = { onClick(entry) },
                onLongClick = { onLongClick(entry) }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    entry: DeviceSyncEntry,
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

@Composable
private fun SyncBadge(state: DeviceMediaSyncState, modifier: Modifier = Modifier) {
    val (bg, tint, icon) = when (state) {
        is DeviceMediaSyncState.Synced ->
            Triple(Color(0xFF1B5E20), Color.White, Icons.Filled.Check)
        is DeviceMediaSyncState.NotSynced -> return
        is DeviceMediaSyncState.Failed ->
            Triple(MaterialTheme.colorScheme.error, Color.White, Icons.Filled.Refresh)
        DeviceMediaSyncState.Uploading ->
            Triple(MaterialTheme.colorScheme.primary, Color.White, Icons.Filled.PlayArrow)
        DeviceMediaSyncState.Unknown -> return
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
