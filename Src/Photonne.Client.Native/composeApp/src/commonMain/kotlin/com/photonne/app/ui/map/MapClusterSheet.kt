package com.photonne.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.MapPoint
import com.photonne.app.resources.Res
import com.photonne.app.resources.map_cluster_sheet_title
import com.photonne.app.resources.selection_action_add_to_album
import com.photonne.app.resources.selection_action_archive
import com.photonne.app.resources.selection_action_close
import com.photonne.app.resources.selection_action_trash
import com.photonne.app.resources.selection_count
import com.photonne.app.resources.map_action_select_all
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet that drops in when the user taps a cluster marker. Mirrors
 * the PWA `Map.razor` drawer: dense thumbnail grid, long-press to enter
 * selection mode, header that swaps into a bulk-action toolbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapClusterSheet(
    points: List<MapPoint>,
    baseUrl: String,
    selectedIds: Set<String>,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    if (points.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSelectionActive = selectedIds.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = { if (!isMutating) onDismiss() },
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            if (isSelectionActive) {
                SelectionHeader(
                    selectedCount = selectedIds.size,
                    isMutating = isMutating,
                    onExit = onExitSelection,
                    onSelectAll = onSelectAll,
                    onAddToAlbum = onAddToAlbum,
                    onArchive = onArchive,
                    onTrash = onTrash
                )
            } else {
                Text(
                    text = stringResource(Res.string.map_cluster_sheet_title, points.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            ) {
                itemsIndexed(points, key = { _, p -> p.id }) { index, point ->
                    val selected = point.id in selectedIds
                    ClusterCell(
                        point = point,
                        baseUrl = baseUrl,
                        selected = selected,
                        selectionActive = isSelectionActive,
                        onClick = {
                            if (isSelectionActive) onToggleSelection(point.id)
                            else onPhotoClick(index)
                        },
                        onLongClick = { onToggleSelection(point.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    isMutating: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit, enabled = !isMutating) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(Res.string.selection_action_close)
            )
        }
        Text(
            text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
        Box(modifier = Modifier.weight(1f))
        TextButton(onClick = onSelectAll, enabled = !isMutating) {
            Text(stringResource(Res.string.map_action_select_all))
        }
        IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(Res.string.selection_action_add_to_album)
            )
        }
        IconButton(onClick = onArchive, enabled = !isMutating) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(Res.string.selection_action_archive)
            )
        }
        IconButton(onClick = onTrash, enabled = !isMutating) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(Res.string.selection_action_trash),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ClusterCell(
    point: MapPoint,
    baseUrl: String,
    selected: Boolean,
    selectionActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Scale down when selected to mirror the PWA's .map-drawer-cell.selected
            .scale(if (selected) 0.9f else 1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (point.hasThumbnail) {
            AsyncImage(
                model = "$baseUrl/api/assets/${point.id}/thumbnail?size=Small",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (selectionActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
