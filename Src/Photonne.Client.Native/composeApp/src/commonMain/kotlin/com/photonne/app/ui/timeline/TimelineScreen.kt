package com.photonne.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.settings.TimelineZoomLevel
import com.photonne.app.data.settings.TimelineZoomStore
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_empty_subtitle
import com.photonne.app.resources.timeline_empty_title
import com.photonne.app.ui.devicesync.DeviceSyncViewModel
import com.photonne.app.ui.grid.GroupedAssetGrid
import com.photonne.app.ui.grid.TimelineEntry
import com.photonne.app.ui.grid.findEntryIndexForDate
import com.photonne.app.ui.grid.groupTimelineEntries
import com.photonne.app.ui.grid.mergeTimelineWithLocal
import com.photonne.app.ui.theme.EmptyState
import kotlin.math.abs
import kotlin.math.ln
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    /**
     * Opens the asset viewer with [items] (server entries possibly
     * interleaved with device-pending ones from the Backup module) at
     * the given [mergedIndex]. The pager and detail viewer handle
     * local items in-place via their `localUri`/`localThumbnailModel`
     * fields, so the same viewer covers both kinds.
     */
    onOpenAsset: (items: List<com.photonne.app.data.models.TimelineItem>, mergedIndex: Int) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit = {},
    onToggleSelection: ((assetId: String) -> Unit)? = null,
    pendingJumpDate: Instant? = null,
    onJumpHandled: () -> Unit = {},
    onMemoryClick: (List<com.photonne.app.data.models.TimelineItem>, Int) -> Unit = { _, _ -> }
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val pullState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()
    val zoomStore: TimelineZoomStore = koinInject()
    val zoomLevel by zoomStore.value.collectAsState()
    val memoriesViewModel: MemoriesViewModel = koinViewModel()
    val memoriesState by memoriesViewModel.state.collectAsState()
    val deviceSyncViewModel: DeviceSyncViewModel = koinViewModel()
    val deviceSyncState by deviceSyncViewModel.state.collectAsState()
    var memorySheetItems by remember { mutableStateOf<List<com.photonne.app.data.models.TimelineItem>?>(null) }

    // Drives the Memories carousel collapse: scrolling up consumes deltas to
    // shrink the row from 110x150 to 40x60; reaching the top of the grid
    // expands it back. Pull-to-refresh still works because we only consume
    // post-scroll deltas while the carousel is partially collapsed.
    val density = LocalDensity.current
    val collapseRangePx = remember(density) { with(density) { 120.dp.toPx() } }
    var collapseOffsetPx by remember { mutableFloatStateOf(0f) }
    val collapseFraction = (-collapseOffsetPx / collapseRangePx).coerceIn(0f, 1f)
    val hasMemories = memoriesState.items.isNotEmpty()
    val nestedScrollConnection = remember(collapseRangePx, hasMemories) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!hasMemories) return Offset.Zero
                val delta = available.y
                if (delta < 0 && collapseOffsetPx > -collapseRangePx) {
                    val newOffset = (collapseOffsetPx + delta).coerceIn(-collapseRangePx, 0f)
                    val consumed = newOffset - collapseOffsetPx
                    collapseOffsetPx = newOffset
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!hasMemories) return Offset.Zero
                val delta = available.y
                if (delta > 0 && collapseOffsetPx < 0f) {
                    val newOffset = (collapseOffsetPx + delta).coerceIn(-collapseRangePx, 0f)
                    val consumedDelta = newOffset - collapseOffsetPx
                    collapseOffsetPx = newOffset
                    return Offset(0f, consumedDelta)
                }
                return Offset.Zero
            }
        }
    }

    // Only mix device entries in once backup is enabled — otherwise the
    // timeline reflects only what's actually on the server.
    val localItems = remember(deviceSyncState.entries, deviceSyncState.isBackupEnabled) {
        if (!deviceSyncState.isBackupEnabled) emptyList()
        else deviceSyncViewModel.deviceTimelineItems()
    }
    val mergedItems = remember(state.items, localItems) {
        mergeTimelineWithLocal(state.items, localItems)
    }
    val entries = remember(mergedItems, zoomLevel) {
        groupTimelineEntries(mergedItems, zoomLevel.grouping)
    }

    // Continuously snapshot the topmost visible cell's date — used to
    // re-anchor the grid when the zoom level changes (dropdown or pinch)
    // so the user's place in time is preserved across re-groupings.
    val anchorDate: LocalDate? by remember {
        derivedStateOf {
            val idx = gridState.firstVisibleItemIndex
            if (entries.isEmpty()) return@derivedStateOf null
            val end = minOf(idx + 8, entries.size - 1)
            for (i in idx..end) {
                val e = entries.getOrNull(i)
                if (e is TimelineEntry.Cell) {
                    return@derivedStateOf e.item.fileCreatedAt
                        .toLocalDateTime(TimeZone.currentSystemDefault()).date
                }
            }
            null
        }
    }

    var isFirstZoomComposition by remember { mutableStateOf(true) }
    LaunchedEffect(zoomLevel) {
        if (isFirstZoomComposition) {
            isFirstZoomComposition = false
            return@LaunchedEffect
        }
        val target = anchorDate ?: return@LaunchedEffect
        val newIdx = findEntryIndexForDate(entries, target, zoomLevel.grouping)
        if (newIdx >= 0) {
            runCatching { gridState.animateScrollToItem(newIdx) }
        }
    }

    // Pinch state: during a two-finger zoom the grid's cellMinSize lerps
    // toward the next zoom level for visual feedback, then snaps to a
    // discrete level on gesture end. Grouping stays at zoomLevel.grouping
    // for the duration of the gesture — only the size animates.
    var gestureActive by remember { mutableStateOf(false) }
    var gestureZoom by remember { mutableFloatStateOf(1f) }
    val gestureTarget: TimelineZoomLevel = remember(zoomLevel, gestureZoom) {
        if (gestureZoom > 1f) nextZoomLevelIn(zoomLevel)
        else nextZoomLevelOut(zoomLevel)
    }
    val gestureFraction: Float = remember(gestureZoom) {
        if (gestureZoom <= 0f) 0f
        else (abs(ln(gestureZoom)) / ln(1.5f)).toFloat().coerceIn(0f, 1f)
    }
    val effectiveCellMinSize = if (!gestureActive) {
        zoomLevel.cellMinSizeDp.dp
    } else {
        lerp(
            zoomLevel.cellMinSizeDp.dp,
            gestureTarget.cellMinSizeDp.dp,
            gestureFraction
        )
    }

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
        val index = findEntryIndexForDate(entries, targetDate, zoomLevel.grouping)
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
                state.isEmpty -> TimelineEmptyState()
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    if (hasMemories) {
                        MemoriesCarousel(
                            items = memoriesState.items,
                            baseUrl = apiBaseUrl,
                            collapseFraction = collapseFraction,
                            onGroupClick = { groupItems ->
                                if (groupItems.size > 1) {
                                    memorySheetItems = groupItems
                                } else {
                                    onMemoryClick(groupItems, 0)
                                }
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTimelinePinch(
                                    onZoomStart = {
                                        gestureActive = true
                                        gestureZoom = 1f
                                    },
                                    onZoom = { z -> gestureZoom = z },
                                    onZoomEnd = { finalZoom ->
                                        gestureActive = false
                                        val fraction = if (finalZoom <= 0f) 0f
                                        else (abs(ln(finalZoom)) / ln(1.5f))
                                            .toFloat().coerceIn(0f, 1f)
                                        if (fraction > 0.5f) {
                                            val target = if (finalZoom > 1f)
                                                nextZoomLevelIn(zoomLevel)
                                            else nextZoomLevelOut(zoomLevel)
                                            if (target != zoomLevel) {
                                                zoomStore.update(target)
                                            }
                                        }
                                        gestureZoom = 1f
                                    }
                                )
                            }
                    ) {
                        GroupedAssetGrid(
                            items = mergedItems,
                            baseUrl = apiBaseUrl,
                            onItemClick = { mergedIndex ->
                                val item = mergedItems.getOrNull(mergedIndex)
                                    ?: return@GroupedAssetGrid
                                val toggler = onToggleSelection
                                if (state.isSelectionActive && !item.isLocalOnly &&
                                    toggler != null) {
                                    // Tap-to-multi-select only applies to
                                    // server assets — local-only items
                                    // aren't part of the timeline's bulk
                                    // operations.
                                    toggler(item.id)
                                } else {
                                    onOpenAsset(mergedItems, mergedIndex)
                                }
                            },
                            gridState = gridState,
                            hasMore = state.hasMore,
                            isAppending = state.isAppending,
                            isInitialLoading = state.isInitialLoading,
                            onLoadMore = onLoadMore,
                            selectedIds = state.selection,
                            onItemLongClick = onToggleSelection?.let { toggler ->
                                { mergedIndex ->
                                    val item = mergedItems.getOrNull(mergedIndex)
                                    if (item != null && !item.isLocalOnly) {
                                        toggler(item.id)
                                    }
                                }
                            },
                            grouping = zoomLevel.grouping,
                            cellMinSize = effectiveCellMinSize,
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

/**
 * Returns the next-most-zoomed-in level. Levels are ordered Year (most
 * zoomed-out) → Month → DayLarge → DayMedium → DaySmall (most zoomed-in).
 */
private fun nextZoomLevelIn(current: TimelineZoomLevel): TimelineZoomLevel {
    val all = TimelineZoomLevel.entries
    return all.getOrNull(current.ordinal + 1) ?: current
}

/** Inverse of [nextZoomLevelIn]: returns the next-most-zoomed-out level. */
private fun nextZoomLevelOut(current: TimelineZoomLevel): TimelineZoomLevel {
    val all = TimelineZoomLevel.entries
    return all.getOrNull(current.ordinal - 1) ?: current
}

@Composable
private fun CenteredLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TimelineEmptyState() {
    EmptyState(
        icon = Icons.Outlined.PhotoLibrary,
        title = stringResource(Res.string.timeline_empty_title),
        subtitle = stringResource(Res.string.timeline_empty_subtitle)
    )
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
