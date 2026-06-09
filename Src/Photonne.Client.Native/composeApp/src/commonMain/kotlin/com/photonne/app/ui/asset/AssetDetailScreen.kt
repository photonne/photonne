package com.photonne.app.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Iso
import androidx.compose.material.icons.outlined.ShutterSpeed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.toTimelineItem
import com.photonne.app.ui.image.AssetThumbnailImage
import com.photonne.app.ui.platform.OrientationController
import com.photonne.app.resources.Res
import com.photonne.app.resources.asset_action_archive
import com.photonne.app.resources.asset_action_edit_date
import com.photonne.app.resources.asset_action_edit_description
import com.photonne.app.resources.asset_action_faces
import com.photonne.app.resources.asset_action_more
import com.photonne.app.resources.asset_action_open_in_maps
import com.photonne.app.resources.asset_action_trash
import com.photonne.app.resources.asset_metadata_location
import com.photonne.app.resources.asset_metadata_open_map
import com.photonne.app.resources.slideshow_exit
import com.photonne.app.resources.slideshow_next
import com.photonne.app.resources.slideshow_pause
import com.photonne.app.resources.slideshow_play
import com.photonne.app.resources.slideshow_previous
import com.photonne.app.resources.slideshow_start
import com.photonne.app.ui.theme.LocalSharedTransitionScope
import com.photonne.app.ui.util.openExternalUrl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val PAGER_PREFETCH_THRESHOLD = 8
private const val PAGER_DISABLE_THRESHOLD = 1.05f
private val PAGER_PAGE_SPACING = 16.dp
// In portrait the bottom chrome floats slightly OVER the asset (covering a few
// of its pixels) instead of butting up against it edge-to-edge.
private val STRIP_PORTRAIT_OVERLAP = 8.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AssetDetailScreen(
    items: List<TimelineItem>,
    startIndex: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onFavoriteChanged: (assetId: String, isFavorite: Boolean) -> Unit,
    onAddToAlbum: (TimelineItem) -> Unit = {},
    onAssetTrashed: (assetId: String) -> Unit = {},
    onAssetArchived: (assetId: String) -> Unit = {},
    onOpenFaces: (assetId: String) -> Unit = {},
    onPageChanged: (assetId: String) -> Unit = {},
    onOpenAsset: (TimelineItem) -> Unit = {},
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val viewModel: AssetDetailViewModel = koinViewModel()
    val apiBaseUrl = rememberApiBaseUrl()
    val tokenStorage: TokenStorage = koinInject()
    val state by viewModel.state.collectAsState()
    val details by viewModel.details.collectAsState()

    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) {
        items.size
    }

    var currentScale by remember { mutableStateOf(1f) }
    val showOriginal = remember { mutableStateMapOf<String, Boolean>() }

    var slideshowActive by remember { mutableStateOf(false) }
    var slideshowPaused by remember { mutableStateOf(false) }
    var slideshowIntervalSec by remember { mutableStateOf(5) }
    val coroutineScope = rememberCoroutineScope()

    // Swipe-to-dismiss: vertical drag offset of the whole pager. Driven by an
    // Animatable so a sub-threshold release can spring back smoothly.
    val density = LocalDensity.current
    val dismissOffsetY = remember { Animatable(0f) }

    // Info "continuation" panel (One UI style): a single progress value in
    // [0,1] drives the asset shrinking into a fixed header (portrait) or a
    // left pane (landscape) while the metadata panel fills the rest and
    // scrolls. 0 = full asset, 1 = info fully open. An upward swipe opens it;
    // an at-the-top downward drag inside the metadata closes it (a SEPARATE
    // gesture from the swipe-down-to-dismiss that closes the whole viewer).
    val infoProgress = remember { Animatable(0f) }
    val infoDragPx = with(density) { 320.dp.toPx() }
    // A deliberately slowish settle so the asset (and the video, which can only
    // crop in one binary native step near the end) eases into the 1:1 top
    // position instead of snapping — especially noticeable on a fast flick up.
    val infoSpring = remember {
        tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)
    }

    // Immersive mode: a single tap on the asset toggles all chrome (top bar +
    // bottom strip/actions) so the asset can be viewed edge to edge, matching
    // the iOS Photos / Google Photos gallery. Starts visible.
    var chromeVisible by remember { mutableStateOf(true) }
    // Measured height of the bottom chrome column, fed back as bottom padding to
    // the video player so its native controls (scrubber/timeline) render ABOVE
    // the actions bar instead of behind it.
    var bottomChromeHeightPx by remember { mutableStateOf(0) }
    val bottomChromeHeight = with(density) { bottomChromeHeightPx.toDp() }
    // Same for the top bar, so a portrait video is letterboxed BELOW it instead
    // of running up behind it.
    var topChromeHeightPx by remember { mutableStateOf(0) }
    val topChromeHeight = with(density) { topChromeHeightPx.toDp() }

    LaunchedEffect(
        slideshowActive,
        slideshowPaused,
        slideshowIntervalSec,
        pagerState.currentPage,
        items.size
    ) {
        if (!slideshowActive || slideshowPaused || items.isEmpty()) return@LaunchedEffect
        delay(slideshowIntervalSec * 1000L)
        val next = (pagerState.currentPage + 1) % items.size
        pagerState.animateScrollToPage(next)
    }

    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { index ->
                currentScale = 1f
                items.getOrNull(index)?.let {
                    viewModel.select(it.id)
                    // Inform the host (App.kt) which asset is now front-and-
                    // center so the matching grid thumbnail stays hidden under
                    // the shared-element morph until the user closes the viewer.
                    onPageChanged(it.id)
                }
                // Warm the neighbours so their info panel is ready when the user
                // swipes to them (otherwise sliding reveals empty text first).
                viewModel.prefetch(
                    listOfNotNull(items.getOrNull(index - 1)?.id, items.getOrNull(index + 1)?.id)
                )
                if (hasMore && index >= items.size - PAGER_PREFETCH_THRESHOLD) {
                    onLoadMore()
                }
            }
    }

    var showOverflow by remember { mutableStateOf(false) }
    var showEditDescription by remember { mutableStateOf(false) }
    var showEditDate by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    val currentItem = items.getOrNull(pagerState.currentPage)
    val currentIsFavorite = state.detail
        ?.takeIf { it.id == currentItem?.id }?.isFavorite
        ?: currentItem?.isFavorite ?: false
    val currentShowingOriginal = currentItem?.let { showOriginal[it.id] == true } == true

    // Orientation: the app is portrait-locked everywhere. The viewer offers an
    // explicit landscape mode, entered ONLY via the rotate button in the top bar
    // (turning the phone does nothing — we never auto-rotate). It works for both
    // photos and videos: the asset and the chrome (thumbnail strip + bars + back)
    // fill the screen, and a tap hides/shows the chrome, like the native gallery.
    var landscapeMode by remember { mutableStateOf(false) }
    LaunchedEffect(landscapeMode) {
        if (landscapeMode) OrientationController.forceLandscape()
        else OrientationController.lockPortrait()
    }
    DisposableEffect(Unit) {
        onDispose { OrientationController.lockPortrait() }
    }

    // We deliberately do NOT use Scaffold's topBar/bottomBar slots: those
    // constrain the content area to the space between the bars, so the photo
    // never lives *behind* the bar — making any translucency meaningless
    // (the bar would just blend with the Scaffold's black container, not
    // with the photo). Instead the pager fills the whole screen and the
    // bars are stacked on top inside the same Box. Both bars render a
    // semi-transparent blurred backdrop unconditionally, so the real photo
    // the pager is drawing underneath always bleeds through.
    // Containing the viewer in a Transparent Scaffold (rather than an opaque
    // black one) lets the dismiss drag fade the backdrop and reveal the grid
    // sitting behind this overlay — the iOS/Google Photos swipe-down effect.
    Scaffold(
        containerColor = Color.Transparent
    ) { _ ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay assets", color = Color.White)
            }
            return@Scaffold
        }

        // Distance that maps to a full fade; threshold past which release closes.
        val dismissDistancePx = with(density) { 240.dp.toPx() }
        val dismissThresholdPx = with(density) { 110.dp.toPx() }
        val dismissProgress = (abs(dismissOffsetY.value) / dismissDistancePx).coerceIn(0f, 1f)
        val backgroundAlpha = 1f - dismissProgress * 0.9f
        val pageScale = 1f - dismissProgress * 0.15f
        // Chrome fades out both while dragging to dismiss AND when the user
        // toggles immersive mode with a tap. animateFloatAsState gives the tap a
        // smooth cross-fade; the drag contribution stays frame-accurate.
        val chromeToggleAlpha by animateFloatAsState(
            targetValue = if (chromeVisible) 1f else 0f,
            label = "chromeToggleAlpha"
        )
        val chromeAlpha = (1f - dismissProgress) * chromeToggleAlpha * (1f - infoProgress.value)

        // Settles the info panel fully open or closed by position (no velocity,
        // matching the dismiss-by-distance convention). Shared by the drive
        // gesture's release and each page's close connection.
        suspend fun settleInfo() {
            infoProgress.animateTo(if (infoProgress.value >= 0.45f) 1f else 0f, infoSpring)
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val progress = infoProgress.value
            val infoOpen = progress > 0f
            // Info open shrinks the asset to a 1:1 SQUARE. Portrait: a top header
            // (side = screen width) that shares ONE vertical scroll with the
            // metadata below it — so once 1:1 is reached, continuing to scroll
            // carries the asset up together with the content. Landscape: a left
            // pane (side = screen height) with the metadata as an independent,
            // scrollable right pane. The asset isn't deformed: it crops
            // (zoom-to-fill), interpolated from Fit so opening is a smooth
            // progressive zoom rather than an instant jump.
            val headerHeightDp = maxHeight - (maxHeight - maxWidth) * progress
            val mediaWidthDp = maxWidth - (maxWidth - maxHeight) * progress
            val rightPaneWidthDp = (maxWidth - maxHeight) * progress
            val metadataMinHeightDp = maxHeight - headerHeightDp
            val authHeaders = remember(tokenStorage) { authHeadersFor(tokenStorage) }

            // Vertical-drive gesture (drag up = open info, drag down = dismiss
            // the viewer), active only while fully closed. Shared by both
            // orientations; only the rendered branch attaches it.
            val driveModifier = Modifier.pointerInput(items.size) {
                detectVerticalDriveGesture(
                    isEnabled = {
                        currentScale <= PAGER_DISABLE_THRESHOLD &&
                            !slideshowActive &&
                            infoProgress.value == 0f
                    },
                    onDrag = { dy, directionUp ->
                        if (directionUp) {
                            coroutineScope.launch {
                                infoProgress.snapTo((infoProgress.value - dy / infoDragPx).coerceIn(0f, 1f))
                            }
                        } else {
                            coroutineScope.launch {
                                dismissOffsetY.snapTo(dismissOffsetY.value + dy)
                            }
                        }
                    },
                    onRelease = { directionUp, totalY ->
                        if (directionUp) {
                            coroutineScope.launch { settleInfo() }
                        } else if (abs(totalY) > dismissThresholdPx) {
                            onBack()
                        } else {
                            coroutineScope.launch { dismissOffsetY.animateTo(0f) }
                        }
                    }
                )
            }

            // The fading black backdrop. Everything else stacks on top.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = backgroundAlpha))
            )

            // Swipe-to-dismiss transform (offset + scale) applied to the WHOLE
            // pager so the current asset isn't clipped while dragged down. A
            // no-op while info is open (dismissOffsetY = 0, pageScale = 1).
            val dismissTransform = Modifier.graphicsLayer {
                translationY = dismissOffsetY.value
                scaleX = pageScale
                scaleY = pageScale
            }

            // The pager is the OUTER container: each page holds the full
            // per-asset info view (1:1 header + metadata), so paging in info
            // mode slides the asset AND its info together. The vertical scroll
            // and the close gesture live per page.
            HorizontalPager(
                state = pagerState,
                // Paging stays enabled in info mode (the whole page slides);
                // only disabled while zoomed.
                userScrollEnabled = currentScale <= PAGER_DISABLE_THRESHOLD,
                pageSpacing = PAGER_PAGE_SPACING,
                modifier = Modifier.fillMaxSize().then(dismissTransform)
            ) { page ->
                val item = items[page]
                val isCurrent = page == pagerState.currentPage
                val pageDetail = details[item.id]
                val pageScroll = rememberScrollState()
                // Close-on-overscroll connection bound to THIS page's scroll.
                val pageConn = remember(pageScroll) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            val dy = available.y
                            val p = infoProgress.value
                            val midTransition = p > 0f && p < 1f
                            val closingFromTop = p >= 1f && dy > 0f && pageScroll.value == 0
                            if (midTransition || closingFromTop) {
                                coroutineScope.launch {
                                    infoProgress.snapTo((p - dy / infoDragPx).coerceIn(0f, 1f))
                                }
                                return Offset(0f, dy)
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (infoProgress.value < 1f) {
                                settleInfo()
                                return available
                            }
                            return Velocity.Zero
                        }
                    }
                }

                val renderAsset: @Composable () -> Unit = {
                    AssetPage(
                        item = item,
                        baseUrl = apiBaseUrl,
                        showOriginal = showOriginal[item.id] == true,
                        isCurrent = isCurrent,
                        authHeaders = authHeaders,
                        onScaleChange = { newScale -> if (isCurrent) currentScale = newScale },
                        onToggleChrome = { chromeVisible = !chromeVisible },
                        zoomEnabled = !infoOpen,
                        infoOpen = infoOpen,
                        // Video can't interpolate its crop (native binary
                        // gravity), so keep it fit (positioning) until the box is
                        // almost square, then crop — so it fills the 1:1 it's
                        // already in instead of jumping mid-transition.
                        videoFillCrop = progress >= 0.92f,
                        contentScale = blendedContentScale(progress),
                        onVideoControlsVisibilityChanged = { chromeVisible = it },
                        videoTopInset = if (chromeVisible && !landscapeMode && !infoOpen) topChromeHeight else 0.dp,
                        videoBottomInset = when {
                            infoOpen -> 0.dp
                            !chromeVisible -> 0.dp
                            landscapeMode -> bottomChromeHeight
                            else -> (bottomChromeHeight - STRIP_PORTRAIT_OVERLAP).coerceAtLeast(0.dp)
                        },
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }

                val renderMetadata: @Composable () -> Unit = {
                    AssetMetadataPanel(
                        fallback = item,
                        detail = pageDetail,
                        isLoading = isCurrent && state.isLoading,
                        errorMessage = if (isCurrent) state.error?.userMessage else null,
                        baseUrl = apiBaseUrl,
                        faces = if (isCurrent) state.faces else emptyList(),
                        samePersonAssets = if (isCurrent) state.samePersonAssets else emptyList(),
                        sameDayAssets = if (isCurrent) state.sameDayAssets else emptyList(),
                        onEditDescription = { showEditDescription = true },
                        onEditDate = { showEditDate = true },
                        onOpenFaces = { onOpenFaces(item.id) },
                        onAddTag = { tag -> viewModel.addTag(item.id, tag) },
                        onRemoveTag = { tag -> viewModel.removeTag(item.id, tag) },
                        onOpenAsset = onOpenAsset
                    )
                }

                if (landscapeMode) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(mediaWidthDp)
                                .then(driveModifier)
                        ) { renderAsset() }
                        if (infoOpen) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(rightPaneWidthDp)
                                    .nestedScroll(pageConn),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(modifier = Modifier.verticalScroll(pageScroll)) {
                                    renderMetadata()
                                }
                            }
                        }
                    }
                } else {
                    // Portrait: header + metadata in ONE vertical scroll so the
                    // square asset scrolls up together with the info; the
                    // nestedScroll connection (outside verticalScroll, so it
                    // intercepts as a parent) closes info at the top.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pageConn)
                            .verticalScroll(pageScroll, enabled = infoOpen)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerHeightDp)
                                .then(driveModifier)
                        ) { renderAsset() }
                        if (infoOpen) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = metadataMinHeightDp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                renderMetadata()
                            }
                        }
                    }
                }
            }

            // Skip the top bar entirely once faded out so it can't intercept
            // taps meant for the asset underneath (immersive mode).
            if (chromeAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = chromeAlpha }
                    .onSizeChanged { topChromeHeightPx = it.height }
            ) {
                ChromeBackground()
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    title = {
                        Text(
                            text = currentItem?.fileName ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1
                        )
                    },
                    actions = {
                        val isLocalOnly = currentItem?.isLocalOnly == true
                        // Landscape toggle (photos and videos): the ONLY way in
                        // or out of landscape — turning the phone does nothing.
                        // The asset and chrome fill the screen; a tap hides/shows
                        // the bars, like the native gallery.
                        IconButton(onClick = { landscapeMode = !landscapeMode }) {
                            Icon(
                                imageVector = if (landscapeMode) {
                                    Icons.Filled.ScreenLockPortrait
                                } else {
                                    Icons.Filled.ScreenRotation
                                },
                                contentDescription = if (landscapeMode) {
                                    "Volver a vertical"
                                } else {
                                    "Girar a horizontal"
                                }
                            )
                        }
                        if (currentItem != null && !currentItem.isVideo && !isLocalOnly) {
                            IconButton(onClick = {
                                showOriginal[currentItem.id] = !currentShowingOriginal
                            }) {
                                Text(
                                    text = if (currentShowingOriginal) "ORIG" else "HD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (currentShowingOriginal) Color(0xFFFFB300) else Color.White
                                )
                            }
                        }
                        if (items.size > 1 && !slideshowActive) {
                            IconButton(onClick = {
                                slideshowActive = true
                                slideshowPaused = false
                            }) {
                                Icon(
                                    Icons.Filled.Slideshow,
                                    contentDescription = stringResource(Res.string.slideshow_start)
                                )
                            }
                        }
                        // Landscape: the bottom action bar is hidden, so its
                        // actions live here as a floating overflow on the right —
                        // favourite / album / info inline, the rest under ⋮.
                        if (landscapeMode && currentItem != null && !isLocalOnly) {
                            IconButton(onClick = {
                                viewModel.toggleFavorite(currentItem.id) { confirmed ->
                                    onFavoriteChanged(currentItem.id, confirmed)
                                }
                            }) {
                                Icon(
                                    imageVector = if (currentIsFavorite) Icons.Filled.Favorite
                                    else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (currentIsFavorite) "Quitar favorito" else "Marcar favorito",
                                    tint = if (currentIsFavorite) Color(0xFFFF5252) else Color.White
                                )
                            }
                            IconButton(onClick = { onAddToAlbum(currentItem) }) {
                                Icon(Icons.Outlined.AddToPhotos, contentDescription = "Añadir a álbum", tint = Color.White)
                            }
                            IconButton(onClick = {
                                coroutineScope.launch { infoProgress.animateTo(1f, infoSpring) }
                            }) {
                                Icon(Icons.Outlined.Info, contentDescription = "Detalles", tint = Color.White)
                            }
                            Box {
                                IconButton(onClick = { showOverflow = true }) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        contentDescription = stringResource(Res.string.asset_action_more),
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflow,
                                    onDismissRequest = { showOverflow = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.asset_action_edit_description)) },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                        onClick = { showOverflow = false; showEditDescription = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.asset_action_faces)) },
                                        leadingIcon = { Icon(Icons.Outlined.Face, contentDescription = null) },
                                        onClick = { showOverflow = false; onOpenFaces(currentItem.id) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.asset_action_archive)) },
                                        leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.archive(currentItem.id) { id -> onAssetArchived(id) }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.asset_action_trash)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                        onClick = { showOverflow = false; showTrashConfirm = true }
                                    )
                                }
                            }
                        }
                    }
                )
            }
            }

            if (currentItem != null && !slideshowActive && chromeAlpha > 0.01f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = chromeAlpha }
                        .onSizeChanged { bottomChromeHeightPx = it.height }
                ) {
                    if (items.size > 1) {
                        AssetThumbnailStrip(
                            items = items,
                            pagerState = pagerState,
                            baseUrl = apiBaseUrl,
                            onThumbnailClick = { index ->
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Portrait keeps the full action bar at the bottom. In
                    // landscape these actions move UP into the top bar's floating
                    // overflow, leaving only the thumbnail strip down here.
                    if (!landscapeMode) {
                        AssetActionsBottomBar(
                            item = currentItem,
                            isFavorite = currentIsFavorite,
                            showOverflow = showOverflow,
                            onShowOverflowChange = { showOverflow = it },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(currentItem.id) { confirmed ->
                                    onFavoriteChanged(currentItem.id, confirmed)
                                }
                            },
                            onAddToAlbum = { onAddToAlbum(currentItem) },
                            onShowInfo = {
                                coroutineScope.launch { infoProgress.animateTo(1f, infoSpring) }
                            },
                            onTrashRequest = { showTrashConfirm = true },
                            onEditDescription = { showEditDescription = true },
                            onEditDate = { showEditDate = true },
                            onOpenFaces = { onOpenFaces(currentItem.id) },
                            onArchive = {
                                viewModel.archive(currentItem.id) { id ->
                                    onAssetArchived(id)
                                }
                            }
                        )
                    }
                }
            }

            if (slideshowActive) {
                SlideshowControls(
                    isPaused = slideshowPaused,
                    intervalSec = slideshowIntervalSec,
                    onTogglePause = { slideshowPaused = !slideshowPaused },
                    onPrevious = {
                        coroutineScope.launch {
                            val prev = (pagerState.currentPage - 1 + items.size) % items.size
                            pagerState.animateScrollToPage(prev)
                        }
                    },
                    onNext = {
                        coroutineScope.launch {
                            val next = (pagerState.currentPage + 1) % items.size
                            pagerState.animateScrollToPage(next)
                        }
                    },
                    onSetInterval = { slideshowIntervalSec = it },
                    onExit = {
                        slideshowActive = false
                        slideshowPaused = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 24.dp)
                )
            }
        }
    }

    if (showEditDescription && currentItem != null) {
        val current = state.detail.takeIf { it?.id == currentItem.id }?.caption
        EditDescriptionDialog(
            initialDescription = current,
            onDismiss = { showEditDescription = false },
            onConfirm = { value ->
                viewModel.updateDescription(currentItem.id, value)
                showEditDescription = false
            }
        )
    }

    if (showEditDate && currentItem != null) {
        val detail = state.detail.takeIf { it?.id == currentItem.id }
        // Prefer the stored capture date; fall back to the filesystem creation
        // date so an asset with no EXIF date can still be given one.
        val initial = detail?.exif?.dateTaken ?: detail?.fileCreatedAt ?: currentItem.fileCreatedAt
        EditCaptureDateDialog(
            initialDate = initial,
            isReadOnly = detail?.isReadOnly ?: false,
            onDismiss = { showEditDate = false },
            onConfirm = { instant, writeToFile ->
                viewModel.updateCaptureDate(currentItem.id, instant, writeToFile)
                showEditDate = false
            },
            onRequestSuggestion = { viewModel.captureDateSuggestion(currentItem.id) }
        )
    }

    if (showTrashConfirm && currentItem != null) {
        TrashAssetDialog(
            fileName = currentItem.fileName,
            onDismiss = { showTrashConfirm = false },
            onConfirm = {
                showTrashConfirm = false
                viewModel.trash(currentItem.id) { id -> onAssetTrashed(id) }
            }
        )
    }
}

