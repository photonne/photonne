package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.favorites_empty_subtitle
import com.photonne.app.resources.favorites_empty_title
import com.photonne.app.resources.favorites_title
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
fun FavoritesScreen(
    state: FavoritesUiState,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    // Fuente de blur del cromo: la rejilla que scrollea por detrás, de la que las
    // cápsulas son HERMANAS (regla de Haze).
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    // Con selección activa manda la barra sólida del Scaffold, que ya empuja la
    // rejilla: reservar además el cromo flotante dejaría una banda muerta doble.
    val chromeFloating = !state.isSelectionActive
    val reservedTop = if (chromeFloating) subscreenChromeReservedTop() else 0.dp

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
        // El cromo envuelve todas las ramas: carga / error / vacío también
        // necesitan su barra (y su botón de volver).
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
                        icon = Icons.Outlined.FavoriteBorder,
                        title = stringResource(Res.string.favorites_empty_title),
                        subtitle = stringResource(Res.string.favorites_empty_subtitle)
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
                    title = stringResource(Res.string.favorites_title),
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
                    // Fotos a sangre bajo la status bar: sin scrim el reloj se pierde
                    // sobre según qué miniatura.
                    statusBarScrim = true
                )
            }
        }
    }
}

/** Celdas scrolleadas antes de que aparezca la píldora de volver arriba. */
private const val SCROLL_TO_TOP_MIN_CELL = 12

/** Adónde teletransporta el tap antes de animar el último tramo. */
private const val SCROLL_TO_TOP_SNAP_CELL = 48
