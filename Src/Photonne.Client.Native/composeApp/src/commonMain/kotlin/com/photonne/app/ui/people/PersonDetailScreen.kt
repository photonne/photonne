package com.photonne.app.ui.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.ui.grid.AssetGrid
import org.koin.compose.koinInject

@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val config: PhotonneAppConfig = koinInject()
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isInitialLoading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.errorMessage != null && state.items.isEmpty() ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                }
            else -> AssetGrid(
                items = state.items,
                baseUrl = config.apiBaseUrl,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                selectedIds = state.selection,
                hasMore = state.hasMore,
                isAppending = state.isAppending,
                isInitialLoading = state.isInitialLoading,
                onLoadMore = onLoadMore,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
