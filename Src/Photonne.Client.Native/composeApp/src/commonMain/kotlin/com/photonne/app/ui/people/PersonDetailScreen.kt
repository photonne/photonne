package com.photonne.app.ui.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.main.floatingNavBarReservedHeight

@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit
) {
    val apiBaseUrl = rememberApiBaseUrl()
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isInitialLoading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null && state.items.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    com.photonne.app.ui.error.ErrorBanner(error = state.error)
                }
            else -> AssetGrid(
                items = state.items,
                baseUrl = apiBaseUrl,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                selectedIds = state.selection,
                hasMore = state.hasMore,
                isAppending = state.isAppending,
                isInitialLoading = state.isInitialLoading,
                onLoadMore = onLoadMore,
                contentPadding = PaddingValues(bottom = floatingNavBarReservedHeight()),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
