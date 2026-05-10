package com.photonne.app.ui.folder

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
import com.photonne.app.resources.folders_empty_subtitle
import com.photonne.app.resources.folders_empty_title
import com.photonne.app.ui.grid.AssetGrid
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun FolderDetailScreen(
    folderId: String,
    folderName: String,
    onItemClick: (Int) -> Unit,
    viewModel: FolderDetailViewModel
) {
    val config: PhotonneAppConfig = koinInject()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(folderId) { viewModel.open(folderId, folderName) }

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
                            stringResource(Res.string.folders_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(Res.string.folders_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            else -> AssetGrid(
                items = state.items,
                baseUrl = config.apiBaseUrl,
                onItemClick = onItemClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
