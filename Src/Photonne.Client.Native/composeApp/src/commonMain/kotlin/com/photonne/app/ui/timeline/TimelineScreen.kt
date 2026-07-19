package com.photonne.app.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.settings.TimelineGrouping
import com.photonne.app.data.settings.TimelineZoomLevel
import com.photonne.app.data.settings.TimelineZoomStore
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_empty_action_upload
import com.photonne.app.resources.timeline_empty_subtitle
import com.photonne.app.resources.timeline_empty_title
import com.photonne.app.ui.devicebackup.DeviceBackupViewModel
import com.photonne.app.ui.grid.BucketEntriesResult
import com.photonne.app.ui.grid.GroupedAssetGrid
import com.photonne.app.ui.grid.TimelineRowEntry
import com.photonne.app.ui.main.chromeCapsuleBackdrop
import com.photonne.app.ui.main.FloatingDatePill
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.ScrollToTopPill
import com.photonne.app.ui.main.TimelineTopBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
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
    /** Only the empty state offers this — uploading otherwise lives in Más. */
    onOpenUpload: (() -> Unit)? = null,
    /** Opens the "jump to date" picker from the floating top bar. */
    onJumpToDate: () -> Unit = {},
    /** Opens global search from the floating top bar. */
    onOpenSearch: () -> Unit = {},
    /**
     * Reports the immersive-chrome visibility (hidden while scrolling down) so
     * the host can slide the shared bottom navigation in the same rhythm.
     */
    onChromeVisibleChange: (Boolean) -> Unit = {},
    pendingJumpDate: Instant? = null,
    onJumpHandled: () -> Unit = {},
    /** On-this-day memories shown as a carousel pinned above the grid. */
    memories: List<com.photonne.app.data.models.TimelineItem> = emptyList(),
    onOpenMemory: ((memory: com.photonne.app.ui.memories.MemoryDetailContext) -> Unit)? = null,
    onSeeAllMemories: (() -> Unit)? = null
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val pullState = rememberPullToRefreshState()
    val gridState = rememberLazyListState()
    // Fuente de blur de TODO el cromo del timeline (píldora superior, scrubber,
    // botón de subir). Su fuente es SOLO la rejilla (ver `hazeSource` más abajo),
    // así que las cápsulas quedan como HERMANAS de ella y no descendientes — la
    // regla de Haze. No se reusa el estado global de MainScaffold porque la
    // píldora vive dentro del contenido y colgaría de su propia fuente.
    val gridHazeState = remember { HazeState() }

    // Immersive chrome (floating top pill + bottom nav). It hides only while
    // the user is actively scrolling DOWN, and comes back the moment they
    // scroll up or the list settles — matching "disappears on scroll, returns
    // when it stops". At the very top the bar is docked (full, with wordmark).
    val atTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 &&
                gridState.firstVisibleItemScrollOffset == 0
        }
    }
    // rememberSaveable so the pager keeps the chrome's shown/hidden state when
    // this page scrolls off and back — otherwise it reset to `true` on return,
    // re-firing the fade + the onChromeVisibleChange hop (the "jump/flash").
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    // Small dead-zone so micro-scrolls and fling jitter don't flip the chrome.
    val chromeThresholdPx = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(gridState, chromeThresholdPx) {
        var prevIndex = gridState.firstVisibleItemIndex
        var prevOffset = gridState.firstVisibleItemScrollOffset
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            // Rows have variable heights, so we can't turn index into pixels.
            // Within one row compare the offset; when the first visible index
            // changes we crossed a boundary — take the index sign as direction.
            val delta = if (index != prevIndex) {
                (index - prevIndex).toFloat() * (chromeThresholdPx + 1f)
            } else {
                (offset - prevOffset).toFloat()
            }
            if (delta > chromeThresholdPx) chromeVisible = false
            else if (delta < -chromeThresholdPx) chromeVisible = true
            prevIndex = index
            prevOffset = offset
        }
    }
    // Bring the chrome back a beat after scrolling stops (and on first load).
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }.collectLatest { scrolling ->
            if (!scrolling) {
                delay(160)
                chromeVisible = true
            }
        }
    }
    // Parked at the very top always shows the (docked) bar.
    LaunchedEffect(atTop) { if (atTop) chromeVisible = true }
    // Report visibility up so the host can slide the bottom navigation in step.
    LaunchedEffect(chromeVisible) { onChromeVisibleChange(chromeVisible) }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (chromeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "timelineChromeAlpha"
    )
    // Space the docked bar occupies at the top: status bar + a standard app-bar
    // height. The grid reserves this so the memories strip clears it at rest,
    // and the lateral scrubber starts below the status-bar icons.
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val reservedTop = statusBarTop + 64.dp
    // The grid draws full-bleed behind the floating bottom nav; reserve the bar's
    // full height at the scroll end so the last row still clears it.
    val reservedBottom = floatingNavBarReservedHeight()

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
    // Whether the Recuerdos strip belongs in the timeline at all. It stays
    // mounted while selecting and instead collapses smoothly (see the
    // AnimatedVisibility below) so entering selection no longer makes the
    // whole grid jump up by the strip's height.
    val hasMemoriesHeader = memories.isNotEmpty() && onOpenMemory != null
    // Year view renders the compressed per-year summaries (a few sampled
    // rows per year, count in the header); every other zoom level renders
    // the full bucket timeline. Both flatten into the same entries shape.
    val isYearView = zoomLevel.grouping == TimelineGrouping.Year

    // The base grid is always packed at the committed level's cell size; the
    // pinch no longer morphs it (that forced a per-frame re-pack). The visible
    // zoom motion is driven by the continuous reflow layer instead — see the
    // reflow state inside the grid block below.
    val effectiveCellMinSize = zoomLevel.cellMinSizeDp.dp

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
                                            .captureLocalDate()
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
                    val scope = rememberCoroutineScope()

                    // ---- Continuous pinch-reflow state ----
                    // The base grid is frozen + hidden while the reflow layer
                    // drives the visible transition. The pinch callbacks live in
                    // a `pointerInput(Unit)` block (created once), so anything
                    // that changes across recomposition is read through these
                    // `rememberUpdatedState` mirrors rather than captured stale.
                    val rowsLatest = rememberUpdatedState(rows)
                    val widthLatest = rememberUpdatedState(containerWidthDp.value)
                    val zoomLatest = rememberUpdatedState(zoomLevel)
                    val headerCount = if (hasMemoriesHeader) 1 else 0
                    val headerCountLatest = rememberUpdatedState(headerCount)

                    var reflowActive by remember { mutableStateOf(false) }
                    // The two same-grouping levels currently cross-dissolving
                    // (indices into [ladder]); only updated when a level boundary
                    // is crossed, so per-frame zoom never recomposes.
                    var bracketLo by remember { mutableStateOf(0) }
                    var bracketHi by remember { mutableStateOf(0) }
                    // The "virtual" cell size (dp) the grid is being pinched to;
                    // read live inside the dissolve layer (no recompose).
                    val virtualCell = remember { mutableFloatStateOf(0f) }
                    // Captured at pinch start.
                    var ladder by remember { mutableStateOf<List<LadderEntry>>(emptyList()) }
                    var windowItems by remember {
                        mutableStateOf<List<com.photonne.app.data.models.TimelineItem>>(emptyList())
                    }
                    var capAnchorId by remember { mutableStateOf<String?>(null) }
                    // Anchor date: handoff fallback when the exact photo isn't in
                    // the target rows (Year shows only a sampled subset).
                    var capAnchorDate by remember { mutableStateOf<LocalDate?>(null) }
                    var anchorTopYdp by remember { mutableFloatStateOf(0f) }
                    var committedCell by remember { mutableFloatStateOf(0f) }
                    // Grouping captured at gesture start; used for the whole
                    // dissolve so headers never blink when the bracket crosses
                    // into a different-grouping level.
                    var committedGrouping by remember { mutableStateOf(zoomLevel.grouping) }
                    // True when zooming out of Year: anchor on the photo under the
                    // fingers (navigational dive into that month) and transform the
                    // whole screen around it, rather than anchoring top-left.
                    var focalZoom by remember { mutableStateOf(false) }
                    // Set on commit; consumed by the handoff effect once the base
                    // grid has re-packed for the new level.
                    var pendingReflowCommit by remember { mutableStateOf<ReflowCommit?>(null) }
                    // Set true once the settle animation has reached its target,
                    // gating the handoff so the overlay is fully resolved before
                    // the live grid takes over (no mid-motion jump).
                    var reflowSettled by remember { mutableStateOf(false) }

                    var isFirstZoomComposition by remember { mutableStateOf(true) }
                    // Stage 1 — CAPTURE where to re-anchor the instant the zoom
                    // level flips. A click-to-zoom (Year → Month) has already
                    // set pendingZoomAnchor to the clicked month and
                    // pendingAssetAnchor to the exact asset, so we leave those
                    // alone. A manual zoom (pinch/menu) instead re-anchors on
                    // whatever is topmost right now and discards any stale asset
                    // jump from an earlier click. We only record the target
                    // here; the actual scroll happens in stage 2 once the rows
                    // have been repacked for the new grouping — at this instant
                    // `rows` still holds the OLD grouping (packing is async).
                    LaunchedEffect(zoomLevel) {
                        if (isFirstZoomComposition) {
                            isFirstZoomComposition = false
                            return@LaunchedEffect
                        }
                        // A reflow commit anchors the grid itself (precisely) via
                        // the handoff effect — skip the coarse generic re-anchor
                        // so the two don't fight over gridState.
                        if (pendingReflowCommit != null) return@LaunchedEffect
                        if (pendingZoomAnchor == null) {
                            pendingAssetAnchor = null
                            pendingZoomAnchor = anchorDate
                        }
                    }

                    // Stage 2 — APPLY the pending re-anchor, but only against
                    // rows that match the active grouping. Re-anchoring against
                    // stale rows is exactly what made a view switch jump to a
                    // random year/month and what stopped a Year-view asset
                    // click from landing where it should. Coarse (month/date)
                    // and fine (exact asset) anchoring live in one effect so
                    // they never fight over gridState.
                    LaunchedEffect(current, pendingZoomAnchor, pendingAssetAnchor) {
                        if (pendingZoomAnchor == null && pendingAssetAnchor == null) {
                            return@LaunchedEffect
                        }
                        // Wait for the repack to catch up with the zoom level.
                        if (current.grouping != zoomLevel.grouping) return@LaunchedEffect

                        // Prefer landing exactly on a clicked asset once its
                        // bucket content is in the rows; until then fall through
                        // to the coarse month anchor so we park on the right
                        // month while it hydrates.
                        val asset = pendingAssetAnchor
                        if (asset != null && !isYearView) {
                            val idx = findRowIndexForAsset(rows, asset.assetId)
                            if (idx >= 0) {
                                // Land the row just below its (non-sticky) month
                                // header so the date shows above it.
                                val headerPx = with(density) { 56.dp.roundToPx() }
                                runCatching { gridState.animateScrollToItem(idx, -headerPx) }
                                pendingAssetAnchor = null
                                pendingZoomAnchor = null
                                return@LaunchedEffect
                            }
                            // Not in the rows yet. Drop the asset target only if
                            // its bucket is absent or already loaded (asset gone
                            // server-side); if it's still loading, keep it and
                            // let the coarse anchor park us meanwhile.
                            val bucket = state.buckets.firstOrNull { it.key == asset.bucketKey }
                            if (bucket == null || bucket.isLoaded) pendingAssetAnchor = null
                        }

                        val target = pendingZoomAnchor ?: return@LaunchedEffect
                        val newIdx = findRowIndexForDate(rows, target, zoomLevel.grouping)
                        if (newIdx >= 0) {
                            // Snap (don't animate) across a wholesale layout
                            // swap; the exact-asset stage above is the only
                            // place that adds visible motion.
                            runCatching { gridState.scrollToItem(newIdx) }
                        }
                        pendingZoomAnchor = null
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
                        val targetDate = target.captureLocalDate()
                        val index = findRowIndexForDate(rows, targetDate, zoomLevel.grouping)
                        if (index >= 0) {
                            runCatching { gridState.animateScrollToItem(index) }
                        }
                        onJumpHandled()
                    }

                    // Reflow handoff: once the base grid has re-packed for the
                    // committed level, scroll it so the anchor (top-left) photo
                    // sits exactly where the dissolve layer left it, then drop the
                    // overlay — making the swap to the live grid seamless.
                    LaunchedEffect(pendingReflowCommit, current, reflowSettled) {
                        val pc = pendingReflowCommit ?: return@LaunchedEffect
                        if (!reflowSettled) return@LaunchedEffect
                        if (current.grouping != pc.toLevel.grouping) return@LaunchedEffect
                        if (current.packedCellMinDp != pc.toLevel.cellMinSizeDp.toFloat()) {
                            return@LaunchedEffect
                        }
                        val anchorId = pc.anchorId
                        if (anchorId != null) {
                            // Prefer the exact photo; fall back to the date's group
                            // when it isn't present (Year only keeps a sample).
                            var idx = findRowIndexForAsset(current.rows, anchorId)
                            if (idx < 0 && pc.anchorDate != null) {
                                idx = findRowIndexForDate(
                                    current.rows, pc.anchorDate, pc.toLevel.grouping
                                )
                            }
                            if (idx >= 0) {
                                // Offset stays signed: when the anchor row sits below
                                // the top (Recuerdos above it) the offset is negative
                                // and LazyColumn fills the space above with it.
                                val off = with(density) { (-pc.anchorTopYdp).dp.roundToPx() }
                                runCatching {
                                    gridState.scrollToItem(headerCount + idx, off)
                                }
                            }
                        }
                        withFrameNanos {}
                        withFrameNanos {}
                        pendingReflowCommit = null
                        reflowSettled = false
                        reflowActive = false
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTimelinePinch(
                                    onZoomStart = { centroid ->
                                        // Capture a forward window of real items.
                                        // The dissolve spans every level in one
                                        // gesture (Year ↔ Month ↔ Day S/M/L), using
                                        // the committed view's on-screen photos +
                                        // grouping throughout (Year's real sampled
                                        // grid takes over on release).
                                        val rws = rowsLatest.value
                                        val hc = headerCountLatest.value
                                        val width = widthLatest.value
                                        val zl = zoomLatest.value
                                        committedCell = levelNaturalCell(
                                            width, zl.cellMinSizeDp.toFloat()
                                        )
                                        committedGrouping = zl.grouping
                                        // In Year view, zooming in is navigational:
                                        // anchor on the photo UNDER THE FINGERS so we
                                        // dive into that month. Every other level
                                        // anchors top-left (current scroll stays put).
                                        val focal = zl.grouping == TimelineGrouping.Year
                                        val vis = gridState.layoutInfo.visibleItemsInfo
                                        val chosen = if (focal) {
                                            vis.lastOrNull { vi ->
                                                val e = rws.getOrNull(vi.index - hc)
                                                e is TimelineRowEntry.Row &&
                                                    centroid.y >= vi.offset &&
                                                    centroid.y < vi.offset + vi.size
                                            } ?: vis.firstOrNull {
                                                rws.getOrNull(it.index - hc) is TimelineRowEntry.Row
                                            }
                                        } else {
                                            vis.firstOrNull {
                                                rws.getOrNull(it.index - hc) is TimelineRowEntry.Row
                                            }
                                        }
                                        val allLevels = TimelineZoomLevel.entries
                                        if (chosen != null && allLevels.size >= 2) {
                                            val ri = chosen.index - hc
                                            val offsetPx = chosen.offset
                                            val cells = (rws[ri] as TimelineRowEntry.Row).row.cells
                                            val anchorCol = if (focal) {
                                                val cellPx = with(density) { committedCell.dp.toPx() }
                                                val spacingPx = with(density) { 2f.dp.toPx() }
                                                (centroid.x / (cellPx + spacingPx)).toInt()
                                                    .coerceIn(0, (cells.size - 1).coerceAtLeast(0))
                                            } else 0
                                            val anchorItem = (cells.getOrNull(anchorCol)
                                                ?: cells.firstOrNull())?.item
                                            capAnchorId = anchorItem?.id
                                            capAnchorDate = anchorItem?.fileCreatedAt
                                                ?.captureLocalDate()
                                            anchorTopYdp = with(density) { offsetPx.toDp().value }
                                            // Warm Year's sampled summaries so the
                                            // landing on Year (if the gesture ends
                                            // there) has data ready instead of a
                                            // skeleton.
                                            onEnsureYearSummaries(
                                                (width / TimelineZoomLevel.Year.cellMinSizeDp)
                                                    .toInt().coerceAtLeast(1) * YEAR_VIEW_MAX_ROWS
                                            )
                                            // Focal (Year) zoom dives into the month
                                            // under the fingers — preload that month's
                                            // bucket now so its photos are ready when
                                            // we land, instead of loading on arrival.
                                            if (focal) {
                                                anchorItem?.fileCreatedAt?.let {
                                                    onBucketsVisible(listOf(bucketKeyOf(it)))
                                                }
                                            }
                                            focalZoom = focal
                                            // Focal zoom (Year) collects items both
                                            // above and below the anchor so the whole
                                            // screen transforms around it; top-left
                                            // anchoring only needs items forward.
                                            val start = if (focal) {
                                                windowStartIdx(rws, ri, REFLOW_BACK_CELLS)
                                            } else ri
                                            val end = windowEndIdx(rws, ri, REFLOW_FWD_CELLS)
                                            val items = ArrayList<com.photonne.app.data.models.TimelineItem>()
                                            for (k in start until end) {
                                                val e = rws[k]
                                                if (e is TimelineRowEntry.Row) {
                                                    e.row.cells.forEach { items += it.item }
                                                }
                                            }
                                            windowItems = items
                                            ladder = allLevels
                                                .map { lvl ->
                                                    LadderEntry(
                                                        lvl,
                                                        levelNaturalCell(width, lvl.cellMinSizeDp.toFloat())
                                                    )
                                                }
                                                .sortedBy { it.naturalCell }
                                            virtualCell.floatValue = committedCell
                                            val (lo, hi) = bracketIndices(ladder, committedCell)
                                            bracketLo = lo
                                            bracketHi = hi
                                            reflowActive = items.isNotEmpty() &&
                                                capAnchorId != null && ladder.size >= 2
                                        } else {
                                            reflowActive = false
                                            ladder = emptyList()
                                        }
                                    },
                                    onZoom = { zoom, _ ->
                                        if (!reflowActive) return@detectTimelinePinch
                                        val l = ladder
                                        if (l.isEmpty()) return@detectTimelinePinch
                                        val minC = l.first().naturalCell
                                        val maxC = l.last().naturalCell
                                        val vc = (committedCell * zoom).coerceIn(minC, maxC)
                                        virtualCell.floatValue = vc
                                        val (lo, hi) = bracketIndices(l, vc)
                                        if (lo != bracketLo) bracketLo = lo
                                        if (hi != bracketHi) bracketHi = hi
                                    },
                                    onZoomEnd = { finalZoom ->
                                        val zl = zoomLatest.value
                                        if (!reflowActive) {
                                            // No dissolve was shown (cross-grouping
                                            // or no neighbours): instant one-step
                                            // commit past the half-way threshold.
                                            val dir = if (finalZoom > 1f) 1 else -1
                                            val all = TimelineZoomLevel.entries
                                            val target = all.getOrNull(zl.ordinal + dir) ?: zl
                                            if (kotlin.math.abs(kotlin.math.ln(finalZoom)) >
                                                kotlin.math.ln(1.5f) * 0.5f && target != zl
                                            ) {
                                                zoomStore.update(target)
                                            }
                                            return@detectTimelinePinch
                                        }
                                        val l = ladder
                                        if (l.isEmpty()) { reflowActive = false; return@detectTimelinePinch }
                                        val vc = virtualCell.floatValue
                                        val lo = bracketLo.coerceIn(0, l.size - 1)
                                        val hi = bracketHi.coerceIn(0, l.size - 1)
                                        val loC = l[lo].naturalCell
                                        val hiC = l[hi].naturalCell
                                        val f = if (hiC > loC)
                                            ((vc - loC) / (hiC - loC)).coerceIn(0f, 1f) else 0f
                                        val targetIdx = if (f > 0.5f) hi else lo
                                        val targetEntry = l[targetIdx]
                                        val targetLevel = targetEntry.level
                                        val targetCell = targetEntry.naturalCell
                                        if (targetLevel == zl) {
                                            // Clamped at a ladder end. If the user
                                            // keeps pinching toward an out-of-ladder
                                            // neighbour (e.g. Month → Year, which is
                                            // sampled and has no dissolve yet),
                                            // commit to it instantly past a firmer
                                            // threshold; otherwise spring back.
                                            val dir = if (finalZoom > 1f) 1 else -1
                                            val beyond = TimelineZoomLevel.entries
                                                .getOrNull(zl.ordinal + dir)
                                            val strong = kotlin.math.abs(kotlin.math.ln(finalZoom)) >
                                                kotlin.math.ln(1.5f) * 0.75f
                                            // Only instant-jump to a level the
                                            // dissolve doesn't cover (Year).
                                            if (beyond != null && strong &&
                                                ladder.none { it.level == beyond }
                                            ) {
                                                reflowActive = false
                                                virtualCell.floatValue = committedCell
                                                zoomStore.update(beyond)
                                            } else {
                                                scope.launch {
                                                    animateReflow(
                                                        virtualCell.floatValue, committedCell
                                                    ) { virtualCell.floatValue = it }
                                                    reflowActive = false
                                                }
                                            }
                                        } else {
                                            reflowSettled = false
                                            pendingReflowCommit = ReflowCommit(
                                                anchorId = capAnchorId,
                                                anchorDate = capAnchorDate,
                                                anchorTopYdp = anchorTopYdp,
                                                toLevel = targetLevel
                                            )
                                            zoomStore.update(targetLevel)
                                            scope.launch {
                                                animateReflow(
                                                    virtualCell.floatValue, targetCell
                                                ) { virtualCell.floatValue = it }
                                                reflowSettled = true
                                            }
                                        }
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
                                            .captureLocalDate(),
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
                            // Reserve the docked bar's height at rest so the
                            // memories strip clears it; the padding scrolls away
                            // so photos still bleed under the bar. Also reserve
                            // the bottom nav's height at the scroll end (the grid
                            // bleeds behind it otherwise). Selection mode uses the
                            // solid Scaffold bars, so no reserve.
                            contentPadding = if (state.isSelectionActive) {
                                PaddingValues(0.dp)
                            } else {
                                PaddingValues(top = reservedTop, bottom = reservedBottom)
                            },
                            header = if (hasMemoriesHeader) {
                                {
                                    item(key = "memories-strip") {
                                        // Collapse (not unmount) during selection so
                                        // the grid eases up instead of snapping.
                                        AnimatedVisibility(
                                            visible = !state.isSelectionActive,
                                            enter = expandVertically() + fadeIn(),
                                            exit = shrinkVertically() + fadeOut()
                                        ) {
                                            MemoriesStrip(
                                                memories = memories,
                                                baseUrl = apiBaseUrl,
                                                onOpenMemory = onOpenMemory!!,
                                                onSeeAll = onSeeAllMemories
                                            )
                                        }
                                    }
                                }
                            } else null,
                            // Frozen while a pinch plays. NOT hidden: the dissolve
                            // overlay only paints from the anchor row down, so the
                            // base keeps showing the chrome above it (Recuerdos /
                            // pinned header) instead of it blinking out.
                            userScrollEnabled = !reflowActive,
                            // Única fuente de blur del cromo del timeline: SOLO la
                            // rejilla, no el `when` entero (el scrubber / scroll-to-
                            // top cuelgan de ahí y no deben ser la fuente).
                            modifier = Modifier.fillMaxSize().hazeSource(gridHazeState)
                        )

                        // Per-cell dissolve layer. Top-left mode (Day/Month) anchors
                        // the first visible photo and paints only below it (chrome
                        // above keeps showing). Focal mode (zoom out of Year) anchors
                        // the photo under the fingers and transforms the whole screen
                        // around it. Rebuilt only when the bracket pair changes.
                        if (reflowActive && ladder.size >= 2) {
                            val lo = bracketLo.coerceIn(0, ladder.size - 1)
                            val hi = bracketHi.coerceIn(0, ladder.size - 1)
                            val plan = remember(
                                lo, hi, windowItems, containerWidthDp, committedGrouping, capAnchorId
                            ) {
                                buildDissolvePlan(
                                    items = windowItems,
                                    grouping = committedGrouping,
                                    loCellMinDp = ladder[lo].level.cellMinSizeDp.toFloat(),
                                    hiCellMinDp = ladder[hi].level.cellMinSizeDp.toFloat(),
                                    widthDp = containerWidthDp.value,
                                    anchorId = capAnchorId,
                                    baseUrl = apiBaseUrl
                                )
                            }
                            if (plan != null) {
                                val surfaceColor = MaterialTheme.colorScheme.surface
                                val isFocal = focalZoom
                                val anchorTopPx = with(density) { anchorTopYdp.dp.toPx() }
                                ZoomDissolveLayer(
                                    plan = plan,
                                    virtualCell = { virtualCell.floatValue },
                                    anchorScreenYdp = anchorTopYdp,
                                    skipFirstHeader = !isFocal,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .drawBehind {
                                            // Focal: cover the whole screen (the
                                            // dissolve replaces everything). Top-left:
                                            // cover only below the anchor so the
                                            // chrome above keeps showing from the base.
                                            val top = if (isFocal) 0f
                                            else anchorTopPx.coerceAtLeast(0f)
                                            drawRect(
                                                color = surfaceColor,
                                                topLeft = Offset(0f, top),
                                                size = Size(size.width, size.height - top)
                                            )
                                        }
                                )
                            }
                        }
                    }

                    TimelineScrubber(
                        gridState = gridState,
                        rows = rows,
                        headerItemCount = if (hasMemoriesHeader) 1 else 0,
                        onDraggingChange = { dragging -> isScrubbing = dragging },
                        hazeState = gridHazeState,
                        // Start the track below the top chrome (status bar +
                        // docked bar / floating action pill), which also hugs the
                        // top-end corner. Otherwise the handle rides up behind the
                        // pill and the two overlap; now the handle lands just under it.
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = reservedTop + 8.dp)
                    )

                    // Mes de lo que hay arriba, flotando centrado en la parte
                    // superior (como "Volver arriba" pero arriba). Solo en scroll
                    // normal: al arrastrar el scrubber la fecha vuelve al mango.
                    val rowLabels = remember(rows) { scrubberRowLabels(rows) }
                    val hc = if (hasMemoriesHeader) 1 else 0
                    val topDateLabel by remember(rowLabels, hc) {
                        derivedStateOf {
                            rowLabels.getOrNull(
                                (gridState.firstVisibleItemIndex - hc).coerceAtLeast(0)
                            ).orEmpty()
                        }
                    }
                    FloatingDatePill(
                        label = topDateLabel,
                        isScrollInProgress = { gridState.isScrollInProgress },
                        suppressed = isScrubbing || state.isSelectionActive,
                        hazeState = gridHazeState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = reservedTop + 8.dp)
                    )

                    // Bottom-center so it never collides with the scrubber
                    // handle riding the right edge.
                    ScrollToTopPill(
                        firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                        isScrollInProgress = { gridState.isScrollInProgress },
                        minIndex = SCROLL_TO_TOP_MIN_INDEX,
                        onScrollToTop = {
                            if (gridState.firstVisibleItemIndex > SCROLL_TO_TOP_SNAP_INDEX) {
                                gridState.scrollToItem(SCROLL_TO_TOP_SNAP_INDEX)
                            }
                            gridState.animateScrollToItem(0)
                        },
                        suppressed = isScrubbing || state.isSelectionActive,
                        hazeState = gridHazeState,
                        // Float above the bottom nav / system buttons rather than
                        // over them (the grid is edge-to-edge at the bottom now).
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = reservedBottom + 8.dp)
                    )
                }
            }
            // Immersive top chrome (skipped entirely during selection, where the
            // solid AssetSelectionTopBar takes over via the Scaffold slot).
            if (!state.isSelectionActive) {
                // Persistent status-bar scrim so the phone's clock/indicators
                // stay legible over photos once the docked bar has scrolled off.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(statusBarTop + 16.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                if (chromeAlpha > 0.01f) {
                    // One bar for both states — docked wordmark at the very top,
                    // translucent pill once scrolled — so the action icons stay
                    // put across the swap. Skipped when faded out so it can't eat
                    // taps meant for the photos underneath.
                    TimelineTopBar(
                        atTop = atTop,
                        onJumpToDate = onJumpToDate,
                        currentZoom = zoomLevel,
                        onZoomSelected = zoomStore::update,
                        onOpenSearch = onOpenSearch,
                        deviceLoading = deviceBackupState.isBackupEnabled &&
                            deviceBackupState.isLoading,
                        hazeState = gridHazeState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer { alpha = chromeAlpha }
                    )
                }
            }
            state.error?.let {
                com.photonne.app.ui.error.ErrorBanner(
                    error = it,
                    // Clear the status-bar icons — the grid is edge-to-edge here.
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBars),
                    onRetry = onRefresh,
                )
            }
        }
    }
}

