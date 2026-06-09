package com.photonne.app.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
    viewModel: FolderDetailViewModel
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()

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
                    onSubfolderClick = onSubfolderClick
                )
            else -> Column(modifier = Modifier.fillMaxSize()) {
                if (state.subFolders.isNotEmpty()) {
                    SubfolderList(
                        subFolders = state.subFolders,
                        onSubfolderClick = onSubfolderClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
                AssetGrid(
                    items = state.items,
                    baseUrl = apiBaseUrl,
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
    onSubfolderClick: (FolderSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(subFolders, key = { it.id }) { folder ->
            SubfolderRow(folder = folder, onClick = { onSubfolderClick(folder) })
        }
    }
}

@Composable
private fun SubfolderRow(folder: FolderSummary, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
