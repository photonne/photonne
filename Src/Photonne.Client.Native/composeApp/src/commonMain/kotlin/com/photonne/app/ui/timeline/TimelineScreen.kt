package com.photonne.app.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.settings.TimelineGrouping
import com.photonne.app.data.settings.TimelineZoomLevel
import com.photonne.app.data.settings.TimelineZoomStore
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_device_loading
import com.photonne.app.resources.timeline_scroll_to_top
import com.photonne.app.resources.timeline_empty_action_upload
import com.photonne.app.resources.timeline_empty_subtitle
import com.photonne.app.resources.timeline_empty_title
import com.photonne.app.ui.devicebackup.DeviceBackupViewModel
import com.photonne.app.ui.grid.BucketEntriesResult
import com.photonne.app.ui.grid.GroupedAssetGrid
import com.photonne.app.ui.grid.TimelineRowEntry
import com.photonne.app.ui.grid.assetCellKey
import com.photonne.app.ui.grid.bucketKeyOf
import com.photonne.app.ui.grid.buildBucketEntries
import com.photonne.app.ui.grid.buildYearSummaryEntries
import com.photonne.app.ui.grid.contiguousRunAround
import com.photonne.app.ui.grid.expandWithNeighborBuckets
import com.photonne.app.ui.grid.findRowIndexForAsset
import com.photonne.app.ui.grid.findRowIndexForDate
import com.photonne.app.ui.grid.packUniformRows
import com.photonne.app.ui.grid.truncateRowsPerGroup
import androidx.compose.foundation.layout.aspectRatio
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.SkeletonBlock
import com.photonne.app.ui.theme.SkeletonChip
import kotlin.math.abs
import kotlin.math.ln
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /**
     * The grid reports which bucket keys ("yyyy-MM") are on or near the
     * viewport; the ViewModel loads their contents. Replaces the old
     * cursor-pagination onLoadMore.
     */
    onBucketsVisible: (List<String>) -> Unit,
    /**
     * The Year zoom level asks for per-year summaries with at least this
     * many sampled items per year (rows × columns at the current width).
     */
    onEnsureYearSummaries: (sample: Int) -> Unit = {},
    onRefresh: () -> Unit = {},
    onToggleSelection: ((assetId: String) -> Unit)? = null,
    onOpenUpload: (() -> Unit)? = null,
    pendingJumpDate: Instant? = null,
    onJumpHandled: () -> Unit = {},
    /** On-this-day memories shown as a carousel pinned above the grid. */
    memories: List<com.photonne.app.data.models.TimelineItem> = emptyList(),
    onOpenMemory: ((items: List<com.photonne.app.data.models.TimelineItem>, index: Int) -> Unit)? = null,
    onSeeAllMemories: (() -> Unit)? = null
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
    // Subtle "finding device photos" hint shown only during the first device
    // scan (no cache yet → entries still empty). Cached launches seed the
    // grid instantly and never flip isLoading, so the hint stays hidden.
    val deviceLoading = deviceBackupState.isBackupEnabled && deviceBackupState.isLoading
    val showMemoriesHeader = memories.isNotEmpty() && onOpenMemory != null &&
        !state.isSelectionActive
    // Year view renders the compressed per-year summaries (a few sampled
    // rows per year, count in the header); every other zoom level renders
    // the full bucket timeline. Both flatten into the same entries shape.
    val isYearView = zoomLevel.grouping == TimelineGrouping.Year

    // Pinch state: during a two-finger zoom the cell size lerps toward the
    // next zoom level for visual feedback, then snaps to a discrete level on
    // gesture end. Grouping stays at zoomLevel.grouping for the duration of
    // the gesture — only the cell size animates.
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

    PullToRefreshBox(
        state = pullState,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Year view needs enough sampled items per year to fill its
            // capped rows at the current width. Fires while the skeleton is
            // still up (it must — that's what resolves the skeleton) and
            // re-fires on rotation/resize; the store dedups and caches.
            val yearSample = (maxWidth.value / TimelineZoomLevel.Year.cellMinSizeDp)
                .toInt().coerceAtLeast(1) * YEAR_VIEW_MAX_ROWS
            LaunchedEffect(isYearView, yearSample) {
                if (isYearView) onEnsureYearSummaries(yearSample)
            }
            when {
                state.isInitialLoading -> TimelineSkeleton(cellMinSize = effectiveCellMinSize)
                state.isEmpty -> TimelineEmptyState(onOpenUpload = onOpenUpload)
                // Year view before its summaries arrive: full-screen skeleton
                // (the ensure effect below fires the fetch).
                isYearView && state.yearSummaries == null ->
                    TimelineSkeleton(cellMinSize = effectiveCellMinSize)
                else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val containerWidthDp = maxWidth
                    // The whole derivation pipeline — flattening buckets into
                    // entries, packing them into uniform rows, and the
                    // key→bucket map — runs OFF the main thread. With the whole
                    // library laid out up front this touches tens of thousands
                    // of items per bucket arrival; doing it in a remember{}
                    // (i.e. during composition) was what hitched the scrubber
                    // whenever data landed. The PREVIOUS frame's rows stay on
                    // screen while the new ones compute, so the UI thread never
                    // waits; restarts cancel superseded computations (mapLatest
                    // semantics).
                    var packed by remember { mutableStateOf<PackedTimeline?>(null) }
                    LaunchedEffect(
                        state.buckets, state.yearSummaries,
                        localItems, zoomLevel, containerWidthDp, effectiveCellMinSize
                    ) {
                        packed = withContext(Dispatchers.Default) {
                            derivePackedTimeline(
                                state = state,
                                localItems = localItems,
                                grouping = zoomLevel.grouping,
                                containerWidthDp = containerWidthDp.value,
                                minCellSizeDp = effectiveCellMinSize.value
                            )
                        }
                    }
                    // While the scrubber is dragged the grid renders in
                    // lightweight mode: dominant-colour cells, no thumbnail
                    // requests. Thumbnails come back the instant the finger
                    // lifts.
                    var isScrubbing by remember { mutableStateOf(false) }

                    val current = packed
                    if (current == null) {
                        // Only the very first derivation (cold entry into the
                        // grid) has nothing previous to show.
                        TimelineSkeleton(cellMinSize = effectiveCellMinSize)
                        return@BoxWithConstraints
                    }
                    val bucketEntries = current.entries
                    val mergedItems = bucketEntries.mergedItems
                    val rows = current.rows
                    val keyToBucket = current.keyToBucket

                    // Drives bucket loading: visible months (± one neighbour
                    // per side) get their contents fetched. The store dedups
                    // and caches, so emitting on every scroll frame is fine.
                    // Year view opts out — its summaries are self-contained
                    // and loading month buckets for sampled cells would be
                    // wasted requests.
                    LaunchedEffect(gridState, keyToBucket, isYearView) {
                        if (isYearView) return@LaunchedEffect
                        snapshotFlow {
                            gridState.layoutInfo.visibleItemsInfo
                                .mapNotNull { keyToBucket[it.key] }
                                .distinct()
                        }
                            .distinctUntilChanged()
                            .collectLatest { visible ->
                                if (visible.isEmpty()) return@collectLatest
                                // Settle debounce: while a fling or the
                                // scrubber is flying through months, the
                                // collectLatest restart cancels the delay, so
                                // intermediate buckets are never requested —
                                // they stay as gray skeletons. Only where the
                                // scroll settles do we spend requests (and
                                // each arrival re-packs the whole timeline,
                                // so flooding the queue is what caused the
                                // mid-scrub jank).
                                delay(SETTLE_DEBOUNCE_MS)
                                onBucketsVisible(
                                    expandWithNeighborBuckets(visible, bucketEntries.bucketOrder)
                                )
                            }
                    }

                    // Topmost visible cell drives both the sticky header
                    // overlay and the zoom-level re-anchor logic. Skeleton
                    // rows anchor to the first day of their month.
                    val anchorDate: LocalDate? by remember(rows) {
                        derivedStateOf {
                            val idx = gridState.firstVisibleItemIndex
                            if (rows.isEmpty()) return@derivedStateOf null
                            val end = minOf(idx + 8, rows.size - 1)
                            for (i in idx..end) {
                                when (val e = rows.getOrNull(i)) {
                                    is TimelineRowEntry.Row -> {
                                        val first = e.row.cells.firstOrNull() ?: continue
                                        return@derivedStateOf first.item.fileCreatedAt
                                            .toLocalDateTime(TimeZone.currentSystemDefault()).date
                                    }
                                    is TimelineRowEntry.SkeletonRow -> {
                                        val year = e.bucketKey.substringBefore('-').toIntOrNull()
                                            ?: continue
                                        val month = e.bucketKey.substringAfter('-').toIntOrNull()
                                            ?: continue
                                        return@derivedStateOf LocalDate(year, month, 1)
                                    }
                                    else -> Unit
                                }
                            }
                            null
                        }
                    }

                    // Set by the Year-view click-to-zoom; consumed by the
                    // zoom re-anchor effect below.
                    var pendingZoomAnchor by remember { mutableStateOf<LocalDate?>(null) }
                    // Second, finer stage of the same click: once the asset's
                    // bucket content is in the rows, scroll to the asset's
                    // exact row (the month header is just the coarse anchor
                    // while the bucket loads).
                    var pendingAssetAnchor by remember {
                        mutableStateOf<PendingAssetAnchor?>(null)
                    }
                    val density = LocalDensity.current

                    var isFirstZoomComposition by remember { mutableStateOf(true) }
                    LaunchedEffect(zoomLevel) {
                        if (isFirstZoomComposition) {
                            isFirstZoomComposition = false
                            return@LaunchedEffect
                        }
                        // A click-to-zoom (Year → Month) wants to land on the
                        // clicked month, not on whatever was topmost. A manual
                        // zoom change (pinch/menu) invalidates any pending
                        // asset jump from an earlier click.
                        if (pendingZoomAnchor == null) pendingAssetAnchor = null
                        val target = pendingZoomAnchor ?: anchorDate ?: return@LaunchedEffect
                        pendingZoomAnchor = null
                        val newIdx = findRowIndexForDate(rows, target, zoomLevel.grouping)
                        if (newIdx >= 0) {
                            runCatching { gridState.animateScrollToItem(newIdx) }
                        }
                    }

                    LaunchedEffect(rows, pendingAssetAnchor) {
                        val anchor = pendingAssetAnchor ?: return@LaunchedEffect
                        if (isYearView) return@LaunchedEffect
                        val idx = findRowIndexForAsset(rows, anchor.assetId)
                        if (idx >= 0) {
                            // Land the row just below the pinned sticky header.
                            val headerPx = with(density) { 56.dp.roundToPx() }
                            runCatching { gridState.animateScrollToItem(idx, -headerPx) }
                            pendingAssetAnchor = null
                        } else {
                            // Bucket loaded but the asset isn't there (gone
                            // server-side) — stay at the month header.
                            val bucket = state.buckets.firstOrNull { it.key == anchor.bucketKey }
                            if (bucket == null || bucket.isLoaded) pendingAssetAnchor = null
                        }
                    }

                    LaunchedEffect(pendingJumpDate, rows) {
                        val target = pendingJumpDate ?: return@LaunchedEffect
                        if (rows.isEmpty()) {
                            // Still waiting for the skeleton — the effect
                            // re-fires when rows arrive. Only a truly empty
                            // timeline swallows the jump.
                            if (!state.isInitialLoading) onJumpHandled()
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
                        // Year view is navigation, not detail (Apple-Photos
                        // semantics): clicking dives into the month — and,
                        // when a concrete asset was clicked, onto that asset
                        // as soon as its bucket content is available. 35dp
                        // cells were a misclick-sized target for "open
                        // detail" anyway.
                        fun zoomIntoMonth(anchor: LocalDate, asset: PendingAssetAnchor? = null) {
                            pendingZoomAnchor = anchor
                            pendingAssetAnchor = asset
                            zoomStore.update(TimelineZoomLevel.Month)
                        }
                        GroupedAssetGrid(
                            rows = rows,
                            baseUrl = apiBaseUrl,
                            onItemClick = { mergedIndex ->
                                val item = mergedItems.getOrNull(mergedIndex)
                                    ?: return@GroupedAssetGrid
                                val toggler = onToggleSelection
                                when {
                                    isYearView -> zoomIntoMonth(
                                        anchor = item.fileCreatedAt
                                            .toLocalDateTime(TimeZone.currentSystemDefault())
                                            .date,
                                        asset = PendingAssetAnchor(
                                            assetId = item.id,
                                            bucketKey = bucketKeyOf(item.fileCreatedAt)
                                        )
                                    )
                                    state.isSelectionActive && !item.isLocalOnly &&
                                        toggler != null ->
                                        // Tap-to-multi-select only applies to
                                        // server assets — local-only items
                                        // aren't part of the timeline's bulk
                                        // operations.
                                        toggler(item.id)
                                    else -> {
                                        // The pager gets the contiguous loaded
                                        // run around the click, so swiping never
                                        // silently skips an unloaded month.
                                        val run = contiguousRunAround(mergedIndex, bucketEntries)
                                        if (run != null) {
                                            val (runItems, runStart) = run
                                            onOpenAsset(runItems, mergedIndex - runStart)
                                        }
                                    }
                                }
                            },
                            state = gridState,
                            selectedIds = state.selection,
                            // No long-press selection in Year view: with
                            // click = navigate, selecting over a sea of
                            // skeletons would be ambiguous about what's
                            // actually selected.
                            onItemLongClick = onToggleSelection?.takeIf { !isYearView }
                                ?.let { toggler ->
                                    { mergedIndex ->
                                        val item = mergedItems.getOrNull(mergedIndex)
                                        if (item != null && !item.isLocalOnly) {
                                            toggler(item.id)
                                        }
                                    }
                                },
                            onSkeletonClick = { bucketKey ->
                                if (isYearView) {
                                    val year = bucketKey.substringBefore('-').toIntOrNull()
                                    val month = bucketKey.substringAfter('-').toIntOrNull()
                                    if (year != null && month != null) {
                                        zoomIntoMonth(LocalDate(year, month, 1))
                                    }
                                }
                            },
                            suppressThumbnails = isScrubbing,
                            header = if (showMemoriesHeader) {
                                {
                                    item(key = "memories-strip") {
                                        MemoriesStrip(
                                            memories = memories,
                                            baseUrl = apiBaseUrl,
                                            onOpenMemory = onOpenMemory!!,
                                            onSeeAll = onSeeAllMemories
                                        )
                                    }
                                    // With memories present the floating pill
                                    // would overlap the strip, so the scan hint
                                    // sits inline between memories and the grid.
                                    if (deviceLoading && state.error == null) {
                                        item(key = "device-scan-indicator") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                DeviceScanIndicator()
                                            }
                                        }
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    TimelineScrubber(
                        gridState = gridState,
                        rows = rows,
                        headerItemCount = if (showMemoriesHeader) {
                            1 + (if (deviceLoading && state.error == null) 1 else 0)
                        } else 0,
                        onDraggingChange = { dragging -> isScrubbing = dragging },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )

                    // Bottom-center so it never collides with the scrubber
                    // handle riding the right edge.
                    ScrollToTopButton(
                        gridState = gridState,
                        suppressed = isScrubbing || state.isSelectionActive,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }
            state.error?.let {
                com.photonne.app.ui.error.ErrorBanner(
                    error = it,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onRetry = onRefresh,
                )
            }
            // Floating pill only when there's no memories header to host it
            // inline (otherwise the inline header item above is used).
            if (deviceLoading && state.error == null && !showMemoriesHeader) {
                DeviceScanIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                )
            }
        }
    }
}

/** Small pill shown over the grid while the first device-gallery scan runs. */
@Composable
private fun DeviceScanIndicator(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(Res.string.timeline_device_loading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Floating "back to top" pill. Follows the scrubber's rhythm — appears
 * while the list is scrolling (once a few screens deep) and fades out
 * after a pause — so the overlay chrome comes and goes as one. Tapping
 * teleports near the top and animates the last stretch; animating the
 * whole way would compose thousands of rows for nothing.
 */
@Composable
private fun ScrollToTopButton(
    gridState: LazyListState,
    /** Hidden while scrubbing or selecting, where it would just be noise. */
    suppressed: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val pastThreshold by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > SCROLL_TO_TOP_MIN_INDEX }
    }
    val active = pastThreshold && gridState.isScrollInProgress
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(active, pastThreshold) {
        when {
            active -> visible = true
            !pastThreshold -> visible = false
            else -> {
                delay(1500)
                visible = false
            }
        }
    }
    AnimatedVisibility(
        visible = visible && !suppressed,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Surface(
            onClick = {
                scope.launch {
                    runCatching {
                        if (gridState.firstVisibleItemIndex > SCROLL_TO_TOP_SNAP_INDEX) {
                            gridState.scrollToItem(SCROLL_TO_TOP_SNAP_INDEX)
                        }
                        gridState.animateScrollToItem(0)
                    }
                }
            },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(Res.string.timeline_scroll_to_top),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Rows scrolled past before the back-to-top pill can appear (~3 screens). */
private const val SCROLL_TO_TOP_MIN_INDEX = 24

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_INDEX = 12

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

/** Max justified rows per year in the compressed Year view. */
private const val YEAR_VIEW_MAX_ROWS = 5

/** How long the viewport must hold still before its buckets are fetched. */
private const val SETTLE_DEBOUNCE_MS = 180L

/**
 * The asset half of a Year-view click: which asset to land on (and which
 * bucket carries it, for the give-up check) once the Month view has the
 * bucket's content in its rows.
 */
private data class PendingAssetAnchor(val assetId: String, val bucketKey: String)

/**
 * Snapshot of one full timeline derivation: flattened entries, uniform rows
 * and the LazyColumn key → bucket map. Produced as a unit off the main
 * thread so the grid swaps atomically from one consistent frame to the next.
 */
private data class PackedTimeline(
    val entries: BucketEntriesResult,
    val rows: List<TimelineRowEntry>,
    val keyToBucket: Map<Any, String>
)

/** The CPU-heavy derivation pipeline — always called on a background dispatcher. */
private fun derivePackedTimeline(
    state: TimelineUiState,
    localItems: List<com.photonne.app.data.models.TimelineItem>,
    grouping: TimelineGrouping,
    containerWidthDp: Float,
    minCellSizeDp: Float
): PackedTimeline {
    val isYear = grouping == TimelineGrouping.Year
    val entries = if (isYear) {
        buildYearSummaryEntries(state.yearSummaries.orEmpty())
    } else {
        buildBucketEntries(
            buckets = state.buckets,
            localItems = localItems,
            grouping = grouping
        )
    }
    var rows = packUniformRows(
        entries = entries.entries,
        containerWidthDp = containerWidthDp,
        minCellSizeDp = minCellSizeDp,
        spacingDp = 2f
    )
    // Year view: cap every year to a fixed number of rows — the header
    // count says how much more there is, and clicking dives into the month.
    if (isYear) rows = truncateRowsPerGroup(rows, YEAR_VIEW_MAX_ROWS)

    val keyToBucket = buildMap {
        rows.forEach { entry ->
            when (entry) {
                is TimelineRowEntry.Row -> {
                    val first = entry.row.cells.firstOrNull()
                    if (first != null) {
                        put(
                            "r:${assetCellKey(first.item, first.index)}" as Any,
                            bucketKeyOf(first.item.fileCreatedAt)
                        )
                    }
                }
                is TimelineRowEntry.SkeletonRow ->
                    put("s:${entry.bucketKey}:${entry.rowIndex}", entry.bucketKey)
                is TimelineRowEntry.Header -> Unit
            }
        }
    }
    return PackedTimeline(entries = entries, rows = rows, keyToBucket = keyToBucket)
}

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

