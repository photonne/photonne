package com.photonne.app.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_section_pinned
import com.photonne.app.resources.collections_pinned_empty_subtitle
import com.photonne.app.resources.collections_pinned_empty_title
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/**
 * "Fijados" a pantalla completa: álbumes y carpetas fijados, mezclados y del
 * último fijado al primero, en la misma rejilla que Álbumes y Carpetas.
 * Desfijar se hace desde cada uno (⋮ del detalle o su selección).
 */
@Composable
fun PinnedCollectionsScreen(
    pinned: List<PinnedEntry>,
    baseUrl: String,
    onOpen: (PinnedEntry) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val gridState = rememberLazyGridState()
    val hazeState = remember { HazeState() }
    val reservedTop = subscreenChromeReservedTop()

    Box(modifier = Modifier.fillMaxSize()) {
        if (pinned.isEmpty()) {
            EmptyState(
                icon = PhotonneIcons.Pin,
                title = stringResource(Res.string.collections_pinned_empty_title),
                subtitle = stringResource(Res.string.collections_pinned_empty_subtitle),
                modifier = Modifier.fillMaxSize().padding(top = reservedTop)
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 100.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    top = Spacing.lg + reservedTop,
                    end = Spacing.lg,
                    bottom = Spacing.lg + floatingNavBarReservedHeight()
                ),
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
            ) {
                items(pinned, key = { it.key }) { entry ->
                    PinnedTile(entry = entry, baseUrl = baseUrl, onClick = { onOpen(entry) })
                }
            }
        }

        SubscreenFloatingChrome(
            title = stringResource(Res.string.albums_section_pinned),
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                isScrollInProgress = { gridState.isScrollInProgress },
                scrollToTopMinIndex = 9,
                onScrollToTop = { gridState.animateScrollToItem(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
}
