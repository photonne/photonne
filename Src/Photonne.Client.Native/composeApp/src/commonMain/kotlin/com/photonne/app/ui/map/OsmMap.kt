package com.photonne.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.MapPoint
import kotlin.math.floor
import kotlin.math.roundToInt

/** Min/max zoom we ever request from the tile server. CARTO and OSM both
 * go up to 19; we stay one short so re-pinching past max never blanks out. */
const val MIN_ZOOM = 1
const val MAX_ZOOM = 18

/** Dark CARTO basemap, same as the PWA's default. `{s}` rotates through
 * `a..d` so requests don't all hit the same subdomain. */
private const val TILE_URL_TEMPLATE =
    "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"

private val MarkerBorderColor = Color(0xFFFFD166)
private val ClusterBadgeColor = Color(0xFFF44336)
private val MapBackground = Color(0xFF1A1A1A)

/**
 * Interactive CARTO tile viewer with pinch-to-zoom and drag-to-pan,
 * plus an overlay of clickable thumbnail markers. Cluster markers
 * carry a small red badge with the photo count; single-photo markers
 * are just the thumbnail circle.
 */
@Composable
fun OsmMap(
    centerLat: Double,
    centerLng: Double,
    zoom: Int,
    points: List<MapPoint>,
    baseUrl: String,
    onCenterChanged: (lat: Double, lng: Double) -> Unit,
    onZoomChanged: (zoom: Int) -> Unit,
    onClusterClick: (List<MapPoint>) -> Unit,
    onPointClick: (MapPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    var zoomAccumulator by remember(zoom) { mutableFloatStateOf(0f) }

    val markers by remember(points, zoom) {
        derivedStateOf { clusterPoints(points = points, zoom = zoom) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MapBackground)
            .onSizeChanged { size = it }
            .pointerInput(zoom) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    if (pan.x != 0f || pan.y != 0f) {
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
                val subdomain = "abcd"[(((tileX + tileY) % 4) + 4) % 4]
                AsyncImage(
                    model = TILE_URL_TEMPLATE
                        .replace("{s}", subdomain.toString())
                        .replace("{z}", zoom.toString())
                        .replace("{x}", wrappedX.toString())
                        .replace("{y}", tileY.toString()),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset { IntOffset(tilePxX, tilePxY) }
                        .size(tileSizeDp, tileSizeDp)
                )
            }
        }

        for (marker in markers) {
            val screenX = (marker.worldX - viewportLeftWorldX).roundToInt()
            val screenY = (marker.worldY - viewportTopWorldY).roundToInt()
            // Generous overdraw so a half-visible marker stays on
            // screen until it's fully gone.
            if (screenX < -120 || screenX > size.width + 120) continue
            if (screenY < -120 || screenY > size.height + 120) continue
            ThumbnailMarker(
                marker = marker,
                baseUrl = baseUrl,
                offsetPx = IntOffset(screenX, screenY),
                onClick = {
                    if (marker.count == 1) onPointClick(marker.firstPoint)
                    else onClusterClick(marker.points)
                }
            )
        }
    }
}

@Composable
private fun ThumbnailMarker(
    marker: MapMarker,
    baseUrl: String,
    offsetPx: IntOffset,
    onClick: () -> Unit
) {
    // Same scaling rule as the PWA: 40dp floor, +2dp per asset, capped
    // at 80dp so dense regions don't take over the screen.
    val circleDp = if (marker.count == 1) 44
    else (35 + marker.count * 2).coerceIn(40, 80)
    val badgeOffsetDp = 12
    val totalDp = circleDp + badgeOffsetDp
    val halfPx = with(LocalDensity.current) { (totalDp.dp / 2).toPx().toInt() }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetPx.x - halfPx, offsetPx.y - halfPx) }
            .size(totalDp.dp)
            .pointerInput(marker.id) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        // Yellow ring + thumbnail centred inside the wrapper.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(circleDp.dp)
                .clip(CircleShape)
                .background(MarkerBorderColor)
                .padding(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val previewId = marker.firstPoint.id
            if (marker.firstPoint.hasThumbnail) {
                AsyncImage(
                    model = "$baseUrl/api/assets/$previewId/thumbnail?size=Small",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (marker.count > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(ClusterBadgeColor)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (marker.count > 999) "999+" else marker.count.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
