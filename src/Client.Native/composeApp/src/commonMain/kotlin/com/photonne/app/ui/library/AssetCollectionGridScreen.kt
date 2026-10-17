package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import androidx.compose.ui.graphics.vector.ImageVector
import com.photonne.app.data.error.UiError
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.PhotoGridScrubberOverlay
import com.photonne.app.ui.grid.rememberAssetGridSelectionGestures
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Rejilla de una colección paginada de fotos (Favoritos, Vídeos): carga,
 * error, vacío, selección por arrastre, scrubber y el cromo flotante de
 * subpantalla con su título.
 */
@Composable
internal fun AssetCollectionGridScreen(
    title: String,
    items: List<TimelineItem>,
    selection: Set<String>,
    hasMore: Boolean,
    isInitialLoading: Boolean,
    isAppending: Boolean,
    isRefreshing: Boolean,
    isEmpty: Boolean,
    error: UiError?,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onApplySelection: (SelectionPatch) -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    // Fuente de blur del cromo: la rejilla que scrollea por detrás, de la que las
    // cápsulas son HERMANAS (regla de Haze).
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val gestures = rememberAssetGridSelectionGestures(onApplySelection)
    // Con una selección activa el cromo flotante cede el sitio a la cápsula de
    // selección, que ocupa el mismo hueco: la rejilla lo reserva siempre, así
    // que entrar o salir de la selección no la mueve (ni a mitad de arrastre).
    val chromeFloating = selection.isEmpty()
    val reservedTop = subscreenChromeReservedTop()

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        indicatorTopPadding = reservedTop,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh
    ) {
        // El cromo envuelve todas las ramas: carga / error / vacío también
        // necesitan su barra (y su botón de volver).
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isInitialLoading ->
                    AssetGridSkeleton(
                        contentPadding = PaddingValues(
                            top = reservedTop,
                            bottom = floatingNavBarReservedHeight()
                        )
                    )
                error != null && items.isEmpty() ->
                    com.photonne.app.ui.error.FullScreenError(
                        error = error,
                        onRetry = onRefresh,
                        modifier = Modifier.padding(top = reservedTop)
                    )
                isEmpty ->
                    EmptyState(
                        icon = emptyIcon,
                        title = emptyTitle,
                        subtitle = emptySubtitle
                    )
                else -> AssetGrid(
                    items = items,
                    baseUrl = apiBaseUrl,
                    gridState = gridState,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    selectedIds = selection,
                    hasMore = hasMore,
                    isAppending = isAppending,
                    isInitialLoading = isInitialLoading,
                    onLoadMore = onLoadMore,
                    dragSelect = gestures.dragSelect,
                    contentPadding = PaddingValues(
                        top = reservedTop,
                        bottom = floatingNavBarReservedHeight()
                    ),
                    modifier = Modifier.fillMaxWidth().hazeSource(hazeState)
                )
            }

            PhotoGridScrubberOverlay(
                gridState = gridState,
                items = items,
                reservedTop = reservedTop,
                reservedBottom = floatingNavBarReservedHeight(),
                selectionActive = selection.isNotEmpty(),
                hazeState = hazeState,
            )

            if (chromeFloating) {
                SubscreenFloatingChrome(
                    title = title,
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
