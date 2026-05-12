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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_empty_subtitle
import com.photonne.app.resources.timeline_empty_title
import com.photonne.app.ui.grid.GroupedAssetGrid
import com.photonne.app.ui.grid.TimelineEntry
import com.photonne.app.ui.grid.findEntryIndexForMonth
import com.photonne.app.ui.grid.groupTimelineEntries
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onItemClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit = {},
    onItemLongClick: ((Int) -> Unit)? = null,
    pendingJumpDate: Instant? = null,
    onJumpHandled: () -> Unit = {},
    onMemoryClick: (List<com.photonne.app.data.models.TimelineItem>, Int) -> Unit = { _, _ -> }
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val pullState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()
    val memoriesViewModel: MemoriesViewModel = koinViewModel()
    val memoriesState by memoriesViewModel.state.collectAsState()
    var memorySheetItems by remember { mutableStateOf<List<com.photonne.app.data.models.TimelineItem>?>(null) }

    val entries = remember(state.items) { groupTimelineEntries(state.items) }

    val stickyHeader by remember(entries) {
        derivedStateOf {
            if (entries.isEmpty()) return@derivedStateOf null
            val firstIndex = gridState.firstVisibleItemIndex
            val firstOffset = gridState.firstVisibleItemScrollOffset
            // Don't show overlay if the actual header is fully visible at the top.
            val firstEntry = entries.getOrNull(firstIndex)
            if (firstEntry is TimelineEntry.Header && firstOffset == 0) return@derivedStateOf null
            // Walk back to find the most recent header.
            var i = firstIndex.coerceAtMost(entries.size - 1)
            while (i >= 0) {
                val entry = entries[i]
                if (entry is TimelineEntry.Header) return@derivedStateOf entry
                i--
            }
            null
        }
    }

    LaunchedEffect(pendingJumpDate, state.items) {
        val target = pendingJumpDate ?: return@LaunchedEffect
        if (state.items.isEmpty()) {
            onJumpHandled()
            return@LaunchedEffect
        }
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
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (memoriesState.items.isNotEmpty()) {
                        MemoriesCarousel(
                            items = memoriesState.items,
                            baseUrl = apiBaseUrl,
                            onGroupClick = { groupItems ->
                                if (groupItems.size > 1) {
                                    memorySheetItems = groupItems
                                } else {
                                    onMemoryClick(groupItems, 0)
                                }
                            }
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        GroupedAssetGrid(
                            items = state.items,
                            baseUrl = apiBaseUrl,
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
                        stickyHeader?.let { header ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                                    .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = header.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
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

    memorySheetItems?.let { items ->
        MemoryGroupSheet(
            items = items,
            baseUrl = apiBaseUrl,
            onPhotoClick = { index ->
                memorySheetItems = null
                onMemoryClick(items, index)
            },
            onDismiss = { memorySheetItems = null }
        )
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
