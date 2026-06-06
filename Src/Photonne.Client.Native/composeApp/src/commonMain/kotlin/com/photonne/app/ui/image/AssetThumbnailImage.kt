package com.photonne.app.ui.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.photonne.app.data.models.TimelineItem

/**
 * Resolves the best thumbnail source for [item].
 *
 * Device-local entries draw straight from their local thumbnail model.
 * For server assets we deliberately hit the thumbnail endpoint **even when
 * [TimelineItem.hasThumbnails] is false**: that endpoint generates the
 * thumbnail on demand (and caches it immutably), so attempting it is how a
 * freshly-scanned — or previously-failed — asset finally gets a picture
 * instead of an empty cell. Returns `null` only when there is genuinely no
 * source to try (a local-only entry with no thumbnail and no server id).
 */
fun thumbnailModelFor(item: TimelineItem, baseUrl: String, size: String): String? {
    item.localThumbnailModel?.let { return it }
    if (item.id.isNotBlank()) {
        return "$baseUrl/api/assets/${item.id}/thumbnail?size=$size"
    }
    return null
}

/**
 * A thumbnail that never leaves a blank hole. While loading it stays
 * transparent so the caller's dominant-colour backdrop shows through; if the
 * load fails (or there is no source at all) it falls back to a typed glyph —
 * a film camera for video, a picture frame for images, a broken-image mark
 * when a known source errored out. Shared by the timeline grid, the detail
 * filmstrip and the detail poster so all three behave identically.
 *
 * Uses [AsyncImage] (not SubcomposeAsyncImage) plus an `onState` overlay so
 * it stays cheap to recycle inside LazyGrid/LazyRow.
 */
@Composable
fun AssetThumbnailImage(
    item: TimelineItem,
    baseUrl: String,
    size: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    /**
     * When false, no image request is created (or kept) at all — the caller's
     * dominant-colour backdrop shows through. Used by the timeline while the
     * scrubber is being dragged: teleporting the viewport several times per
     * second with live AsyncImages churns one image request per cell per
     * jump, which is exactly the work that made fast scrubbing stutter.
     */
    enabled: Boolean = true,
) {
    if (!enabled) {
        Box(modifier = modifier)
        return
    }
    val model = remember(item.id, item.localThumbnailModel, baseUrl, size) {
        thumbnailModelFor(item, baseUrl, size)
    }
    var failed by remember(model) { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (model != null && !failed) {
            AsyncImage(
                model = model,
                contentDescription = item.fileName,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true }
            )
        } else {
            ThumbnailFallbackIcon(
                icon = when {
                    model == null && item.isVideo -> Icons.Outlined.Videocam
                    model == null -> Icons.Outlined.Image
                    else -> Icons.Outlined.BrokenImage
                }
            )
        }
    }
}

@Composable
private fun ThumbnailFallbackIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxSize(0.4f)
    )
}
