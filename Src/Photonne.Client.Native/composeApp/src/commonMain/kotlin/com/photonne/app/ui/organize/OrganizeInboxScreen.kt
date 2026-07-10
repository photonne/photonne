package com.photonne.app.ui.organize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.organize_inbox_empty_subtitle
import com.photonne.app.resources.organize_inbox_empty_title
import com.photonne.app.resources.organize_inbox_header
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
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
) {
    val apiBaseUrl = rememberApiBaseUrl()

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
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
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    selectedIds = state.selection,
                    hasMore = state.hasMore,
                    isAppending = state.isAppending,
                    isInitialLoading = state.isInitialLoading,
                    onLoadMore = onLoadMore,
                    modifier = Modifier.fillMaxWidth(),
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
    }
}
