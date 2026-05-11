package com.photonne.app.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.resources.Res
import com.photonne.app.resources.album_empty_subtitle
import com.photonne.app.resources.album_empty_title
import com.photonne.app.ui.grid.AssetGrid
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    onItemClick: (Int) -> Unit,
    viewModel: AlbumDetailViewModel
) {
    val config: PhotonneAppConfig = koinInject()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(albumId) { viewModel.open(albumId, albumName) }

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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(Res.string.album_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.album_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            else -> AssetGrid(
                items = state.items,
                baseUrl = config.apiBaseUrl,
                onItemClick = { index ->
                    if (state.isSelectionActive) {
                        state.items.getOrNull(index)?.let { viewModel.toggleSelection(it.id) }
                    } else {
                        onItemClick(index)
                    }
                },
                onItemLongClick = { index ->
                    state.items.getOrNull(index)?.let { viewModel.toggleSelection(it.id) }
                },
                selectedIds = state.selection,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
