package com.photonne.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.MapCluster
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Lat/lng box derived from the current viewport and zoom; what we
 * forward to the backend so it can return only the visible clusters. */
data class MapBounds(
    val zoom: Int,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
)

/**
 * Interactive OSM tile viewer with pinch-to-zoom and drag-to-pan, plus
 * an overlay of clickable cluster markers. Calls [onViewportChanged]
 * whenever the visible region settles so the caller can refetch data.
 */
@Composable
fun OsmMap(
    centerLat: Double,
    centerLng: Double,
    zoom: Int,
    clusters: List<MapCluster>,
    onCenterChanged: (lat: Double, lng: Double) -> Unit,
    onZoomChanged: (zoom: Int) -> Unit,
    onViewportChanged: (MapBounds) -> Unit,
    onClusterClick: (MapCluster) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // Pinch zoom can hold a fractional accumulator that we promote to
    // an integer zoom level once it crosses the threshold; OSM tiles
    // only exist at integer zooms.
    var zoomAccumulator by remember(zoom) { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE6E2DA))
            .onSizeChanged { size = it }
            .pointerInput(zoom) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    if (pan.x != 0f || pan.y != 0f) {
                        // Convert screen drag (pixels) to a shift in world pixels.
                        val center = project(LatLng(centerLat, centerLng), zoom)
                        val newCenter = WorldPx(center.x - pan.x, center.y - pan.y)
                        val unprojected = unproject(newCenter, zoom)
                        onCenterChanged(unprojected.latitude, unprojected.longitude)
                    }
                    if (gestureZoom != 1f) {
                        zoomAccumulator += (gestureZoom - 1f)
                        if (zoomAccumulator > 0.6f && zoom < MAX_ZOOM) {
                            zoomAccumulator = 0f
                            onZoomChanged(zoom + 1)
                        } else if (zoomAccumulator < -0.6f && zoom > MIN_ZOOM) {
                            zoomAccumulator = 0f
                            onZoomChanged(zoom - 1)
                        }
                    }
                }
            }
            .pointerInput(zoom) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (zoom < MAX_ZOOM) {
                            val center = project(LatLng(centerLat, centerLng), zoom)
                            val tapWorld = WorldPx(
                                x = center.x + (tap.x - size.width / 2.0),
                                y = center.y + (tap.y - size.height / 2.0)
                            )
                            val newPoint = unproject(tapWorld, zoom)
                            onCenterChanged(newPoint.latitude, newPoint.longitude)
                            onZoomChanged(zoom + 1)
                        }
                    }
                )
            }
    ) {
        if (size == IntSize.Zero) return@Box

        val center = project(LatLng(centerLat, centerLng), zoom)
        val viewportLeftWorldX = center.x - size.width / 2.0
        val viewportTopWorldY = center.y - size.height / 2.0
        val firstTileX = floor(viewportLeftWorldX / TILE_SIZE_PX).toInt()
        val firstTileY = floor(viewportTopWorldY / TILE_SIZE_PX).toInt()
        val tilesPerRow = (size.width / TILE_SIZE_PX) + 2
        val tilesPerCol = (size.height / TILE_SIZE_PX) + 2
        val tileMax = (1L shl zoom).toInt()
        val tileSizeDp = with(density) { TILE_SIZE_PX.toDp() }

        for (row in 0 until tilesPerCol) {
            for (col in 0 until tilesPerRow) {
                val tileX = firstTileX + col
                val tileY = firstTileY + row
                if (tileY < 0 || tileY >= tileMax) continue
                val wrappedX = ((tileX % tileMax) + tileMax) % tileMax
                val tilePxX = (tileX * TILE_SIZE_PX - viewportLeftWorldX).roundToInt()
                val tilePxY = (tileY * TILE_SIZE_PX - viewportTopWorldY).roundToInt()
                AsyncImage(
                    model = "https://tile.openstreetmap.org/$zoom/$wrappedX/$tileY.png",
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset { IntOffset(tilePxX, tilePxY) }
                        .size(tileSizeDp, tileSizeDp)
                )
            }
        }

        for (cluster in clusters) {
            val world = project(LatLng(cluster.latitude, cluster.longitude), zoom)
            val screenX = (world.x - viewportLeftWorldX).roundToInt()
            val screenY = (world.y - viewportTopWorldY).roundToInt()
            // Cull markers slightly outside the viewport
            if (screenX < -64 || screenX > size.width + 64) continue
            if (screenY < -64 || screenY > size.height + 64) continue
            ClusterMarker(
                cluster = cluster,
                offsetPx = IntOffset(screenX, screenY),
                onClick = { onClusterClick(cluster) }
            )
        }

        // Fire viewport-changed once layout settles. We compute lat/lng
        // bounds at the four screen corners using the projection.
        LaunchedEffect(zoom, centerLat, centerLng, size) {
            if (size.width == 0 || size.height == 0) return@LaunchedEffect
            val tl = unproject(WorldPx(viewportLeftWorldX, viewportTopWorldY), zoom)
            val br = unproject(
                WorldPx(
                    viewportLeftWorldX + size.width,
                    viewportTopWorldY + size.height
                ),
                zoom
            )
            onViewportChanged(
                MapBounds(
                    zoom = zoom,
                    minLat = min(tl.latitude, br.latitude),
                    maxLat = max(tl.latitude, br.latitude),
                    minLng = min(tl.longitude, br.longitude),
                    maxLng = max(tl.longitude, br.longitude)
                )
            )
        }
    }
}

@Composable
private fun ClusterMarker(
    cluster: MapCluster,
    offsetPx: IntOffset,
    onClick: () -> Unit
) {
    // Keep the disc roughly the same on-screen size whatever the
    // density. Single-photo clusters get a smaller pin so the
    // viewport doesn't drown in 40dp circles when fully zoomed in.
    val sizeDp = if (cluster.count == 1) 28 else (32 + (cluster.count.coerceAtMost(99) / 4))
    val halfPx = with(LocalDensity.current) { (sizeDp.dp / 2).toPx().toInt() }
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetPx.x - halfPx, offsetPx.y - halfPx) }
            .size(sizeDp.dp)
            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
            .pointerInput(cluster.id) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (cluster.count == 1) "1" else cluster.count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Min/max zoom we ever request from OSM. The free tile server caps at
 * 19; we stay one short so re-pinching after max never produces blanks. */
const val MIN_ZOOM = 1
const val MAX_ZOOM = 18
