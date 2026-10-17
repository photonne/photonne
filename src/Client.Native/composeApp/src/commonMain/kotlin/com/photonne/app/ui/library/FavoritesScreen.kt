package com.photonne.app.ui.library

import androidx.compose.runtime.Composable
import com.photonne.app.resources.Res
import com.photonne.app.resources.favorites_empty_subtitle
import com.photonne.app.resources.favorites_empty_title
import com.photonne.app.resources.favorites_title
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.theme.PhotonneIcons
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
    onApplySelection: (SelectionPatch) -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    AssetCollectionGridScreen(
        title = stringResource(Res.string.favorites_title),
        items = state.items,
        selection = state.selection,
        hasMore = state.hasMore,
        isInitialLoading = state.isInitialLoading,
        isAppending = state.isAppending,
        isRefreshing = state.isRefreshing,
        isEmpty = state.isEmpty,
        error = state.error,
        emptyIcon = PhotonneIcons.Favorite,
        emptyTitle = stringResource(Res.string.favorites_empty_title),
        emptySubtitle = stringResource(Res.string.favorites_empty_subtitle),
        onItemClick = onItemClick,
        onItemLongClick = onItemLongClick,
        onLoadMore = onLoadMore,
        onLoad = onLoad,
        onRefresh = onRefresh,
        onBack = onBack,
        onApplySelection = onApplySelection,
        onChromeVisibleChange = onChromeVisibleChange
    )
}
