package com.photonne.app.ui.organize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.organize_inbox_empty_subtitle
import com.photonne.app.resources.organize_inbox_empty_title
import com.photonne.app.resources.organize_inbox_header
import com.photonne.app.resources.organize_inbox_title
import com.photonne.app.resources.organize_rule_title
import androidx.compose.foundation.layout.PaddingValues
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/**
 * "Para organizar" inbox: a paged grid of the assets still sitting under
 * MobileBackup (dropped by automatic backup, not yet filed). Long-press to
 * multi-select; the selection bars (App.kt) drive the move-out action that
 * files them into a folder and removes them from here.
 */
@Composable
fun OrganizeInboxScreen(
    state: OrganizeInboxUiState,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val apiBaseUrl = rememberApiBaseUrl()
    // Fuente de blur del cromo: la rejilla que scrollea por detrás, de la que
    // las cápsulas son HERMANAS — la regla de Haze.
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    // Con una selección activa manda la cápsula sólida del Scaffold, que YA
    // empuja la rejilla hacia abajo: reservar además el hueco del cromo
    // flotante dejaría una banda muerta del doble de alta.
    val chromeFloating = !state.isSelectionActive
    val reservedTop = if (chromeFloating) subscreenChromeReservedTop() else 0.dp

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
        // El cromo envuelve todas las ramas: las de carga / vacío también
        // necesitan su barra (y su botón de volver).
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading && state.items.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.isEmpty ->
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = stringResource(Res.string.organize_inbox_empty_title),
                        subtitle = stringResource(Res.string.organize_inbox_empty_subtitle)
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
                        contentPadding = PaddingValues(
                            top = reservedTop,
                            bottom = floatingNavBarReservedHeight()
                        ),
                        modifier = Modifier.fillMaxWidth().hazeSource(hazeState),
                        header = {
                            Text(
                                text = stringResource(Res.string.organize_inbox_header),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    )
            }

            if (chromeFloating) {
                SubscreenFloatingChrome(
                    title = stringResource(Res.string.organize_inbox_title),
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
                    // Fotos a sangre bajo la status bar: sin scrim el reloj se
                    // pierde sobre según qué miniatura.
                    statusBarScrim = true,
                    actions = {
                        IconButton(onClick = onOpenRules) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = stringResource(
                                    Res.string.organize_rule_title
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

/** Cells scrolled past before the back-to-top pill appears (~3 rows). */
private const val SCROLL_TO_TOP_MIN_CELL = 12

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_CELL = 48
