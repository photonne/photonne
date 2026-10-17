package com.photonne.app.ui.library

import androidx.compose.runtime.Composable
import com.photonne.app.resources.Res
import com.photonne.app.resources.videos_empty_subtitle
import com.photonne.app.resources.videos_empty_title
import com.photonne.app.resources.videos_title
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.theme.PhotonneIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun VideosScreen(
    state: VideosUiState,
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
        title = stringResource(Res.string.videos_title),
        items = state.items,
        selection = state.selection,
        hasMore = state.hasMore,
        isInitialLoading = state.isInitialLoading,
        isAppending = state.isAppending,
        isRefreshing = state.isRefreshing,
        isEmpty = state.isEmpty,
        error = state.error,
        emptyIcon = PhotonneIcons.Videos,
        emptyTitle = stringResource(Res.string.videos_empty_title),
        emptySubtitle = stringResource(Res.string.videos_empty_subtitle),
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