/**
 * A [ContentScale] that blends from [ContentScale.Fit] (fraction 0) to
 * [ContentScale.Crop] (fraction 1). Driving [fraction] with the info-open
 * progress turns the square-crop into a smooth progressive zoom instead of an
 * instant Fit→Crop pop at the start of the gesture.
 */
private fun blendedContentScale(fraction: Float): ContentScale =
    if (fraction <= 0f) {
        ContentScale.Fit
    } else {
        object : ContentScale {
            override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
                val fit = ContentScale.Fit.computeScaleFactor(srcSize, dstSize)
                val fill = ContentScale.Crop.computeScaleFactor(srcSize, dstSize)
                return ScaleFactor(
                    lerp(fit.scaleX, fill.scaleX, fraction),
                    lerp(fit.scaleY, fill.scaleY, fraction)
                )
            }
        }
    }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AssetPage(
    item: TimelineItem,
    baseUrl: String,
    showOriginal: Boolean,
    isCurrent: Boolean,
    authHeaders: Map<String, String>,
    onScaleChange: (Float) -> Unit,
    onToggleChrome: () -> Unit = {},
    zoomEnabled: Boolean = true,
    infoOpen: Boolean = false,
    videoFillCrop: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    onVideoControlsVisibilityChanged: (Boolean) -> Unit = {},
    videoTopInset: androidx.compose.ui.unit.Dp = 0.dp,
    videoBottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val sharedScope = LocalSharedTransitionScope.current
    // Only attach the shared-element modifier while AnimatedVisibility is
    // actually transitioning (open or close). Keeping it on during stable
    // display would place the page in the shared overlay and decouple it
    // from the pager's swipe motion, making swipes feel choppy.
    val transition = animatedVisibilityScope?.transition
    val isTransitioning = transition != null &&
        transition.currentState != transition.targetState
    val sharedMod: Modifier = if (
        sharedScope != null &&
        animatedVisibilityScope != null &&
        isCurrent &&
        isTransitioning
    ) {
        with(sharedScope) {
            Modifier.sharedElement(
                state = rememberSharedContentState(key = "asset-${item.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ ->
                    androidx.compose.animation.core.tween(durationMillis = 320)
                }
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
      Box(modifier = Modifier.fillMaxSize().then(sharedMod), contentAlignment = Alignment.Center) {
        // Local-only entries don't exist on the server: render directly from
        // the device URI. Photos go through Coil; videos play in place off the
        // same local URI (content:// on Android, photokit:/asset on iOS) using
        // the shared platform player — mirroring DeviceAssetPreviewScreen so the
        // interleaved timeline behaves like the device-folder preview.
        val localUri = item.localUri
        if (localUri != null) {
            when {
                item.isVideo && isVideoPlaybackSupported && isCurrent && !isTransitioning -> {
                    VideoPlayer(
                        url = localUri,
                        headers = emptyMap(),
                        onControlsVisibilityChanged = onVideoControlsVisibilityChanged,
                        fillCrop = videoFillCrop,
                        controlsEnabled = !infoOpen,
                        modifier = Modifier.fillMaxSize()
                            .padding(top = videoTopInset, bottom = videoBottomInset)
                    )
                }
                else -> {
                    val model = item.localThumbnailModel ?: localUri
                    ZoomablePagerImage(
                        model = model,
                        contentDescription = item.fileName,
                        onScaleChange = onScaleChange,
                        zoomEnabled = zoomEnabled,
                        contentScale = contentScale,
                        onTap = onToggleChrome
                    )
                    if (item.isVideo && !isVideoPlaybackSupported) {
                        Text(
                            text = "Reproducción de vídeo no disponible en este sistema",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            return@Box
        }
        when {
            item.isVideo && isVideoPlaybackSupported && isCurrent && !isTransitioning -> {
                VideoPlayer(
                    url = "$baseUrl/api/assets/${item.id}/content",
                    headers = authHeaders,
                    onControlsVisibilityChanged = onVideoControlsVisibilityChanged,
                    fillCrop = videoFillCrop,
                    controlsEnabled = !infoOpen,
                    modifier = Modifier.fillMaxSize()
                        .padding(top = videoTopInset, bottom = videoBottomInset)
                )
            }
            item.isVideo -> {
                // Off-screen pages, unsupported platforms, or while a
                // shared-element transition is morphing the page bounds:
                // render the poster so Compose can animate it cleanly, and
                // let the platform video player mount only once the page
                // is at its stable, final size (AVPlayerViewController on
                // iOS bakes its initial bounds into auto-layout constraints
                // and never recovers if mounted mid-morph).
                AssetThumbnailImage(
                    item = item,
                    baseUrl = baseUrl,
                    size = "Large",
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
                if (!isVideoPlaybackSupported) {
                    Text(
                        text = "Reproducción de vídeo no disponible en este sistema",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            item.isLivePhoto && isVideoPlaybackSupported -> {
                LivePhotoPage(
                    item = item,
                    baseUrl = baseUrl,
                    showOriginal = showOriginal,
                    enabled = isCurrent && !isTransitioning,
                    authHeaders = authHeaders,
                    onScaleChange = onScaleChange,
                    zoomEnabled = zoomEnabled,
                    contentScale = contentScale,
                    onTap = onToggleChrome
                )
            }
            else -> {
                val imageUrl = if (showOriginal) {
                    "$baseUrl/api/assets/${item.id}/content"
                } else {
                    "$baseUrl/api/assets/${item.id}/thumbnail?size=Large"
                }
                ZoomablePagerImage(
                    model = imageUrl,
                    contentDescription = item.fileName,
                    onScaleChange = onScaleChange,
                    zoomEnabled = zoomEnabled,
                    contentScale = contentScale,
                    onTap = onToggleChrome
                )
            }
        }
      }
    }
}

/** How a Live Photo's motion clip is currently playing, if at all. */
private enum class MotionPlay { None, Hold, TapOnce }

/**
 * A Live Photo still with two ways to bring it to life:
 *  - press-and-hold, mirroring iOS Photos: the looping clip plays for as long
 *    as the finger stays down ([MotionPlay.Hold]);
 *  - a tappable "Ver foto en movimiento" pill that plays the clip through
 *    exactly once, like a short video, then reverts to the still
 *    ([MotionPlay.TapOnce]) — the discoverable affordance.
 *
 * The zoomable still is always present; whichever trigger is active mounts the
 * motion clip on top, and the pill flips to an active "LIVE" badge while it
 * plays.
 *
 * The hold detector lives in its own [pointerInput] layered over the image.
 * It only claims the gesture once the press survives [LIVE_HOLD_THRESHOLD_MS]
 * without moving past touch slop, so quick taps, double-tap-zoom and pinch
 * still reach [ZoomablePagerImage] underneath. The pill is drawn above that
 * layer, so a tap on it never reaches the hold detector.
 */
@Composable
private fun LivePhotoPage(
    item: TimelineItem,
    baseUrl: String,
    showOriginal: Boolean,
    enabled: Boolean,
    authHeaders: Map<String, String>,
    onScaleChange: (Float) -> Unit,
    zoomEnabled: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    onTap: (() -> Unit)? = null
) {
    var motionPlay by remember(item.id) { mutableStateOf(MotionPlay.None) }
    val playing = motionPlay != MotionPlay.None

    // Stop playing the moment the page stops being the active/stable one.
    LaunchedEffect(enabled) {
        if (!enabled) motionPlay = MotionPlay.None
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val imageUrl = if (showOriginal) {
            "$baseUrl/api/assets/${item.id}/content"
        } else {
            "$baseUrl/api/assets/${item.id}/thumbnail?size=Large"
        }
        ZoomablePagerImage(
            model = imageUrl,
            contentDescription = item.fileName,
            onScaleChange = onScaleChange,
            zoomEnabled = zoomEnabled,
            contentScale = contentScale,
            onTap = onTap
        )

        if (enabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(item.id) {
                        detectLivePhotoHold(
                            onHoldStart = { motionPlay = MotionPlay.Hold },
                            // Only the hold clears itself on release; a tap-once
                            // clip keeps playing until it reaches its end.
                            onHoldEnd = { if (motionPlay == MotionPlay.Hold) motionPlay = MotionPlay.None }
                        )
                    }
            )
        }

        if (playing && enabled) {
            MotionPhotoPlayer(
                url = "$baseUrl/api/assets/${item.id}/motion",
                headers = authHeaders,
                modifier = Modifier.fillMaxSize(),
                loop = motionPlay == MotionPlay.Hold,
                onPlaybackEnded = { if (motionPlay == MotionPlay.TapOnce) motionPlay = MotionPlay.None }
            )
        }

        LivePhotoBadge(
            active = playing,
            // Idle: a tappable pill that plays the clip once. While playing it
            // just confirms "LIVE" (hold releases on lift; tap-once auto-reverts).
            label = if (playing) "LIVE" else "Ver foto en movimiento",
            onClick = if (enabled && !playing) {
                { motionPlay = MotionPlay.TapOnce }
            } else null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 56.dp)
        )
    }
}

/**
 * Detects a vertical drag and routes it by direction, without stealing the
 * HorizontalPager's left/right swipes or the zoom/pan gestures.
 *
 * Strategy: accumulate movement from the first touch; the first axis to cross
 * touch slop wins. If horizontal wins (or a second finger lands, i.e. a pinch),
 * we bow out and never consume, leaving the pager/zoom in control. If vertical
 * wins — and [isEnabled] still holds — we claim the gesture and lock its
 * direction (the sign at claim time): an upward drag opens the info panel, a
 * downward drag dismisses the viewer. Each dy is forwarded to [onDrag] with the
 * locked direction; the total is reported to [onRelease] on lift so the caller
 * can settle.
 */
private suspend fun PointerInputScope.detectVerticalDriveGesture(
    isEnabled: () -> Boolean,
    onDrag: (dy: Float, directionUp: Boolean) -> Unit,
    onRelease: (directionUp: Boolean, totalDragY: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var claimed = false
        var directionUp = false
        var accumX = 0f
        var accumY = 0f

        while (true) {
            val event = awaitPointerEvent()

            // A second finger means the user is pinching to zoom — never drive.
            if (event.changes.count { it.pressed } > 1) {
                if (claimed) onRelease(directionUp, accumY)
                return@awaitEachGesture
            }

            val change = event.changes.firstOrNull() ?: break
            val delta = change.positionChange()
            accumX += delta.x
            accumY += delta.y

            if (!claimed) {
                if (!isEnabled()) return@awaitEachGesture
                if (abs(accumX) > slop && abs(accumX) >= abs(accumY)) {
                    // Horizontal intent — hand off to the pager.
                    return@awaitEachGesture
                }
                if (abs(accumY) > slop && abs(accumY) > abs(accumX)) {
                    claimed = true
                    directionUp = accumY < 0
                }
            }

            if (claimed) {
                onDrag(delta.y, directionUp)
                change.consume()
            }

            if (event.changes.all { !it.pressed }) break
        }

        if (claimed) onRelease(directionUp, accumY)
    }
}

private const val LIVE_HOLD_THRESHOLD_MS = 180L

/**
 * Waits for a press that is held (without panning past touch slop) for
 * [LIVE_HOLD_THRESHOLD_MS], fires [onHoldStart], then [onHoldEnd] once the
 * finger lifts or the gesture cancels. Never consumes the pointer, so the
 * underlying zoom/pan/tap detectors keep working.
 */
private suspend fun PointerInputScope.detectLivePhotoHold(
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var started = false

        try {
            // Hold gate: poll for the threshold while watching for movement.
            // withTimeout returns null-ish via cancellation if the finger
            // lifts/moves first, so we model it by hand with a deadline.
            val holdResult = withTimeoutOrNull(LIVE_HOLD_THRESHOLD_MS) {
                var totalPan = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { !it.pressed }) return@withTimeoutOrNull false
                    totalPan += event.changes.sumOf { it.positionChange().getDistance().toDouble() }.toFloat()
                    if (totalPan > slop) return@withTimeoutOrNull false
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }

            // Timed out *still pressed and steady* → treat as a hold.
            if (holdResult == null && down.pressed) {
                started = true
                onHoldStart()
            } else {
                return@awaitEachGesture
            }

            // Hold active: wait for release/cancel.
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.all { !it.pressed }) break
            }
        } finally {
            if (started) onHoldEnd()
        }
    }
}

@Composable
private fun LivePhotoBadge(
    active: Boolean,
    modifier: Modifier = Modifier,
    label: String = "LIVE",
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = if (active) 0.55f else 0.4f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.MotionPhotosOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun AssetMetadataPanel(
    fallback: TimelineItem,
    detail: AssetDetail?,
    isLoading: Boolean,
    errorMessage: String?,
    baseUrl: String,
    faces: List<com.photonne.app.data.models.Face>,
    samePersonAssets: List<com.photonne.app.data.models.PersonAsset>,
    sameDayAssets: List<com.photonne.app.data.models.PersonAsset>,
    onEditDescription: () -> Unit,
    onEditDate: () -> Unit,
    onOpenFaces: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onOpenAsset: (TimelineItem) -> Unit
) {
    val exif = detail?.exif
    // Scrolling is owned by the caller's container (the portrait collapsing
    // column or the landscape pane), so this is plain content.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Keep the content clear of the device's navigation bar at the
            // bottom, then add comfortable top/bottom breathing room so it
            // doesn't butt up against the asset above or the system buttons.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = detail?.fileName ?: fallback.fileName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2
        )

        // Editable description + capture date — only for server-backed assets
        // (a local-only asset has no detail and can't be edited).
        if (detail != null) {
            MetadataEditableRow(
                leadingIcon = null,
                label = "Descripción",
                value = detail.caption?.takeIf { it.isNotBlank() },
                placeholder = "Añadir descripción",
                onClick = onEditDescription
            )
            val captureDate = exif?.dateTaken ?: detail.fileCreatedAt
            MetadataEditableRow(
                leadingIcon = Icons.Outlined.DateRange,
                label = "Fecha de captura",
                value = formatInstant(captureDate.toString()),
                placeholder = "",
                onClick = onEditDate
            )
        } else {
            MetadataRow("Creado", formatInstant(fallback.fileCreatedAt.toString()))
        }

        // Technical EXIF as a 2-column grid of stat cells — placed ABOVE the
        // map so the headline numbers (resolution, size…) read first.
        val stats = buildList {
            val width = exif?.width ?: fallback.width
            val height = exif?.height ?: fallback.height
            if (width != null && height != null) {
                val megapixels = width.toLong() * height.toLong() / 1_000_000.0
                add(StatCell(icon = Icons.Outlined.AspectRatio, value = "${formatOneDecimal(megapixels)} MP", label = "$width × $height"))
            }
            add(StatCell(icon = Icons.Outlined.Storage, value = formatBytes(detail?.fileSize ?: fallback.fileSize), label = "Tamaño"))
            exif?.iso?.let { add(StatCell(icon = Icons.Outlined.Iso, value = "ISO $it", label = "Sensibilidad")) }
            exif?.aperture?.let { add(StatCell(icon = Icons.Outlined.Camera, value = "f/$it", label = "Apertura")) }
            exif?.shutterSpeed?.let { add(StatCell(icon = Icons.Outlined.ShutterSpeed, value = formatShutter(it), label = "Velocidad")) }
            exif?.focalLength?.let { add(StatCell(icon = Icons.Outlined.CenterFocusStrong, value = "${it.roundToInt()} mm", label = "Distancia focal")) }
        }
        ExifGrid(stats)

        exif?.cameraDisplay?.let {
            MetadataInfoRow(leadingIcon = Icons.Outlined.PhotoCamera, label = "Cámara", value = it)
        }

        // Location: a cropped map centred exactly on the point (now below EXIF).
        val lat = exif?.latitude
        val lon = exif?.longitude
        if (lat != null && lon != null) {
            LocationMap(latitude = lat, longitude = lon)
        }

        // Detected faces — thumbnails inline; tap any (or "Ver todas") opens
        // the full faces sheet.
        if (detail != null && faces.isNotEmpty()) {
            FacesSection(faces = faces, baseUrl = baseUrl, onOpenFaces = onOpenFaces)
        } else if (detail != null) {
            MetadataActionRow(
                leadingIcon = Icons.Outlined.Face,
                label = "Ver caras",
                onClick = onOpenFaces
            )
        }

        // Editable tags: auto tags fixed, user tags removable, plus "+ Añadir".
        if (detail != null) {
            EditableTagsSection(
                userTags = detail.userTags,
                autoTags = detail.autoTags,
                onAddTag = onAddTag,
                onRemoveTag = onRemoveTag
            )
        } else {
            val tags = fallback.tags
            if (tags.isNotEmpty()) MetadataRow("Etiquetas", tags.joinToString(", "))
        }

        detail?.folderPath?.let { MetadataRow("Carpeta", it) }

        // Related assets: more of the same people, then more from the same day.
        if (samePersonAssets.isNotEmpty()) {
            RelatedAssetsRow(
                title = "Mismas personas",
                items = samePersonAssets,
                baseUrl = baseUrl,
                onOpenAsset = onOpenAsset
            )
        }
        if (sameDayAssets.isNotEmpty()) {
            RelatedAssetsRow(
                title = "Mismo día",
                items = sameDayAssets,
                baseUrl = baseUrl,
                onOpenAsset = onOpenAsset
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp).padding(8.dp))
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class StatCell(val icon: ImageVector, val value: String, val label: String)

/** Numeric EXIF laid out as cards, two per row. */
@Composable
private fun ExifGrid(cells: List<StatCell>) {
    if (cells.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { rowCells ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowCells.forEach { cell ->
                    ExifStatCard(cell = cell, modifier = Modifier.weight(1f))
                }
                if (rowCells.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExifStatCard(cell: StatCell, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = cell.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = cell.value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = cell.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Detected faces as a horizontal strip of circular thumbnails. The whole
 * section is tappable (and a trailing chevron makes that obvious) so it opens
 * the full faces sheet where the user assigns/edits people.
 */
@Composable
private fun FacesSection(
    faces: List<com.photonne.app.data.models.Face>,
    baseUrl: String,
    onOpenFaces: () -> Unit
) {
    Surface(
        onClick = onOpenFaces,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Caras (${faces.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(faces, key = { _, f -> f.id }) { _, face ->
                    AsyncImage(
                        model = "$baseUrl/api/faces/${face.id}/thumbnail",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                }
            }
        }
    }
}

/**
 * Tag chips: ML/auto tags first (fixed, no remove), then user tags (each with
 * an ✕ to remove), then a "+ Añadir" chip that opens a small add dialog.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditableTagsSection(
    userTags: List<String>,
    autoTags: List<String>,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Etiquetas",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            autoTags.forEach { tag -> TagChip(label = tag, onRemove = null) }
            userTags.forEach { tag -> TagChip(label = tag, onRemove = { onRemoveTag(tag) }) }
            AddTagChip(onClick = { showAddDialog = true })
        }
    }
    if (showAddDialog) {
        AddTagDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { value ->
                showAddDialog = false
                onAddTag(value)
            }
        )
    }
}

@Composable
private fun TagChip(label: String, onRemove: (() -> Unit)?) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = if (onRemove != null) 6.dp else 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (onRemove != null) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Quitar $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { onRemove() }
                )
            }
        }
    }
}

@Composable
private fun AddTagChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Añadir",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun AddTagDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir etiqueta") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("p. ej. Vacaciones") }
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Añadir") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * A labelled horizontal strip of related-asset thumbnails (e.g. "Mismas
 * personas", "Mismo día"). Tapping a thumbnail opens that asset.
 */
@Composable
private fun RelatedAssetsRow(
    title: String,
    items: List<com.photonne.app.data.models.PersonAsset>,
    baseUrl: String,
    onOpenAsset: (TimelineItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(items, key = { _, a -> a.id }) { _, asset ->
                val item = asset.toTimelineItem()
                AssetThumbnailImage(
                    item = item,
                    baseUrl = baseUrl,
                    size = "Small",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenAsset(item) }
                )
            }
        }
    }
}

/** A tappable card (description, capture date) that opens an edit dialog. */
@Composable
private fun MetadataEditableRow(
    leadingIcon: ImageVector?,
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = value ?: placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Editar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** A non-interactive labelled card (e.g. camera). */
@Composable
private fun MetadataInfoRow(leadingIcon: ImageVector, label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** A tappable navigation card (e.g. faces) with a trailing chevron. */
@Composable
private fun MetadataActionRow(leadingIcon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * A cropped OSM map centred exactly on [latitude]/[longitude] with the pin in
 * the dead centre. The tiles are laid out as an adjacent grid (Column of Rows)
 * — so the layout itself guarantees they meet edge-to-edge with no seam — sized
 * with requiredSize so the box constraints don't clip the grid (which once
 * blanked the map), and the WHOLE grid is shifted by a single offset to centre
 * the point.
 */
@Composable
private fun LocationMap(latitude: Double, longitude: Double) {
    val mapsUrl = "https://www.google.com/maps/?q=$latitude,$longitude"
    val density = LocalDensity.current
    val n = 1 shl MAP_ZOOM
    val worldX = (longitude + 180.0) / 360.0 * n
    val latRad = latitude * PI / 180.0
    val worldY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
    val centerTileX = floor(worldX).toInt()
    val centerTileY = floor(worldY).toInt()
    val fracX = worldX - centerTileX
    val fracY = worldY - centerTileY

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(Res.string.asset_metadata_location),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MAP_HEIGHT_DP)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { openExternalUrl(mapsUrl) }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val tilePx = with(density) { MAP_TILE_DP.toPx() }
                val boxWpx = with(density) { maxWidth.toPx() }
                val boxHpx = with(density) { maxHeight.toPx() }
                // Tiles needed on each side of the centre tile to cover the box.
                val halfX = ceil(boxWpx / 2f / tilePx).toInt() + 1
                val halfY = ceil(boxHpx / 2f / tilePx).toInt() + 1
                // Single shift so the exact point lands at the box centre.
                val gridOffsetX = (boxWpx / 2.0 - (halfX + fracX) * tilePx).roundToInt()
                val gridOffsetY = (boxHpx / 2.0 - (halfY + fracY) * tilePx).roundToInt()
                Column(
                    modifier = Modifier
                        .requiredSize(
                            width = MAP_TILE_DP * (2 * halfX + 1),
                            height = MAP_TILE_DP * (2 * halfY + 1)
                        )
                        .offset { IntOffset(gridOffsetX, gridOffsetY) }
                ) {
                    for (dy in -halfY..halfY) {
                        Row {
                            for (dx in -halfX..halfX) {
                                val tileX = (((centerTileX + dx) % n) + n) % n
                                val tileY = (centerTileY + dy).coerceIn(0, n - 1)
                                AsyncImage(
                                    model = "https://tile.openstreetmap.org/$MAP_ZOOM/$tileX/$tileY.png",
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(MAP_TILE_DP)
                                )
                            }
                        }
                    }
                }
            }
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = stringResource(Res.string.asset_metadata_open_map),
                tint = Color(0xFFE53935),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-15).dp)
                    .size(34.dp)
            )
            // Bottom bar inside the card: coordinates on the left, the
            // "open in maps" action on the right.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatGps(latitude, longitude),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable { openExternalUrl(mapsUrl) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(Res.string.asset_action_open_in_maps),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private const val MAP_ZOOM = 16
private val MAP_TILE_DP = 180.dp
private val MAP_HEIGHT_DP = 200.dp

private fun formatOneDecimal(value: Double): String =
    ((value * 10).roundToInt() / 10.0).toString()

private fun formatShutter(seconds: Double): String =
    if (seconds >= 1.0) "${formatOneDecimal(seconds)} s"
    else "1/${(1.0 / seconds).roundToInt()} s"

private fun formatGps(lat: Double, lon: Double): String {
    fun round5(value: Double): String {
        val rounded = (value * 100000).toLong() / 100000.0
        return rounded.toString()
    }
    return "${round5(lat)}, ${round5(lon)}"
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = ""
    for (u in units) {
        value /= 1024.0
        unit = u
        if (value < 1024) break
    }
    return "${(value * 10).toLong() / 10.0} $unit"
}

private fun formatInstant(iso: String): String {
    return iso
        .substringBefore('.')
        .removeSuffix("Z")
        .replace('T', ' ')
}

private fun authHeadersFor(tokenStorage: TokenStorage): Map<String, String> {
    val token = tokenStorage.getAccessToken().orEmpty()
    return if (token.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer $token")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlideshowControls(
    isPaused: Boolean,
    intervalSec: Int,
    onTogglePause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetInterval: (Int) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.7f),
        contentColor = Color.White,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onTogglePause) {
                Icon(
                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(
                        if (isPaused) Res.string.slideshow_play else Res.string.slideshow_pause
                    ),
                    tint = Color.White
                )
            }
            IconButton(onClick = onPrevious) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(Res.string.slideshow_previous),
                    tint = Color.White
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = stringResource(Res.string.slideshow_next),
                    tint = Color.White
                )
            }
            listOf(3, 5, 10).forEach { sec ->
                FilterChip(
                    selected = intervalSec == sec,
                    onClick = { onSetInterval(sec) },
                    label = { Text("${sec}s") }
                )
            }
            IconButton(onClick = onExit) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.slideshow_exit),
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Horizontal thumbnail strip that sits above [AssetActionsBottomBar] and
 * partially overlays the photo. The strip itself is transparent — only the
 * thumbnails occlude the image — so the bottom of the photo bleeds through
 * the gaps and the bar stack feels like a floating element.
 *
 * The strip's scroll position is driven by the pager (not the user) so the
 * centered slot stays aligned with the currently visible photo while the
 * user swipes. Each slot scales based on its continuous distance from
 * `pagerState.currentPage + currentPageOffsetFraction`, producing the
 * iOS/Android-style grow/shrink animation as items move through the center.
 */
@Composable
private fun AssetThumbnailStrip(
    items: List<TimelineItem>,
    pagerState: PagerState,
    baseUrl: String,
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val stripState = rememberLazyListState()
    val density = LocalDensity.current
    // Narrow slots pack the strip densely and let a single drag travel across
    // many photos, so long-distance scrubbing feels fluid. A small gap keeps
    // neighbours visually separated; the per-item pitch (slot + gap) is what
    // the pager<->strip sync uses to convert between scroll offset and pages.
    val slotWidth = 34.dp
    val itemSpacing = 2.dp
    val slotWidthPx = with(density) { slotWidth.toPx() }
    val pitchPx = slotWidthPx + with(density) { itemSpacing.toPx() }

    // Pager and strip share one continuous position. Either side can drive,
    // but never both: while the user is dragging the strip we forward its
    // scroll to the pager; otherwise the pager's swipe drives the strip.
    // Gating on interactionSource (rather than isScrollInProgress) avoids
    // counting our own programmatic scrollToItem calls as user input.
    val stripDragged by stripState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, items.size, pitchPx) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            pagerState.currentPage + pagerState.currentPageOffsetFraction
        }.collect { pos ->
            if (stripDragged) return@collect
            val safe = pos.coerceIn(0f, items.lastIndex.toFloat())
            val baseIndex = floor(safe).toInt().coerceIn(0, items.lastIndex)
            val offset = ((safe - baseIndex) * pitchPx).toInt().coerceAtLeast(0)
            stripState.scrollToItem(baseIndex, offset)
        }
    }

    LaunchedEffect(stripState, items.size, pitchPx) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            stripState.firstVisibleItemIndex to stripState.firstVisibleItemScrollOffset
        }.collect { (idx, offset) ->
            if (!stripDragged) return@collect
            val pos = (idx + offset / pitchPx).coerceIn(0f, items.lastIndex.toFloat())
            // Scrubbing the strip should make the main photo SNAP to whichever
            // asset is nearest the centre — not slide with a fractional offset
            // the way a finger swipe does. So we forward only the integer page
            // (offset 0) and fire just when it changes, leaving the filmstrip
            // itself to scroll continuously under the finger.
            val targetPage = pos.roundToInt().coerceIn(0, items.lastIndex)
            if (targetPage != pagerState.currentPage) {
                pagerState.scrollToPage(targetPage, 0f)
            }
        }
    }

    val continuousPos by remember(pagerState) {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    BoxWithConstraints(modifier = modifier) {
        // contentPadding centers the first/last item: with slot S and
        // viewport V, side padding = (V - S) / 2 lets index 0 sit dead
        // center when stripState scroll is 0.
        val sidePadding = ((maxWidth - slotWidth) / 2).coerceAtLeast(0.dp)
        // Centre slot peaks at this scale; the surrounding vertical padding is
        // sized to fit it so the enlarged thumbnail isn't clipped top/bottom.
        val centerScale = 1.5f
        val verticalPadding = 10.dp
        // How far the centred thumbnail bulges past its slot on each side. We
        // translate the neighbours outward by this much so the bigger centre
        // parts them aside instead of overlapping them.
        val centerBulgePx = (centerScale - 1f) * slotWidthPx / 2f
        LazyRow(
            state = stripState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = PaddingValues(
                start = sidePadding,
                end = sidePadding,
                top = verticalPadding,
                bottom = verticalPadding
            )
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val signed = index - continuousPos
                val proximity = 1f - abs(signed).coerceIn(0f, 1f)
                // High min scale keeps off-centre slots nearly touching, while
                // the centred slot grows well past its box so the current asset
                // reads as clearly larger than the rest.
                val scale = lerp(0.94f, centerScale, proximity)
                val alpha = lerp(0.55f, 1f, proximity)
                // Push each side rigidly away from the centre by the bulge so
                // the grown centre opens a gap instead of covering its
                // neighbours; clamped to ±1 slot it tapers smoothly mid-scrub.
                val push = signed.coerceIn(-1f, 1f) * centerBulgePx
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .height(slotWidth)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            translationX = push
                        }
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { onThumbnailClick(index) }
                ) {
                    AssetThumbnailImage(
                        item = item,
                        baseUrl = baseUrl,
                        size = "Small",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Background slot shared by the top and bottom chrome bars: a flat
 * translucent black scrim so the photo the pager draws behind shows through
 * directly (no blurred copy of the asset stamped on top).
 */
@Composable
private fun BoxScope.ChromeBackground() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.4f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetActionsBottomBar(
    item: TimelineItem,
    isFavorite: Boolean,
    showOverflow: Boolean,
    onShowOverflowChange: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToAlbum: () -> Unit,
    onShowInfo: () -> Unit,
    onTrashRequest: () -> Unit,
    onEditDescription: () -> Unit,
    onEditDate: () -> Unit,
    onOpenFaces: () -> Unit,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        ChromeBackground()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isLocalOnly) {
                IconButton(onClick = onShowInfo) {
                    Icon(Icons.Outlined.Info, contentDescription = "Detalles", tint = Color.White)
                }
            } else {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar favorito" else "Marcar favorito",
                        tint = if (isFavorite) Color(0xFFFF5252) else Color.White
                    )
                }
                IconButton(onClick = onAddToAlbum) {
                    Icon(
                        Icons.Outlined.AddToPhotos,
                        contentDescription = "Añadir a álbum",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onShowInfo) {
                    Icon(Icons.Outlined.Info, contentDescription = "Detalles", tint = Color.White)
                }
                IconButton(onClick = onTrashRequest) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(Res.string.asset_action_trash),
                        tint = Color.White
                    )
                }
                Box {
                    IconButton(onClick = { onShowOverflowChange(true) }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = stringResource(Res.string.asset_action_more),
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { onShowOverflowChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.asset_action_edit_description)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                onShowOverflowChange(false)
                                onEditDescription()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.asset_action_edit_date)) },
                            leadingIcon = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
                            onClick = {
                                onShowOverflowChange(false)
                                onEditDate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.asset_action_faces)) },
                            leadingIcon = { Icon(Icons.Outlined.Face, contentDescription = null) },
                            onClick = {
                                onShowOverflowChange(false)
                                onOpenFaces()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.asset_action_archive)) },
                            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                            onClick = {
                                onShowOverflowChange(false)
                                onArchive()
                            }
                        )
                    }
                }
            }
        }
    }
}