/** Rows scrolled past before the back-to-top pill can appear — kept low so it
 * shows almost as soon as the first rows leave the top of the screen. */
private const val SCROLL_TO_TOP_MIN_INDEX = 3

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_INDEX = 12

/** How many cells forward the dissolve window covers from the top-left anchor.
 * Enough to fill the screen at the densest level plus a margin; larger means a
 * heavier one-time compose when the gesture starts. */
private const val REFLOW_FWD_CELLS = 96

/** Cells above the anchor to include for a focal (Year) zoom. */
private const val REFLOW_BACK_CELLS = 60

/** Settle duration after the finger lifts (commit or cancel), ms. */
private const val REFLOW_SETTLE_MS = 200

/** One zoom level paired with its natural cell size (dp) at the current width. */
private data class LadderEntry(
    val level: TimelineZoomLevel,
    val naturalCell: Float
)

/**
 * Pending zoom commit: where to land the freshly re-packed base grid so the
 * handoff from the dissolve layer is seamless.
 */
private data class ReflowCommit(
    val anchorId: String?,
    /** Fallback when [anchorId] isn't in the target rows (Year is sampled). */
    val anchorDate: LocalDate?,
    /** Screen-space top Y (dp) the anchor row should keep after the swap. */
    val anchorTopYdp: Float,
    val toLevel: TimelineZoomLevel
)

