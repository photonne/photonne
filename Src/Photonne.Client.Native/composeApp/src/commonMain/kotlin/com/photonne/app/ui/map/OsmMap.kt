package com.photonne.app.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.MapPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Min/max zoom we ever request from the tile server. CARTO and OSM both
 * go up to 19; we stay one short so re-pinching past max never blanks out. */
const val MIN_ZOOM = 1
const val MAX_ZOOM = 18

/** Dark CARTO basemap, same as the PWA's default. `{s}` rotates through
 * `a..d` so requests don't all hit the same subdomain. */
private const val TILE_URL_TEMPLATE_DARK =
    "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"

/** Light CARTO basemap, used when the host theme is in light mode. */
private const val TILE_URL_TEMPLATE_LIGHT =
    "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"

private val MarkerBorderColor = Color(0xFFFFD166)
private val ClusterBadgeColor = Color(0xFFF44336)
private val MapBackgroundDark = Color(0xFF1A1A1A)
private val MapBackgroundLight = Color(0xFFE6E2DA)

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
    darkTiles: Boolean,
    onCenterChanged: (lat: Double, lng: Double) -> Unit,
    onZoomChanged: (zoom: Int) -> Unit,
    onClusterClick: (List<MapPoint>) -> Unit,
    onPointClick: (MapPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val tileTemplate = if (darkTiles) TILE_URL_TEMPLATE_DARK else TILE_URL_TEMPLATE_LIGHT
    val backgroundColor = if (darkTiles) MapBackgroundDark else MapBackgroundLight
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // `pointerInput` only restarts when its keys change, so the gesture
    // lambda would otherwise close over a stale center after the first
    // pan — every subsequent pan event would start from the original
    // coordinates and the map would refuse to move. Reading through
    // `rememberUpdatedState` lets the same long-lived lambda see fresh
    // state on every event without rebuilding the gesture detector.
    val currentCenterLat by rememberUpdatedState(centerLat)
    val currentCenterLng by rememberUpdatedState(centerLng)
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    // Visual scale + pivot for Leaflet-style smooth pinch zoom. The map
    // renders at an integer zoom level but the gesture handler stretches
    // the tile/marker layer continuously between integer steps, then
    // snaps the residual scale back to 1f when the user lifts.
    // `pinchScale` is written synchronously from the gesture handler so
    // the visual tracks the pinch without a frame of latency; the
    // snap-back animation runs in `scope.launch` and writes back into
    // the same state.
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var pinchPivot by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val snapBackJob = remember { mutableStateOf<Job?>(null) }

    val markers by remember(points, zoom) {
        derivedStateOf { clusterPoints(points = points, zoom = zoom) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // If the user starts a new pinch mid snap-back, cancel
                    // the animation and pick up from the current scale so
                    // the visual doesn't jump.
                    snapBackJob.value?.cancel()
                    snapBackJob.value = null

                    var pastTouchSlop = false
                    var slopZoom = 1f
                    var slopPan = Offset.Zero
                    // Freeze the integer zoom for the whole gesture. Tiles
                    // and clusters stay at this level until the user lifts —
                    // mirrors Leaflet's behaviour in the PWA where markers
                    // don't reshuffle until release, so the pinch feels
                    // continuous instead of stepped.
                    val startZoom = currentZoom
                    var localScale = pinchScale
                    var lastCentroidPx = Offset.Zero
                    var hasCentroid = false

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (canceled) break

                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (!pastTouchSlop) {
                            slopZoom *= zoomChange
                            slopPan += panChange
                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                            val zoomMotion = abs(1f - slopZoom) * centroidSize
                            val panMotion = slopPan.getDistance()
                            if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                pastTouchSlop = true
                            }
                        }

                        if (!pastTouchSlop) continue

                        val centroidPx = event.calculateCentroid(useCurrent = true)
                        // The pinch pivot is only meaningful while two
                        // fingers are touching — `calculateCentroid` collapses
                        // to a single-finger position the moment one finger
                        // lifts, which would yank `lastCentroidPx` away from
                        // the real pinch midpoint and skew the commit math.
                        // Freezing it while sharedPressed < 2 keeps the
                        // pivot anchored at the last two-finger position.
                        val sharedPressedCount =
                            event.changes.count { it.pressed && it.previousPressed }
                        val isPinching = sharedPressedCount >= 2

                        // Pan during the gesture is cheap (just shifts the
                        // viewport — no clustering recompute) so we apply it
                        // immediately for finger-tracking feel.
                        if (panChange != Offset.Zero) {
                            val centerW = project(
                                LatLng(currentCenterLat, currentCenterLng), startZoom
                            )
                            val unproj = unproject(
                                WorldPx(centerW.x - panChange.x, centerW.y - panChange.y),
                                startZoom
                            )
                            currentOnCenterChanged(unproj.latitude, unproj.longitude)
                        }

                        if (isPinching && centroidPx.isSpecified && size != IntSize.Zero) {
                            lastCentroidPx = centroidPx
                            hasCentroid = true
                            pinchPivot = Offset(
                                centroidPx.x / size.width.coerceAtLeast(1),
                                centroidPx.y / size.height.coerceAtLeast(1)
                            )

                            // Zoom is purely visual — the integer level
                            // doesn't change until release, so the layer
                            // stretches continuously across multiple zoom
                            // steps.
                            if (zoomChange != 1f) {
                                localScale *= zoomChange
                                val maxScale = 2f.pow((MAX_ZOOM - startZoom).coerceAtLeast(0))
                                val minScale = 2f.pow((MIN_ZOOM - startZoom).coerceAtMost(0))
                                localScale = localScale.coerceIn(minScale, maxScale)
                                pinchScale = localScale
                            }
                        }

                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })

                    // Now that the gesture is over, decide the final integer
                    // zoom and shift the center so the pivot's geographic
                    // point lands at the same screen pixel under the new
                    // zoom. This is the *only* point where clusters and
                    // tiles re-evaluate.
                    val rawDelta = if (localScale > 0f) log2(localScale).roundToInt() else 0
                    val newZoom = (startZoom + rawDelta).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val zoomDelta = newZoom - startZoom

                    if (zoomDelta != 0 && hasCentroid && size != IntSize.Zero) {
                        val centerW = project(
                            LatLng(currentCenterLat, currentCenterLng), startZoom
                        )
                        val offsetX = lastCentroidPx.x - size.width / 2.0
                        val offsetY = lastCentroidPx.y - size.height / 2.0
                        val scaleFactor = 2.0.pow(zoomDelta)
                        val newCenterW = WorldPx(
                            x = scaleFactor * centerW.x + (scaleFactor - 1.0) * offsetX,
                            y = scaleFactor * centerW.y + (scaleFactor - 1.0) * offsetY
                        )
                        val newLatLng = unproject(newCenterW, newZoom)
                        currentOnCenterChanged(newLatLng.latitude, newLatLng.longitude)
                        currentOnZoomChanged(newZoom)
                    }

                    // Residual visual scale that keeps the rendered map
                    // continuous across the integer-zoom step. Animate it
                    // back to 1f so the final composition lands at pixel
                    // alignment with the new tiles.
                    val residual = localScale / 2f.pow(zoomDelta)
                    pinchScale = residual
                    if (residual != 1f) {
                        snapBackJob.value = scope.launch {
                            val anim = Animatable(residual)
                            anim.animateTo(1f, animationSpec = tween(120)) {
                                pinchScale = value
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val z = currentZoom
                        if (z < MAX_ZOOM) {
                            val center = project(LatLng(currentCenterLat, currentCenterLng), z)
                            val tapWorld = WorldPx(
                                x = center.x + (tap.x - size.width / 2.0),
                                y = center.y + (tap.y - size.height / 2.0)
                            )
                            val newPoint = unproject(tapWorld, z)
                            currentOnCenterChanged(newPoint.latitude, newPoint.longitude)
                            currentOnZoomChanged(z + 1)
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = pinchScale
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(pinchPivot.x, pinchPivot.y)
                }
        ) {
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
                        model = tileTemplate
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
