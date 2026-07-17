package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.archived_empty_subtitle
import com.photonne.app.resources.archived_empty_title
import androidx.compose.foundation.layout.PaddingValues
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArchivedScreen(
    state: ArchivedUiState,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit
) {
    val apiBaseUrl = rememberApiBaseUrl()

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
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
                state.isEmpty ->
                    EmptyState(
                        icon = Icons.Outlined.Archive,
                        title = stringResource(Res.string.archived_empty_title),
                        subtitle = stringResource(Res.string.archived_empty_subtitle)
                    )
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
}
