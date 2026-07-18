package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.archive_action_unarchive
import com.photonne.app.resources.archive_title
import com.photonne.app.resources.archived_empty_subtitle
import com.photonne.app.resources.archived_empty_title
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArchivedScreen(
    state: ArchivedUiState,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onUnarchiveAll: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val chromeFloating = !state.isSelectionActive
    val reservedTop = if (chromeFloating) subscreenChromeReservedTop() else 0.dp

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
                    Box(modifier = Modifier.fillMaxSize().padding(top = reservedTop).padding(24.dp)) {
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
                    gridState = gridState,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    selectedIds = state.selection,
                    hasMore = state.hasMore,
                    isAppending = state.isAppending,
                    isInitialLoading = state.isInitialLoading,
                    onLoadMore = onLoadMore,
                    contentPadding = PaddingValues(
                        top = reservedTop,
                        bottom = floatingNavBarReservedHeight()
                    ),
                    modifier = Modifier.fillMaxWidth().hazeSource(hazeState)
                )
            }

            if (chromeFloating) {
                SubscreenFloatingChrome(
                    title = stringResource(Res.string.archive_title),
                    onBack = onBack,
                    scroll = SubscreenScroll(
                        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                        firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                        isScrollInProgress = { gridState.isScrollInProgress },
                        scrollToTopMinIndex = SCROLL_TO_TOP_MIN_CELL,
                        onScrollToTop = {
                            if (gridState.firstVisibleItemIndex > SCROLL_TO_TOP_SNAP_CELL) {
                                gridState.scrollToItem(SCROLL_TO_TOP_SNAP_CELL)
                            }
                            gridState.animateScrollToItem(0)
                        }
                    ),
                    hazeState = hazeState,
                    onChromeVisibleChange = onChromeVisibleChange,
                    statusBarScrim = true,
                    actions = if (state.items.isNotEmpty()) {
                        {
                            IconButton(onClick = onUnarchiveAll) {
                                Icon(
                                    Icons.Outlined.Unarchive,
                                    contentDescription = stringResource(Res.string.archive_action_unarchive)
                                )
                            }
                        }
                    } else null
                )
            }
        }
    }
}

private const val SCROLL_TO_TOP_MIN_CELL = 12
private const val SCROLL_TO_TOP_SNAP_CELL = 48
