package com.photonne.app.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.asset_action_archive
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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val PAGER_PREFETCH_THRESHOLD = 8
private const val PAGER_DISABLE_THRESHOLD = 1.05f

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
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val viewModel: AssetDetailViewModel = koinViewModel()
    val apiBaseUrl = rememberApiBaseUrl()
    val tokenStorage: TokenStorage = koinInject()
    val state by viewModel.state.collectAsState()

    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) {
        items.size
    }

    var currentScale by remember { mutableStateOf(1f) }
    val showOriginal = remember { mutableStateMapOf<String, Boolean>() }

    var slideshowActive by remember { mutableStateOf(false) }
    var slideshowPaused by remember { mutableStateOf(false) }
    var slideshowIntervalSec by remember { mutableStateOf(5) }
    val coroutineScope = rememberCoroutineScope()

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
                if (hasMore && index >= items.size - PAGER_PREFETCH_THRESHOLD) {
                    onLoadMore()
                }
            }
    }

    var showInfo by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showEditDescription by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentItem = items.getOrNull(pagerState.currentPage)
    val currentIsFavorite = state.detail
        ?.takeIf { it.id == currentItem?.id }?.isFavorite
        ?: currentItem?.isFavorite ?: false
    val currentShowingOriginal = currentItem?.let { showOriginal[it.id] == true } == true

    // We deliberately do NOT use Scaffold's topBar/bottomBar slots: those
    // constrain the content area to the space between the bars, so the photo
    // never lives *behind* the bar — making any translucency meaningless
    // (the bar would just blend with the Scaffold's black container, not
    // with the photo). Instead the pager fills the whole screen and the
    // bars are stacked on top inside the same Box. Both bars render a
    // semi-transparent blurred backdrop unconditionally, so the real photo
    // the pager is drawing underneath always bleeds through.
    Scaffold(
        containerColor = Color.Black
    ) { _ ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay assets", color = Color.White)
            }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= PAGER_DISABLE_THRESHOLD,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = items[page]
                val isCurrent = page == pagerState.currentPage
                AssetPage(
                    item = item,
                    baseUrl = apiBaseUrl,
                    showOriginal = showOriginal[item.id] == true,
                    isCurrent = isCurrent,
                    authHeaders = remember(tokenStorage) { authHeadersFor(tokenStorage) },
                    onScaleChange = { newScale -> if (isCurrent) currentScale = newScale },
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
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
                    }
                )
            }

            if (currentItem != null && !slideshowActive) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
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
                        onShowInfo = { showInfo = true },
                        onTrashRequest = { showTrashConfirm = true },
                        onEditDescription = { showEditDescription = true },
                        onOpenFaces = { onOpenFaces(currentItem.id) },
                        onArchive = {
                            viewModel.archive(currentItem.id) { id ->
                                onAssetArchived(id)
                            }
                        }
                    )
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

    if (showInfo && currentItem != null) {
        ModalBottomSheet(
            onDismissRequest = { showInfo = false },
            sheetState = infoSheetState
        ) {
            AssetMetadataPanel(
                fallback = currentItem,
                detail = state.detail.takeIf { it?.id == currentItem.id },
                isLoading = state.isLoading,
                errorMessage = state.error?.userMessage
            )
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AssetPage(
    item: TimelineItem,
    baseUrl: String,
    showOriginal: Boolean,
    isCurrent: Boolean,
    authHeaders: Map<String, String>,
    onScaleChange: (Float) -> Unit,
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
        // Local-only entries don't exist on the server: render directly
        // from the device URI through Coil for both photos and the
        // video poster. We deliberately don't try to play a device
        // video here — it pulls in platform players that the regular
        // server pipeline avoids, and the existing
        // DeviceAssetPreviewScreen already covers that case if needed.
        val localUri = item.localUri
        if (localUri != null) {
            val model = item.localThumbnailModel ?: localUri
            ZoomablePagerImage(
                model = model,
                contentDescription = item.fileName,
                onScaleChange = onScaleChange
            )
            if (item.isVideo) {
                Text(
                    text = "Vídeo del dispositivo — abre Copia de seguridad para reproducirlo",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            return@Box
        }
        when {
            item.isVideo && isVideoPlaybackSupported && isCurrent && !isTransitioning -> {
                VideoPlayer(
                    url = "$baseUrl/api/assets/${item.id}/content",
                    headers = authHeaders,
                    modifier = Modifier.fillMaxSize()
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
                AsyncImage(
                    model = "$baseUrl/api/assets/${item.id}/thumbnail?size=Large",
                    contentDescription = item.fileName,
                    contentScale = ContentScale.Fit,
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
            else -> {
                val imageUrl = if (showOriginal) {
                    "$baseUrl/api/assets/${item.id}/content"
                } else {
                    "$baseUrl/api/assets/${item.id}/thumbnail?size=Large"
                }
                ZoomablePagerImage(
                    model = imageUrl,
                    contentDescription = item.fileName,
                    onScaleChange = onScaleChange
                )
            }
        }
      }
    }
}

@Composable
private fun AssetMetadataPanel(
    fallback: TimelineItem,
    detail: AssetDetail?,
    isLoading: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = detail?.fileName ?: fallback.fileName,
            style = MaterialTheme.typography.titleMedium
        )
        detail?.caption?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(4.dp))

        MetadataRow("Tipo", (detail?.type ?: fallback.type).lowercase().replaceFirstChar { it.uppercase() })
        MetadataRow("Tamaño del archivo", formatBytes(detail?.fileSize ?: fallback.fileSize))
        val width = detail?.exif?.width ?: fallback.width
        val height = detail?.exif?.height ?: fallback.height
        if (width != null && height != null) {
            MetadataRow("Dimensiones", "${width} × ${height}")
        }
        MetadataRow("Creado", formatInstant((detail?.fileCreatedAt ?: fallback.fileCreatedAt).toString()))
        detail?.exif?.dateTaken?.let { MetadataRow("Fecha de captura", formatInstant(it.toString())) }
        detail?.exif?.cameraDisplay?.let { MetadataRow("Cámara", it) }
        detail?.exif?.iso?.let { MetadataRow("ISO", it.toString()) }
        detail?.exif?.aperture?.let { MetadataRow("Apertura", "f/$it") }
        detail?.exif?.shutterSpeed?.let { MetadataRow("Velocidad", "${it}s") }
        detail?.exif?.focalLength?.let { MetadataRow("Focal", "${it} mm") }
        detail?.folderPath?.let { MetadataRow("Carpeta", it) }

        val lat = detail?.exif?.latitude
        val lon = detail?.exif?.longitude
        if (lat != null && lon != null) {
            GpsPreview(latitude = lat, longitude = lon)
        }

        val tags = detail?.tags ?: fallback.tags
        if (tags.isNotEmpty()) {
            MetadataRow("Etiquetas", tags.joinToString(", "))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(20.dp).padding(8.dp))
            }
        }
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GpsPreview(latitude: Double, longitude: Double) {
    val coords = formatGps(latitude, longitude)
    val mapsUrl = "https://www.google.com/maps/?q=$latitude,$longitude"
    val grid = remember(latitude, longitude) { computeMapGrid(latitude, longitude, MAP_ZOOM) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(Res.string.asset_metadata_location),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { openExternalUrl(mapsUrl) }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    OsmTile(MAP_ZOOM, grid.tileXStart, grid.tileYStart, Modifier.weight(1f).fillMaxHeight())
                    OsmTile(MAP_ZOOM, grid.tileXStart + 1, grid.tileYStart, Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    OsmTile(MAP_ZOOM, grid.tileXStart, grid.tileYStart + 1, Modifier.weight(1f).fillMaxHeight())
                    OsmTile(MAP_ZOOM, grid.tileXStart + 1, grid.tileYStart + 1, Modifier.weight(1f).fillMaxHeight())
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pinX = maxWidth * grid.markerXFraction.toFloat() - 12.dp
                val pinY = maxHeight * grid.markerYFraction.toFloat() - 24.dp
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = stringResource(Res.string.asset_metadata_open_map),
                    tint = Color(0xFFE53935),
                    modifier = Modifier
                        .offset(x = pinX, y = pinY)
                        .size(24.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = coords,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        TextButton(onClick = { openExternalUrl(mapsUrl) }) {
            Icon(Icons.Filled.LocationOn, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.asset_action_open_in_maps))
        }
    }
}

@Composable
private fun OsmTile(zoom: Int, x: Int, y: Int, modifier: Modifier) {
    AsyncImage(
        model = "https://tile.openstreetmap.org/$zoom/$x/$y.png",
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
    )
}

private const val MAP_ZOOM = 15

private data class MapGrid(
    val tileXStart: Int,
    val tileYStart: Int,
    val markerXFraction: Double,
    val markerYFraction: Double
)

private fun computeMapGrid(lat: Double, lon: Double, zoom: Int): MapGrid {
    val n = (1 shl zoom).toDouble()
    val worldX = (lon + 180.0) / 360.0 * n
    val latRad = lat * PI / 180.0
    val worldY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
    val tileXStart = floor(worldX - 0.5).toInt()
    val tileYStart = floor(worldY - 0.5).toInt()
    val markerXFraction = (worldX - tileXStart) / 2.0
    val markerYFraction = (worldY - tileYStart) / 2.0
    return MapGrid(tileXStart, tileYStart, markerXFraction, markerYFraction)
}

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
    // Narrow slots pack the strip densely (neighbours nearly touching) and let
    // a single drag travel across many photos, so long-distance scrubbing feels
    // fluid even though the strip stays locked 1:1 to the pager.
    val slotWidth = 34.dp
    val slotWidthPx = with(density) { slotWidth.toPx() }

    // Pager and strip share one continuous position. Either side can drive,
    // but never both: while the user is dragging the strip we forward its
    // scroll to the pager; otherwise the pager's swipe drives the strip.
    // Gating on interactionSource (rather than isScrollInProgress) avoids
    // counting our own programmatic scrollToItem calls as user input.
    val stripDragged by stripState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(pagerState, items.size, slotWidthPx) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            pagerState.currentPage + pagerState.currentPageOffsetFraction
        }.collect { pos ->
            if (stripDragged) return@collect
            val safe = pos.coerceIn(0f, items.lastIndex.toFloat())
            val baseIndex = floor(safe).toInt().coerceIn(0, items.lastIndex)
            val offset = ((safe - baseIndex) * slotWidthPx).toInt().coerceAtLeast(0)
            stripState.scrollToItem(baseIndex, offset)
        }
    }

    LaunchedEffect(stripState, items.size, slotWidthPx) {
        if (items.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            stripState.firstVisibleItemIndex to stripState.firstVisibleItemScrollOffset
        }.collect { (idx, offset) ->
            if (!stripDragged) return@collect
            val pos = (idx + offset / slotWidthPx).coerceIn(0f, items.lastIndex.toFloat())
            // Anchor to the NEAREST page so the offset fraction stays naturally
            // within scrollToPage's valid [-0.5, 0.5] range. Anchoring to the
            // floor instead makes the fraction climb to 1.0, get clamped at 0.5
            // (the photo freezes mid-swipe), then snap back to ~0 when the base
            // page ticks over — the visible "stop and jump".
            val basePage = pos.roundToInt().coerceIn(0, items.lastIndex)
            val frac = (pos - basePage).coerceIn(-0.5f, 0.5f)
            pagerState.scrollToPage(basePage, frac)
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
        LazyRow(
            state = stripState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = sidePadding,
                end = sidePadding,
                top = 4.dp,
                bottom = 4.dp
            )
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val distance = abs(index - continuousPos)
                val proximity = 1f - distance.coerceIn(0f, 1f)
                // High min scale keeps off-centre slots nearly touching (only a
                // hair of gap), while the centred slot grows well past its box
                // so the current asset reads as clearly larger than the rest.
                val scale = lerp(0.94f, 1.38f, proximity)
                val alpha = lerp(0.55f, 1f, proximity)
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .height(slotWidth)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable { onThumbnailClick(index) }
                ) {
                    val thumbnailModel = item.localThumbnailModel
                        ?: if (item.hasThumbnails) {
                            "$baseUrl/api/assets/${item.id}/thumbnail?size=Small"
                        } else null
                    if (thumbnailModel != null) {
                        AsyncImage(
                            model = thumbnailModel,
                            contentDescription = item.fileName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
