package com.photonne.app.ui.timeline

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.ui.grid.AssetGrid
import org.koin.compose.koinInject

@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onItemClick: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val config: PhotonneAppConfig = koinInject()
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isInitialLoading -> CenteredLoading()
            state.isEmpty -> EmptyState()
            else -> AssetGrid(
                items = state.items,
                baseUrl = config.apiBaseUrl,
                onItemClick = onItemClick,
                hasMore = state.hasMore,
                isAppending = state.isAppending,
                isInitialLoading = state.isInitialLoading,
                onLoadMore = onLoadMore,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (state.isAppending) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
            }
        }
        state.errorMessage?.let { ErrorBanner(it, modifier = Modifier.align(Alignment.TopCenter)) }
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No hay assets en la línea de tiempo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Indexa una carpeta desde la app web para empezar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
