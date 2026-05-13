package com.photonne.app.ui.folder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.folders_empty_subtitle
import com.photonne.app.resources.folders_empty_title
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.theme.EmptyState
import org.jetbrains.compose.resources.stringResource

@Composable
fun FolderDetailScreen(
    folderId: String,
    folderName: String,
    parentFolderId: String?,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    viewModel: FolderDetailViewModel
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(folderId) { viewModel.open(folderId, folderName, parentFolderId) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.items.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.errorMessage != null && state.items.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
            state.items.isEmpty() ->
                EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(Res.string.folders_empty_title),
                    subtitle = stringResource(Res.string.folders_empty_subtitle)
                )
            else -> AssetGrid(
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