/** Bracketing ladder indices (lo, hi) surrounding [cell] (by natural size). */
private fun bracketIndices(ladder: List<LadderEntry>, cell: Float): Pair<Int, Int> {
    if (ladder.isEmpty()) return 0 to 0
    if (cell <= ladder.first().naturalCell) return 0 to 0
    val last = ladder.size - 1
    if (cell >= ladder.last().naturalCell) return last to last
    for (i in 0 until last) {
        if (cell >= ladder[i].naturalCell && cell <= ladder[i + 1].naturalCell) {
            return i to (i + 1)
        }
    }
    return last to last
}

private fun rowCellCount(entry: TimelineRowEntry): Int = when (entry) {
    is TimelineRowEntry.Row -> entry.row.cells.size
    is TimelineRowEntry.SkeletonRow -> entry.cellCount
    is TimelineRowEntry.Header -> 0
}

/** Window start index: back from [from] until [cellCap] cells covered (>=0). */
private fun windowStartIdx(rows: List<TimelineRowEntry>, from: Int, cellCap: Int): Int {
    var i = from
    var cells = 0
    while (i > 0 && cells < cellCap) {
        cells += rowCellCount(rows[i - 1])
        i--
    }
    return i
}

/** Window end index (exclusive): forward from [from] until [cellCap] cells covered. */
private fun windowEndIdx(rows: List<TimelineRowEntry>, from: Int, cellCap: Int): Int {
    var i = from
    var cells = 0
    while (i < rows.size && cells < cellCap) {
        cells += rowCellCount(rows[i])
        i++
    }
    return i
}

/** Animates [virtualCell] toward [target], emitting each frame to [onFrame]. */
private suspend fun animateReflow(from: Float, target: Float, onFrame: (Float) -> Unit) {
    androidx.compose.animation.core.animate(
        initialValue = from,
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = REFLOW_SETTLE_MS,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        )
    ) { value, _ -> onFrame(value) }
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
    val keyToBucket: Map<Any, String>,
    /**
     * The grouping these rows were packed for. The packing runs async off
     * the main thread, so the moment [TimelineZoomLevel] flips this still
     * reports the OLD grouping until the new rows land — the re-anchor logic
     * gates on it so it never scrolls against stale (wrong-grouping) rows.
     */
    val grouping: TimelineGrouping,
    /**
     * Minimum cell size these rows were packed at — lets the reflow handoff
     * tell when the base grid has caught up with the committed level even for
     * same-grouping (Day S/M/L) changes, where [grouping] alone doesn't move.
     */
    val packedCellMinDp: Float
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
    return PackedTimeline(
        entries = entries,
        rows = rows,
        keyToBucket = keyToBucket,
        grouping = grouping,
        packedCellMinDp = minCellSizeDp
    )
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

