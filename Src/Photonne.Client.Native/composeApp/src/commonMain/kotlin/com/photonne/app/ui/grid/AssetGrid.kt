package com.photonne.app.ui.grid

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.LocalSyncBadge
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.grid.dragselect.AssetCellContentType
import com.photonne.app.ui.grid.dragselect.DragSelectConfig
import com.photonne.app.ui.grid.dragselect.DragSelectState
import com.photonne.app.ui.grid.dragselect.dragSelectable
import com.photonne.app.ui.grid.dragselect.rememberLazyGridDragSelectAdapter
import com.photonne.app.ui.haptics.rememberPhotonneHaptics
import com.photonne.app.ui.image.AssetThumbnailImage
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.LocalCurrentDetailAssetId
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.LocalSharedTransitionScope
import com.photonne.app.ui.util.onSecondaryClick
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val PREFETCH_THRESHOLD = 12

/**
 * Reusable square thumbnail grid shared by the Timeline and Album
 * detail. Owns the prefetch trigger that calls [onLoadMore] when the
 * user scrolls near the end of [items].
 */
@Composable
fun AssetGrid(
    items: List<TimelineItem>,
    baseUrl: String,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    /** Padding around the grid content — e.g. to reserve the immersive nav's
     * height at the scroll end so the last row clears the overlaid bar. */
    contentPadding: PaddingValues = PaddingValues(0.dp),
    hasMore: Boolean = false,
    isAppending: Boolean = false,
    isInitialLoading: Boolean = false,
    onLoadMore: () -> Unit = {},
    onItemLongClick: ((Int) -> Unit)? = null,
    selectedIds: Set<String> = emptySet(),
    /**
     * Arrastre en banda. Cuando llega, el long-press deja de gestionarlo la
     * celda y pasa a gestionarlo la rejilla: si ambos lo escuchan se cancelan
     * mutuamente y no entra ninguno.
     */
    dragSelect: AssetGridDragSelect? = null,
    header: (@Composable () -> Unit)? = null
) {
    val shouldLoadMore by remember(hasMore, isAppending, isInitialLoading) {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - PREFETCH_THRESHOLD &&
                hasMore && !isAppending && !isInitialLoading
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    val headerCount = if (header != null) 1 else 0
    val haptics = rememberPhotonneHaptics()
    val dragSelectAdapter = rememberLazyGridDragSelectAdapter(
        gridState = gridState,
        headerCount = { headerCount },
        idAt = { ordinal -> items.getOrNull(ordinal)?.id }
    )
    val gridModifier = if (dragSelect != null) {
        modifier.fillMaxSize().dragSelectable(
            state = dragSelect.state,
            adapter = dragSelectAdapter,
            enabled = dragSelect.enabled,
            selectionActive = selectedIds.isNotEmpty(),
            isSelected = { it in selectedIds },
            onPatch = dragSelect.onPatch,
            haptics = haptics,
            railStartPx = dragSelect.railStartPx,
            config = dragSelect.config
        )
    } else {
        modifier.fillMaxSize()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = gridModifier
    ) {
        if (header != null) {
            item(
                key = "asset-grid-header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "asset-grid-header"
            ) {
                header()
            }
        }
        itemsIndexed(
            items,
            key = { index, item -> assetCellKey(item, index) },
            contentType = { _, _ -> AssetCellContentType }
        ) { index, asset ->
            AssetGridCell(
                asset = asset,
                baseUrl = baseUrl,
                onClick = { onItemClick(index) },
                // Con arrastre en banda el long-press lo posee la rejilla.
                onLongClick = if (dragSelect != null) null
                else onItemLongClick?.let { { it(index) } },
                // El clic derecho sigue viniendo de la celda: escritorio no
                // tiene long-press y es su única entrada a la selección.
                onSecondaryClick = onItemLongClick?.let { { it(index) } },
                isSelected = asset.id in selectedIds
            )
        }
    }
}

/**
 * Configuración del arrastre en banda para [AssetGrid]. Se agrupa en un objeto
 * en vez de en seis parámetros sueltos porque o vienen todos o no viene
 * ninguno.
 */
@Immutable
data class AssetGridDragSelect(
    val state: DragSelectState,
    val onPatch: (SelectionPatch) -> Unit,
    val enabled: Boolean = true,
    val railStartPx: Float = 0f,
    val config: DragSelectConfig = DragSelectConfig.Default
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AssetGridCell(
    asset: TimelineItem,
    baseUrl: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /**
     * Clic derecho. Sigue a [onLongClick] salvo que se indique otra cosa: la
     * rejilla con arrastre en banda le quita el long-press a la celda pero le
     * deja el secundario, que en escritorio es la única entrada a selección.
     */
    onSecondaryClick: (() -> Unit)? = onLongClick,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    /**
     * When `true` (default) the cell forces a 1:1 square shape — the
     * legacy uniform-square grid behavior used by Trash, Favorites, etc.
     * Pass `false` from layouts that already set size externally (e.g.
     * the justified timeline grid where width/height come from the row's
     * height and the cell's weight).
     */
    forceSquare: Boolean = true,
    /**
     * When false the cell renders only its dominant-colour backdrop and
     * badges — no thumbnail request. The timeline flips this while the
     * scrubber is dragged so viewport teleports stay cheap.
     */
    loadThumbnail: Boolean = true
) {
    val placeholder = remember(asset.dominantColor) { parseHexColor(asset.dominantColor) }
    val sharedScope = LocalSharedTransitionScope.current
    val currentDetailId = LocalCurrentDetailAssetId.current
    // Only register the cell as a shared-element source while the asset
    // viewer is open (or animating closed). Registering on every cell at
    // all times made LazyGrid scrolling churn the SharedTransitionScope's
    // internal bookkeeping on every recycle — visibly laggier scroll.
    val thumbnailSharedMod: Modifier = if (sharedScope != null && currentDetailId != null) {
        val sharedKey = remember(asset.id) { "asset-${asset.id}" }
        with(sharedScope) {
            Modifier.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(key = sharedKey),
                visible = currentDetailId != asset.id,
                boundsTransform = { _, _ ->
                    androidx.compose.animation.core.tween(durationMillis = 320)
                }
            )
        }
    } else {
        Modifier
    }
    val selectionPadding by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        label = "selectionPadding"
    )
    val secondaryClick = onSecondaryClick
    Box(
        // OJO con el orden: el padding animado va DESPUÉS de fijar el tamaño,
        // así que encoge el contenido pero no el nodo. El hit-test del
        // arrastre en banda usa la caja del LazyGrid, y si alguien mueve ese
        // padding delante del aspectRatio, seleccionar una celda la encogería
        // bajo el dedo y la banda empezaría a fallar.
        modifier = modifier
            .let { base -> if (forceSquare) base.fillMaxWidth().aspectRatio(1f) else base }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.18f else 0f))
            .padding(selectionPadding)
            .background(placeholder ?: MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .let { base -> if (secondaryClick != null) base.onSecondaryClick(secondaryClick) else base }
    ) {
        AssetThumbnailImage(
            item = asset,
            baseUrl = baseUrl,
            size = "Small",
            modifier = Modifier.fillMaxSize().then(thumbnailSharedMod),
            enabled = loadThumbnail
        )
        asset.localSyncBadge?.let { badge ->
            // BottomStart so we don't collide with the video glyph
            // (TopEnd) or the favorite heart (BottomEnd).
            LocalSyncBadge(
                badge = badge,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
            )
        }
        if (asset.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else if (asset.isLivePhoto) {
            // Same TopEnd slot as the video glyph (they're mutually exclusive):
            // a Live Photo badge mirroring the iOS Photos affordance.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MotionPhotosOn,
                    contentDescription = "Live Photo",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (asset.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Favorito",
                tint = PhotonneColors.favorite,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.xs)
                    .size(IconSize.sm)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun LocalSyncBadge(badge: LocalSyncBadge, modifier: Modifier = Modifier) {
    // Pending: a translucent gray circle so the cloud icon reads as
    // "queued / not yet uploaded" without competing with favorite or
    // selection accents. Uploading / Failed keep their semantic
    // colours because they signal active work or an error.
    val (bg, icon) = when (badge) {
        LocalSyncBadge.Pending ->
            Color(0xFF424242).copy(alpha = 0.7f) to Icons.Filled.CloudUpload
        LocalSyncBadge.Uploading ->
            MaterialTheme.colorScheme.tertiary to Icons.Filled.CloudUpload
        LocalSyncBadge.Failed ->
            MaterialTheme.colorScheme.error to Icons.Filled.Refresh
    }
    Box(
        modifier = modifier
            .size(24.dp)
            .background(bg, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

internal fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}
