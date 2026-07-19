package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.trash_empty_subtitle
import com.photonne.app.resources.trash_empty_title
import com.photonne.app.resources.trash_tab_personal
import com.photonne.app.resources.trash_tab_shared
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.PhotoGridScrubberOverlay
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/** Tabs of the unified Trash screen: the user's own trash and the shared-folder trash. */
enum class TrashTab { Personal, Shared }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashTabBar(selected: TrashTab, onSelect: (TrashTab) -> Unit) {
    val tabs = TrashTab.entries
    PrimaryTabRow(selectedTabIndex = tabs.indexOf(selected)) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        when (tab) {
                            TrashTab.Personal -> stringResource(Res.string.trash_tab_personal)
                            TrashTab.Shared -> stringResource(Res.string.trash_tab_shared)
                        }
                    )
                }
            )
        }
    }
}

@Composable
fun TrashScreen(
    state: TrashUiState,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
    hazeState: HazeState = remember { HazeState() }
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
                        icon = Icons.Outlined.Delete,
                        title = stringResource(Res.string.trash_empty_title),
                        subtitle = stringResource(Res.string.trash_empty_subtitle)
                    )
                else -> AssetGrid(
                    items = state.items,
                    baseUrl = apiBaseUrl,
                    gridState = gridState,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    selectedIds = state.selection,
                    hasMore = state.hasMore,
                    isAppending = state.isAppending,
                    isInitialLoading = state.isInitialLoading,
                    onLoadMore = onLoadMore,
                    contentPadding = PaddingValues(bottom = floatingNavBarReservedHeight()),
                    modifier = Modifier.fillMaxWidth().hazeSource(hazeState)
                )
            }

            // El host ya reserva el top (barra de pestañas + cromo), así que aquí
            // el overlay arranca en 0.
            PhotoGridScrubberOverlay(
                gridState = gridState,
                items = state.items,
                reservedTop = 0.dp,
                reservedBottom = floatingNavBarReservedHeight(),
                selectionActive = state.isSelectionActive,
                hazeState = hazeState,
            )
        }
    }
}
