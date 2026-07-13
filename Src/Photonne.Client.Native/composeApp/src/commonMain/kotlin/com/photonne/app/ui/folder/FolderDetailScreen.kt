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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.folders_empty_subtitle
import com.photonne.app.resources.folders_empty_title
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.main.CompactNavBarContentHeight
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
    val gridContentPadding = if (immersive) {
        PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                CompactNavBarContentHeight
        )
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
            state.items.isEmpty() && state.subFolders.isNotEmpty() ->
                SubfolderList(
                    subFolders = state.subFolders,
                    selectedSubfolderId = state.selectedSubfolderId,
                    onSubfolderClick = onSubfolderClick,
                    onSubfolderLongPress = onSubfolderLongPress
                )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                if (state.subFolders.isNotEmpty()) {
                    SubfolderList(
                        subFolders = state.subFolders,
                        selectedSubfolderId = state.selectedSubfolderId,
                        onSubfolderClick = onSubfolderClick,
                        onSubfolderLongPress = onSubfolderLongPress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
                AssetGrid(
                    items = state.items,
                    baseUrl = apiBaseUrl,
                    gridState = gridState,
                    contentPadding = gridContentPadding,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    selectedIds = state.selection,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SubfolderList(
    subFolders: List<FolderSummary>,
    selectedSubfolderId: String?,
    onSubfolderClick: (FolderSummary) -> Unit,
    onSubfolderLongPress: (FolderSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(subFolders, key = { it.id }) { folder ->
            SubfolderRow(
                folder = folder,
                isSelected = selectedSubfolderId == folder.id,
                onClick = { onSubfolderClick(folder) },
                onLongPress = { onSubfolderLongPress(folder) }
            )
        }
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
