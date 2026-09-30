package com.photonne.app.ui.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_more
import com.photonne.app.resources.people_action_hide
import com.photonne.app.resources.people_detail_empty_subtitle
import com.photonne.app.resources.people_detail_empty_title
import com.photonne.app.resources.people_action_merge
import com.photonne.app.resources.people_action_rename
import com.photonne.app.resources.people_action_suggestions
import com.photonne.app.resources.people_action_unhide
import com.photonne.app.resources.people_unnamed
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.PhotoGridScrubberOverlay
import com.photonne.app.ui.grid.rememberAssetGridSelectionGestures
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneIcons

@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    title: String,
    isHidden: Boolean,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onSuggestions: () -> Unit,
    onMerge: () -> Unit,
    onToggleHidden: () -> Unit,
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = onRetry,
    onApplySelection: (SelectionPatch) -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val gestures = rememberAssetGridSelectionGestures(onApplySelection)
    // Con una selección activa el cromo flotante cede el sitio a la cápsula de
    // selección, que ocupa el mismo hueco: la rejilla lo reserva siempre, así
    // que entrar o salir de la selección no la mueve (ni a mitad de arrastre).
    val chromeFloating = !state.isSelectionActive
    val reservedTop = subscreenChromeReservedTop()

    Box(modifier = Modifier.fillMaxSize()) {
        com.photonne.app.ui.theme.PhotonneRefreshableScreen(
            indicatorTopPadding = reservedTop,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh
        ) {
        when {
            state.isInitialLoading ->
                AssetGridSkeleton(
                    contentPadding = PaddingValues(
                        top = reservedTop,
                        bottom = floatingNavBarReservedHeight()
                    )
                )
            state.error != null && state.items.isEmpty() ->
                com.photonne.app.ui.error.FullScreenError(
                    error = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = reservedTop)
                )
            state.items.isEmpty() && state.personId != null ->
                EmptyState(
                    icon = PhotonneIcons.Person,
                    title = stringResource(Res.string.people_detail_empty_title),
                    subtitle = stringResource(Res.string.people_detail_empty_subtitle),
                    modifier = Modifier.padding(top = reservedTop)
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
                dragSelect = gestures.dragSelect,
                contentPadding = PaddingValues(
                    top = reservedTop,
                    bottom = floatingNavBarReservedHeight()
                ),
                modifier = Modifier.fillMaxWidth().hazeSource(hazeState)
            )
        }
        }

        PhotoGridScrubberOverlay(
            gridState = gridState,
            items = state.items,
            reservedTop = reservedTop,
            reservedBottom = floatingNavBarReservedHeight(),
            selectionActive = state.isSelectionActive,
            hazeState = hazeState,
        )

        if (chromeFloating) {
            SubscreenFloatingChrome(
                title = title.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.people_unnamed),
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
                actions = {
                    PersonDetailOverflowMenu(
                        isHidden = isHidden,
                        onRename = onRename,
                        onSuggestions = onSuggestions,
                        onMerge = onMerge,
                        onToggleHidden = onToggleHidden
                    )
                }
            )
        }
    }
}

@Composable
private fun PersonDetailOverflowMenu(
    isHidden: Boolean,
    onRename: () -> Unit,
    onSuggestions: () -> Unit,
    onMerge: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(PhotonneIcons.More, contentDescription = stringResource(Res.string.action_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_rename)) },
                leadingIcon = { Icon(PhotonneIcons.Rename, contentDescription = null) },
                onClick = { menuOpen = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_suggestions)) },
                leadingIcon = { Icon(PhotonneIcons.FaceSuggestions, contentDescription = null) },
                onClick = { menuOpen = false; onSuggestions() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_merge)) },
                leadingIcon = { Icon(PhotonneIcons.Merge, contentDescription = null) },
                onClick = { menuOpen = false; onMerge() }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (isHidden) stringResource(Res.string.people_action_unhide)
                        else stringResource(Res.string.people_action_hide)
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isHidden) PhotonneIcons.Show else PhotonneIcons.Hide,
                        contentDescription = null
                    )
                },
                onClick = { menuOpen = false; onToggleHidden() }
            )
        }
    }
}

private const val SCROLL_TO_TOP_MIN_CELL = 12
private const val SCROLL_TO_TOP_SNAP_CELL = 48
