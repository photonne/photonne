package com.photonne.app.ui.organize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.organize_excluded_empty_subtitle
import com.photonne.app.resources.organize_excluded_empty_title
import com.photonne.app.resources.organize_excluded_header
import com.photonne.app.resources.organize_excluded_title
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.chromeSelectionActive
import com.photonne.app.ui.grid.rememberAssetGridSelectionGestures
import com.photonne.app.ui.main.ResultSnackbar
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/**
 * Rejilla de lo apartado de "Para organizar". Misma forma que la bandeja: toque
 * abre el visor, pulsación larga (o arrastre) selecciona, y con selección la
 * barra acoplada de App.kt ofrece "Devolver a la bandeja".
 */
@Composable
fun OrganizeExcludedScreen(
    state: OrganizeExcludedUiState,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onBack: () -> Unit,
    onApplySelection: (SelectionPatch) -> Unit = {},
    onErrorShown: () -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val gestures = rememberAssetGridSelectionGestures(onApplySelection)
    val chromeFloating = !gestures.chromeSelectionActive(state.isSelectionActive)
    val reservedTop = if (chromeFloating) subscreenChromeReservedTop() else 0.dp

    LaunchedEffect(Unit) { onLoad() }

    // Un fallo con la rejilla ya pintada (devolver, paginar) va al snackbar; el
    // banner queda para cuando no hay nada que enseñar.
    ResultSnackbar(
        message = state.error?.userMessage?.takeIf { state.items.isNotEmpty() },
        onShown = onErrorShown
    )

    PhotonneRefreshableScreen(
        indicatorTopPadding = reservedTop,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading && state.items.isEmpty() ->
                    AssetGridSkeleton(
                        contentPadding = PaddingValues(
                            top = reservedTop,
                            bottom = floatingNavBarReservedHeight()
                        )
                    )
                state.error != null && state.items.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .padding(top = reservedTop)
                            .padding(Spacing.lg)
                    ) {
                        ErrorBanner(error = state.error, onRetry = onRefresh)
                    }
                state.isEmpty ->
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(Res.string.organize_excluded_empty_title),
                        subtitle = stringResource(Res.string.organize_excluded_empty_subtitle)
                    )
                else ->
                    AssetGrid(
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
                        dragSelect = gestures.dragSelect,
                        contentPadding = PaddingValues(
                            top = reservedTop,
                            bottom = floatingNavBarReservedHeight()
                        ),
                        modifier = Modifier.fillMaxWidth().hazeSource(hazeState),
                        header = {
                            Text(
                                text = stringResource(Res.string.organize_excluded_header),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                            )
                        }
                    )
            }

            if (chromeFloating) {
                SubscreenFloatingChrome(
                    title = stringResource(Res.string.organize_excluded_title),
                    onBack = onBack,
                    scroll = SubscreenScroll(
                        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                        firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                        isScrollInProgress = { gridState.isScrollInProgress },
                        scrollToTopMinIndex = 12,
                        onScrollToTop = { gridState.animateScrollToItem(0) }
                    ),
                    hazeState = hazeState,
                    onChromeVisibleChange = onChromeVisibleChange,
                    statusBarScrim = true,
                )
            }
        }
    }
}
