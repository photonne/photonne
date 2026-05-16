package com.photonne.app.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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
import com.photonne.app.ui.theme.LocalSharedTransitionScope
import com.photonne.app.ui.util.openExternalUrl
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlinx.coroutines.flow.distinctUntilChanged
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

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            // Glass effect: render a heavily blurred copy of the current
            // photo behind the bar so the bar picks up the photo's color/
            // lighting; then layer a dark scrim on top so the icons stay
            // legible regardless of the underlying image.
            Box(modifier = Modifier.fillMaxWidth()) {
                val currentBackdrop = currentItem
                if (currentBackdrop != null && currentBackdrop.hasThumbnails && !currentBackdrop.isLocalOnly) {
                    AsyncImage(
                        model = "$apiBaseUrl/api/assets/${currentBackdrop.id}/thumbnail?size=Small",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(radius = 40.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.55f),
                                    Color.Black.copy(alpha = 0.30f)
                                )
                            )
                        )
                )
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
                    if (currentItem != null && !isLocalOnly) {
                        IconButton(onClick = {
                            viewModel.toggleFavorite(currentItem.id) { confirmed ->
                                onFavoriteChanged(currentItem.id, confirmed)
                            }
                        }) {
                            Icon(
                                imageVector = if (currentIsFavorite) Icons.Filled.Favorite
                                else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (currentIsFavorite) "Quitar favorito"
                                else "Marcar favorito",
                                tint = if (currentIsFavorite) Color(0xFFFF5252) else Color.White
                            )
                        }
                        IconButton(onClick = { onAddToAlbum(currentItem) }) {
                            Icon(
                                Icons.Outlined.AddToPhotos,
                                contentDescription = "Añadir a álbum"
                            )
                        }
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Detalles")
                    }
                    if (currentItem != null && !isLocalOnly) {
                        Box {
                            IconButton(onClick = { showOverflow = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(Res.string.asset_action_more)
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflow,
                                onDismissRequest = { showOverflow = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.asset_action_edit_description)) },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        showEditDescription = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.asset_action_faces)) },
                                    leadingIcon = { Icon(Icons.Outlined.Face, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        onOpenFaces(currentItem.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.asset_action_archive)) },
                                    leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                                    onClick = {
                                        showOverflow = false
                                        viewModel.archive(currentItem.id) { id ->
                                            onAssetArchived(id)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(Res.string.asset_action_trash),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflow = false
                                        showTrashConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
                )
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay assets", color = Color.White)
            }
            return@Scaffold
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = currentScale <= PAGER_DISABLE_THRESHOLD,
            modifier = Modifier.fillMaxSize().padding(padding)
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
                errorMessage = state.errorMessage
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
            item.isVideo && isVideoPlaybackSupported && isCurrent -> {
                VideoPlayer(
                    url = "$baseUrl/api/assets/${item.id}/content",
                    headers = authHeaders,
                    modifier = Modifier.fillMaxSize()
                )
            }
            item.isVideo -> {
                // Off-screen pages or unsupported platforms render the poster
                // so the pager preview stays cheap and predictable.
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
