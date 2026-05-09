package com.photonne.app.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.di.PhotonneAppConfig
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val PAGER_PREFETCH_THRESHOLD = 8
private const val PAGER_DISABLE_THRESHOLD = 1.05f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    items: List<TimelineItem>,
    startIndex: Int,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onFavoriteChanged: (assetId: String, isFavorite: Boolean) -> Unit,
    onAddToAlbum: (assetId: String) -> Unit = {}
) {
    val viewModel: AssetDetailViewModel = koinViewModel()
    val config: PhotonneAppConfig = koinInject()
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
                items.getOrNull(index)?.let { viewModel.select(it.id) }
                if (hasMore && index >= items.size - PAGER_PREFETCH_THRESHOLD) {
                    onLoadMore()
                }
            }
    }

    var showInfo by remember { mutableStateOf(false) }
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentItem = items.getOrNull(pagerState.currentPage)
    val currentIsFavorite = state.detail
        ?.takeIf { it.id == currentItem?.id }?.isFavorite
        ?: currentItem?.isFavorite ?: false
    val currentShowingOriginal = currentItem?.let { showOriginal[it.id] == true } == true

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
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
                    if (currentItem != null && !currentItem.isVideo) {
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
                    if (currentItem != null) {
                        IconButton(onClick = {
                            viewModel.toggleFavorite(currentItem.id) { confirmed ->
                                onFavoriteChanged(currentItem.id, confirmed)
                            }
                        }) {
                            Icon(
                                imageVector = if (currentIsFavorite) Icons.Filled.Favorite
                                else Icons.Filled.FavoriteBorder,
                                contentDescription = if (currentIsFavorite) "Quitar favorito"
                                else "Marcar favorito",
                                tint = if (currentIsFavorite) Color(0xFFFF5252) else Color.White
                            )
                        }
                        IconButton(onClick = { onAddToAlbum(currentItem.id) }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Añadir a álbum"
                            )
                        }
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Filled.Info, contentDescription = "Detalles")
                    }
                }
            )
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
                baseUrl = config.apiBaseUrl,
                showOriginal = showOriginal[item.id] == true,
                isCurrent = isCurrent,
                authHeaders = remember(tokenStorage) { authHeadersFor(tokenStorage) },
                onScaleChange = { newScale -> if (isCurrent) currentScale = newScale }
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
}

@Composable
private fun AssetPage(
    item: TimelineItem,
    baseUrl: String,
    showOriginal: Boolean,
    isCurrent: Boolean,
    authHeaders: Map<String, String>,
    onScaleChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
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
