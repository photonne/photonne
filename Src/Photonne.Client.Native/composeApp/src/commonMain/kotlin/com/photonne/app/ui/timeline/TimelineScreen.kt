package com.photonne.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.di.PhotonneAppConfig
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_empty_subtitle
import com.photonne.app.resources.timeline_empty_title
import com.photonne.app.ui.grid.GroupedAssetGrid
import com.photonne.app.ui.grid.findEntryIndexForMonth
import com.photonne.app.ui.grid.groupTimelineEntries
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onItemClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit = {},
    onItemLongClick: ((Int) -> Unit)? = null,
    pendingJumpDate: Instant? = null,
    onJumpHandled: () -> Unit = {}
) {
    val config: PhotonneAppConfig = koinInject()
    val pullState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(pendingJumpDate, state.items) {
        val target = pendingJumpDate ?: return@LaunchedEffect
        if (state.items.isEmpty()) {
            onJumpHandled()
            return@LaunchedEffect
        }
        val entries = groupTimelineEntries(state.items)
        val targetDate = target.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val index = findEntryIndexForMonth(entries, targetDate)
        if (index >= 0) {
            runCatching { gridState.animateScrollToItem(index) }
        }
        onJumpHandled()
    }

    PullToRefreshBox(
        state = pullState,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading -> CenteredLoading()
                state.isEmpty -> EmptyState()
                else -> GroupedAssetGrid(
                    items = state.items,
                    baseUrl = config.apiBaseUrl,
                    onItemClick = onItemClick,
                    gridState = gridState,
                    hasMore = state.hasMore,
                    isAppending = state.isAppending,
                    isInitialLoading = state.isInitialLoading,
                    onLoadMore = onLoadMore,
                    selectedIds = state.selection,
                    onItemLongClick = onItemLongClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (state.isAppending) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp).align(Alignment.BottomCenter),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp))
                }
            }
            state.errorMessage?.let { ErrorBanner(it, modifier = Modifier.align(Alignment.TopCenter)) }
        }
    }
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(Res.string.timeline_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.timeline_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp)
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
