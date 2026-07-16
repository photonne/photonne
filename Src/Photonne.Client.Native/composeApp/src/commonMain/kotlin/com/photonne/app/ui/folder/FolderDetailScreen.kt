package com.photonne.app.ui.folder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.folders_empty_subtitle
import com.photonne.app.resources.folders_empty_title
import com.photonne.app.ui.grid.AssetGridCell
import com.photonne.app.ui.grid.assetCellKey
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.ImmersiveChromeEffect
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun FolderDetailScreen(
    folderId: String,
    folderName: String,
    parentFolderId: String?,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onSubfolderClick: (FolderSummary) -> Unit,
    onSubfolderLongPress: (FolderSummary) -> Unit,
    viewModel: FolderDetailViewModel,
    /**
     * Immersive bottom nav: while true the photo grid drives the hide-on-scroll
     * chrome (reported via [onChromeVisibleChange]) and reserves the nav's height
     * at its scroll end so it draws edge-to-edge behind the bar, like Fotos.
     */
    immersive: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    // Only drive the immersive chrome when the photo grid is what's on screen —
    // a subfolders-only view keeps the nav docked.
    val gridActive = immersive && state.items.isNotEmpty()
    if (gridActive) {
        ImmersiveChromeEffect(
            firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
            firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
            isScrollInProgress = { gridState.isScrollInProgress },
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
    // Con una selección activa (de assets o de subcarpetas) la nav flotante deja
    // su sitio a la cápsula de acciones, que mide lo mismo: se sigue reservando
    // el mismo hueco aunque `immersive` ya esté apagado.
    val immersiveContentPadding = if (immersive || state.isSelectionActive ||
        state.isSubfolderSelectionActive
    ) {
        PaddingValues(bottom = floatingNavBarReservedHeight())
    } else PaddingValues(0.dp)

    LaunchedEffect(folderId) { viewModel.open(folderId, folderName, parentFolderId) }

    PhotonneRefreshableScreen(
        isRefreshing = state.isLoading &&
            (state.items.isNotEmpty() || state.subFolders.isNotEmpty()),
        onRefresh = viewModel::refresh
    ) {
        when {
            state.isLoading && state.items.isEmpty() && state.subFolders.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null && state.items.isEmpty() && state.subFolders.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    com.photonne.app.ui.error.ErrorBanner(
                        error = state.error,
                        onRetry = { state.folderId?.let { id ->
                            viewModel.open(id, state.folderName.orEmpty(), state.parentFolderId)
                        } },
                    )
                }
            state.items.isEmpty() && state.subFolders.isEmpty() ->
                EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(Res.string.folders_empty_title),
                    subtitle = stringResource(Res.string.folders_empty_subtitle)
                )
            // Subcarpetas y assets comparten un único LazyVerticalGrid para que
            // haya un solo scroll: antes eran un LazyColumn sobre un
            // LazyVerticalGrid dentro de una Column, y la lista se quedaba con
            // casi todo el alto, aplastando la rejilla de fotos.
            else -> FolderDetailGrid(
                subFolders = state.subFolders,
                items = state.items,
                baseUrl = apiBaseUrl,
                isGrid = state.viewMode == FolderViewMode.Grid,
                selectedSubfolderId = state.selectedSubfolderId,
                selection = state.selection,
                gridState = gridState,
                contentPadding = immersiveContentPadding,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                onSubfolderClick = onSubfolderClick,
                onSubfolderLongPress = onSubfolderLongPress
            )
        }
    }
}

/**
 * One scroll for the whole folder: subfolders (as full-width rows in List mode
 * or square cards in Grid mode, mirroring the root folder screen) sit above the
 * asset thumbnails inside a single [LazyVerticalGrid]. The subfolder cards keep
 * some breathing room via their own padding while the asset cells stay tight.
 */
@Composable
private fun FolderDetailGrid(
    subFolders: List<FolderSummary>,
    items: List<TimelineItem>,
    baseUrl: String,
    isGrid: Boolean,
    selectedSubfolderId: String?,
    selection: Set<String>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onSubfolderClick: (FolderSummary) -> Unit,
    onSubfolderLongPress: (FolderSummary) -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (subFolders.isNotEmpty()) {
            if (isGrid) {
                items(
                    subFolders,
                    key = { "subfolder-${it.id}" },
                    contentType = { "subfolder-card" }
                ) { folder ->
                    SubfolderCard(
                        folder = folder,
                        isSelected = selectedSubfolderId == folder.id,
                        onClick = { onSubfolderClick(folder) },
                        onLongPress = { onSubfolderLongPress(folder) }
                    )
                }
            } else {
                items(
                    subFolders,
                    key = { "subfolder-${it.id}" },
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = { "subfolder-row" }
                ) { folder ->
                    SubfolderRow(
                        folder = folder,
                        isSelected = selectedSubfolderId == folder.id,
                        onClick = { onSubfolderClick(folder) },
                        onLongPress = { onSubfolderLongPress(folder) }
                    )
                }
            }
            if (items.isNotEmpty()) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "section-divider"
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        itemsIndexed(
            items,
            key = { index, item -> assetCellKey(item, index) }
        ) { index, asset ->
            AssetGridCell(
                asset = asset,
                baseUrl = baseUrl,
                onClick = { onItemClick(index) },
                onLongClick = { onItemLongClick(index) },
                isSelected = asset.id in selection
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubfolderCard(
    folder: FolderSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The grid arrangement is tight (matching the asset cells); this
            // per-cell padding gives the folder cards their own breathing room.
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isSelected) Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${folder.assetCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.White, shape = RoundedCornerShape(50))
                        .padding(2.dp)
                        .size(20.dp)
                )
            }
        }
        Text(
            text = folder.name.ifBlank { folder.path },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubfolderRow(
    folder: FolderSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isSelected) Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Folder,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name.ifBlank { folder.path },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = stringResource(Res.string.albums_count_format, folder.assetCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!isSelected) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
