package com.photonne.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.settings.TimelineZoomLevel
import com.photonne.app.data.settings.TimelineZoomStore
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_empty_action_upload
import com.photonne.app.resources.timeline_empty_subtitle
import com.photonne.app.resources.timeline_empty_title
import com.photonne.app.ui.devicebackup.DeviceBackupViewModel
import com.photonne.app.ui.grid.GroupedAssetGrid
import com.photonne.app.ui.grid.JustifiedRowEntry
import com.photonne.app.ui.grid.findRowIndexForDate
import com.photonne.app.ui.grid.groupTimelineEntries
import com.photonne.app.ui.grid.mergeTimelineWithLocal
import com.photonne.app.ui.grid.packJustifiedRows
import androidx.compose.foundation.layout.aspectRatio
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.SkeletonBlock
import com.photonne.app.ui.theme.SkeletonChip
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
    onOpenUpload: (() -> Unit)? = null,
    pendingJumpDate: Instant? = null,
    onJumpHandled: () -> Unit = {},
    /** On-this-day memories shown as a carousel pinned above the grid. */
    memories: List<com.photonne.app.data.models.TimelineItem> = emptyList(),
    onOpenMemory: ((items: List<com.photonne.app.data.models.TimelineItem>, index: Int) -> Unit)? = null
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val pullState = rememberPullToRefreshState()
    val gridState = rememberLazyListState()
    val zoomStore: TimelineZoomStore = koinInject()
    val zoomLevel by zoomStore.value.collectAsState()
    val deviceBackupViewModel: DeviceBackupViewModel = koinViewModel()
    val deviceBackupState by deviceBackupViewModel.state.collectAsState()

    // Only mix device entries in once backup is enabled — otherwise the
    // timeline reflects only what's actually on the server.
    val localItems = remember(deviceBackupState.entries, deviceBackupState.isBackupEnabled) {
        if (!deviceBackupState.isBackupEnabled) emptyList()
        else deviceBackupViewModel.deviceTimelineItems()
    }
    val mergedItems = remember(state.items, localItems) {
        mergeTimelineWithLocal(state.items, localItems)
    }
    val entries = remember(mergedItems, zoomLevel) {
        groupTimelineEntries(mergedItems, zoomLevel.grouping)
    }

    // Pinch state: during a two-finger zoom the target row height lerps
    // toward the next zoom level for visual feedback, then snaps to a
    // discrete level on gesture end. Grouping stays at zoomLevel.grouping
    // for the duration of the gesture — only the row height animates.
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
    val effectiveRowHeight = if (!gestureActive) {
        zoomLevel.cellMinSizeDp.dp
    } else {
        lerp(
            zoomLevel.cellMinSizeDp.dp,
            gestureTarget.cellMinSizeDp.dp,
            gestureFraction
        )
    }

    PullToRefreshBox(
        state = pullState,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading -> TimelineSkeleton(cellMinSize = effectiveRowHeight)
                state.isEmpty -> TimelineEmptyState(onOpenUpload = onOpenUpload)
                else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val containerWidthDp = maxWidth
                    // Justify entries into rows. Re-packs whenever the user
                    // scrolls (no), changes zoom (yes), or rotates/resizes
                    // the window (yes). For 1000s of items this is O(n)
                    // and runs at ~1ms — cheap enough to keep inside a
                    // remember-keyed block.
                    val rows = remember(entries, containerWidthDp, effectiveRowHeight) {
                        packJustifiedRows(
                            entries = entries,
                            containerWidthDp = containerWidthDp.value,
                            targetRowHeightDp = effectiveRowHeight.value,
                            spacingDp = 2f
                        )
                    }

                    // Topmost visible cell drives both the sticky header
                    // overlay and the zoom-level re-anchor logic.
                    val anchorDate: LocalDate? by remember(rows) {
                        derivedStateOf {
                            val idx = gridState.firstVisibleItemIndex
                            if (rows.isEmpty()) return@derivedStateOf null
                            val end = minOf(idx + 8, rows.size - 1)
                            for (i in idx..end) {
                                val e = rows.getOrNull(i)
                                if (e is JustifiedRowEntry.Row) {
                                    val first = e.row.cells.firstOrNull() ?: continue
                                    return@derivedStateOf first.item.fileCreatedAt
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
                        val newIdx = findRowIndexForDate(rows, target, zoomLevel.grouping)
                        if (newIdx >= 0) {
                            runCatching { gridState.animateScrollToItem(newIdx) }
                        }
                    }

                    LaunchedEffect(pendingJumpDate, state.items, rows) {
                        val target = pendingJumpDate ?: return@LaunchedEffect
                        if (state.items.isEmpty()) {
                            onJumpHandled()
                            return@LaunchedEffect
                        }
                        val targetDate = target.toLocalDateTime(TimeZone.currentSystemDefault()).date
                        val index = findRowIndexForDate(rows, targetDate, zoomLevel.grouping)
                        if (index >= 0) {
                            runCatching { gridState.animateScrollToItem(index) }
                        }
                        onJumpHandled()
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
                            rows = rows,
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
                            state = gridState,
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
                            header = if (memories.isNotEmpty() && onOpenMemory != null &&
                                !state.isSelectionActive
                            ) {
                                {
                                    item(key = "memories-strip") {
                                        MemoriesStrip(
                                            memories = memories,
                                            baseUrl = apiBaseUrl,
                                            onOpenMemory = onOpenMemory
                                        )
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxSize()
                        )
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
            state.error?.let {
                com.photonne.app.ui.error.ErrorBanner(
                    error = it,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onRetry = onRefresh,
                )
            }
        }
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

/**
 * Stand-in for the grouped grid while the first page is loading. Mimics
 * the real layout — a sticky-header chip plus rows of square tiles — at
 * the current zoom level so the layout doesn't jump when assets arrive.
 */
@Composable
private fun TimelineSkeleton(cellMinSize: androidx.compose.ui.unit.Dp) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            SkeletonChip(width = 120.dp, height = 18.dp)
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = cellMinSize),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            items(SKELETON_TILE_COUNT) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = 4.dp
                )
            }
        }
    }
}

private const val SKELETON_TILE_COUNT = 36

@Composable
private fun TimelineEmptyState(onOpenUpload: (() -> Unit)?) {
    EmptyState(
        icon = Icons.Outlined.PhotoLibrary,
        title = stringResource(Res.string.timeline_empty_title),
        subtitle = stringResource(Res.string.timeline_empty_subtitle),
        actionLabel = onOpenUpload?.let { stringResource(Res.string.timeline_empty_action_upload) },
        onAction = onOpenUpload
    )
}

