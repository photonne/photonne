package com.photonne.app.ui.people

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
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
import com.photonne.app.resources.people_action_merge
import com.photonne.app.resources.people_action_rename
import com.photonne.app.resources.people_action_suggestions
import com.photonne.app.resources.people_action_unhide
import com.photonne.app.resources.people_unnamed
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

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
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val chromeFloating = !state.isSelectionActive
    val reservedTop = if (chromeFloating) subscreenChromeReservedTop() else 0.dp

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
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.action_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_rename)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { menuOpen = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_suggestions)) },
                onClick = { menuOpen = false; onSuggestions() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_merge)) },
                onClick = { menuOpen = false; onMerge() }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (isHidden) stringResource(Res.string.people_action_unhide)
                        else stringResource(Res.string.people_action_hide)
                    )
                },
                onClick = { menuOpen = false; onToggleHidden() }
            )
        }
    }
}

private const val SCROLL_TO_TOP_MIN_CELL = 12
private const val SCROLL_TO_TOP_SNAP_CELL = 48
