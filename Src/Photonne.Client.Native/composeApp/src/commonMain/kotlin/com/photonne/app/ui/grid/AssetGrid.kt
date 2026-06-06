package com.photonne.app.ui.grid

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.photonne.app.ui.image.AssetThumbnailImage
import com.photonne.app.ui.theme.LocalCurrentDetailAssetId
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
    hasMore: Boolean = false,
    isAppending: Boolean = false,
    isInitialLoading: Boolean = false,
    onLoadMore: () -> Unit = {},
    onItemLongClick: ((Int) -> Unit)? = null,
    selectedIds: Set<String> = emptySet(),
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

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize()
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
        itemsIndexed(items, key = { index, item -> assetCellKey(item, index) }) { index, asset ->
            AssetGridCell(
                asset = asset,
                baseUrl = baseUrl,
                onClick = { onItemClick(index) },
                onLongClick = onItemLongClick?.let { { it(index) } },
                isSelected = asset.id in selectedIds
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AssetGridCell(
    asset: TimelineItem,
    baseUrl: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
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
    Box(
        modifier = modifier
            .let { base -> if (forceSquare) base.fillMaxWidth().aspectRatio(1f) else base }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.18f else 0f))
            .padding(selectionPadding)
            .background(placeholder ?: MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .let { base -> if (onLongClick != null) base.onSecondaryClick(onLongClick) else base }
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
                tint = Color(0xFFFF5252),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(16.dp)
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
