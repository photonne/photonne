package com.photonne.app.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Density
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Min/max zoom we ever request from the tile server. CARTO and OSM
 * both go up to 19; we stay one short so re-pinching past max never
 * blanks out. */
const val MIN_ZOOM = 1
const val MAX_ZOOM = 18

/** Dark CARTO basemap, same as the PWA's default. */
private const val TILE_URL_TEMPLATE_DARK =
    "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"

/** Light CARTO basemap, used when the host theme is in light mode. */
private const val TILE_URL_TEMPLATE_LIGHT =
    "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"

private val MarkerBorderColor = Color(0xFFFFD166)
private val ClusterBadgeColor = Color(0xFFF44336)
private val MapBackgroundDark = Color(0xFF1A1A1A)
private val MapBackgroundLight = Color(0xFFE6E2DA)

/** How long the previous tile pyramid sticks around as a full-opacity
 * backdrop after a zoom commit. Long enough that Coil has warmed the
 * new zoom's cache before we drop the fallback. */
private const val BACKDROP_HOLD_MS = 1500

/** Duration of the pinchScale → 1f settle animation. Matched with the
 * cluster animation in AnimatedMarkers.kt so both motions start and
 * end together — the unified transition masks the discrete "click"
 * (cluster recompute + tile zoom swap) the user otherwise perceives
 * when the integer-zoom threshold gets crossed. */
private const val PINCH_RESET_MS = 450

/**
 * Interactive CARTO tile viewer with pinch-to-zoom and drag-to-pan,
 * plus an overlay of clickable thumbnail markers.
 *
 * Zoom UX: the integer level the parent owns commits at pinch release.
 * Until then the tile layer is stretched continuously by `pinchScale`;
 * at release the residual is glided back to 1f over 300 ms (Leaflet's
 * settle motion). The previous tile pyramid stays rendered as a
 * full-opacity backdrop while Coil warms the new zoom's cache so the
 * swap never goes dark.
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
    // pan. Reading through `rememberUpdatedState` lets the same
    // long-lived lambda see fresh state on every event without
    // rebuilding the gesture detector.
    val currentCenterLat by rememberUpdatedState(centerLat)
    val currentCenterLng by rememberUpdatedState(centerLng)
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    // Visual scale + pivot. Lives at `1f` while the user is idle on an
    // integer zoom; goes to the live pinch ratio during a gesture;
    // on release a PINCH_RESET_MS glide carries it back to 1f.
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var pinchPivot by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    // Two-stage backdrop tracking. `effectiveBackdrop` is derived in
    // composition so the backdrop is visible from the FIRST frame at
    // the new zoom — the LaunchedEffect runs after composition, so
    // relying on it alone would let the new (cold-cache) layer render
    // alone for one frame.
    var renderedZoom by remember { mutableStateOf(zoom) }
    var backdropZoom by remember { mutableStateOf<Int?>(null) }
    var pinchScaleSetByGesture by remember { mutableStateOf(false) }
    val pinchResetJob = remember { mutableStateOf<Job?>(null) }

    val effectiveBackdrop: Int? = when {
        renderedZoom != zoom -> renderedZoom
        backdropZoom != null && backdropZoom != zoom -> backdropZoom
        else -> null
    }

    LaunchedEffect(zoom) {
        if (renderedZoom == zoom) return@LaunchedEffect
        val previous = renderedZoom
        renderedZoom = zoom
        val wasGesture = pinchScaleSetByGesture
        pinchScaleSetByGesture = false
        // Programmatic zoom (FAB +/-, fitToData, double-tap) glides
        // the residual back to 1f in parallel with the tile swap.
        if (!wasGesture && pinchScale != 1f) {
            pinchResetJob.value?.cancel()
            pinchResetJob.value = scope.launch {
                val anim = Animatable(pinchScale)
                anim.animateTo(1f, tween(PINCH_RESET_MS, easing = EaseIn)) {
                    pinchScale = value
                }
                pinchResetJob.value = null
            }
        }
        backdropZoom = previous
        delay(BACKDROP_HOLD_MS.toLong())
        backdropZoom = null
    }

    val rawMarkers by remember(points, zoom) {
        derivedStateOf { clusterPoints(points = points, zoom = zoom) }
    }
    val markers = rememberAnimatedMarkers(rawMarkers, zoom)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    // Cancel any in-flight pinch-reset animation so the
                    // new gesture picks up from the actual current
                    // value without competing writes to pinchScale.
                    pinchResetJob.value?.cancel()
                    pinchResetJob.value = null

                    var pastTouchSlop = false
                    var slopZoom = 1f
                    var slopPan = Offset.Zero
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
                        val sharedPressedCount =
                            event.changes.count { it.pressed && it.previousPressed }
                        val isPinching = sharedPressedCount >= 2

                        // Pan: `panChange.x` is screen pixels, the
                        // world moves `/ localScale` to keep the
                        // finger 1:1 with the content under a
                        // fractional residual.
                        if (panChange != Offset.Zero && localScale > 0f) {
                            val centerW = project(
                                LatLng(currentCenterLat, currentCenterLng), startZoom
                            )
                            val unproj = unproject(
                                WorldPx(
                                    centerW.x - panChange.x / localScale,
                                    centerW.y - panChange.y / localScale
                                ),
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
                        // Tell the LaunchedEffect to skip its own
                        // snap-back — the gesture handler launches the
                        // same animation below so the settle starts
                        // immediately rather than waiting for the
                        // LaunchedEffect-zoom-change tick.
                        pinchScaleSetByGesture = true
                        currentOnCenterChanged(newLatLng.latitude, newLatLng.longitude)
                        currentOnZoomChanged(newZoom)
                    }

                    val residual = localScale / 2f.pow(zoomDelta)
                    pinchScale = residual

                    // Glide the residual back to 1f so the integer
                    // zoom commit lands at a crisp natural scale.
                    if (pinchScale != 1f) {
                        pinchResetJob.value?.cancel()
                        pinchResetJob.value = scope.launch {
                            val anim = Animatable(pinchScale)
                            anim.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(
                                    durationMillis = PINCH_RESET_MS,
                                    easing = EaseIn
                                )
                            ) { pinchScale = value }
                            pinchResetJob.value = null
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        val z = currentZoom
                        if (z < MAX_ZOOM) {
                            // Inverse-transform the tap through the
                            // active pinchScale + pivot to find the
                            // world point under the finger.
                            val s = pinchScale.toDouble().coerceAtLeast(0.0001)
                            val pivotX = pinchPivot.x * size.width
                            val pivotY = pinchPivot.y * size.height
                            val localX = pivotX + (tap.x - pivotX) / s
                            val localY = pivotY + (tap.y - pivotY) / s
                            val center = project(LatLng(currentCenterLat, currentCenterLng), z)
                            val tapWorld = WorldPx(
                                x = center.x + (localX - size.width / 2.0),
                                y = center.y + (localY - size.height / 2.0)
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

        // Backdrop tile pyramid (previous zoom level). Always at full
        // opacity — sits below the new layer and fills any tile gap
        // until Coil delivers the new ones, so the user never sees
        // dark areas during a zoom transition. Reading
        // `effectiveBackdrop` (computed above) instead of
        // `backdropZoom` ensures visibility from the first commit
        // frame, not delayed one frame for the LaunchedEffect.
        val backdrop = effectiveBackdrop
        if (backdrop != null) {
            TileLayer(
                tileZoom = backdrop,
                centerLat = centerLat,
                centerLng = centerLng,
                size = size,
                tileTemplate = tileTemplate,
                density = density,
                scale = pinchScale * 2f.pow(zoom - backdrop),
                pivot = pinchPivot,
                alpha = 1f
            )
        }

        // Current tile layer on top — transparent where tiles are
        // still loading, so the backdrop shows through those gaps.
        TileLayer(
            tileZoom = zoom,
            centerLat = centerLat,
            centerLng = centerLng,
            size = size,
            tileTemplate = tileTemplate,
            density = density,
            scale = pinchScale,
            pivot = pinchPivot,
            alpha = 1f
        )

        // Markers are NOT inside the pinchScale graphicsLayer — they
        // stay at a constant on-screen size and their positions are
        // composed manually with the same transform the tile layers
        // apply. Keeping them inside the transform would shrink them
        // ~30 % on zoom-in commit (the moment pinchScale drops from
        // localScale to localScale/2) which the user perceives as a
        // jolt even though the tile content stays put.
        val center = project(LatLng(centerLat, centerLng), zoom)
        val viewportLeftWorldX = center.x - size.width / 2.0
        val viewportTopWorldY = center.y - size.height / 2.0
        val pivotX = pinchPivot.x * size.width.toDouble()
        val pivotY = pinchPivot.y * size.height.toDouble()
        val sd = pinchScale.toDouble()
        val markerPaddingPx = 160

        for (animated in markers) {
            val cluster = animated.marker
            val localX = animated.worldX - viewportLeftWorldX
            val localY = animated.worldY - viewportTopWorldY
            val screenX = (pivotX + (localX - pivotX) * sd).roundToInt()
            val screenY = (pivotY + (localY - pivotY) * sd).roundToInt()
            if (screenX < -markerPaddingPx || screenX > size.width + markerPaddingPx) continue
            if (screenY < -markerPaddingPx || screenY > size.height + markerPaddingPx) continue
            ThumbnailMarker(
                marker = cluster,
                baseUrl = baseUrl,
                offsetPx = IntOffset(screenX, screenY),
                alpha = animated.alpha,
                // Ghosts mid-fade aren't real targets — gating clicks
                // at half-opacity stops a tap from landing on a
                // marker the user can barely see.
                interactive = animated.alpha >= 0.5f,
                onClick = {
                    if (cluster.count == 1) onPointClick(cluster.firstPoint)
                    else onClusterClick(cluster.points)
                }
            )
        }
    }
}

/**
 * Renders one CARTO tile pyramid level inside its own transformed Box.
 * Used twice during a zoom transition: once at the new [tileZoom] (the
 * commit target) and once at the previously-displayed zoom whose layer
 * is the backdrop. The `scale` parameter combines `pinchScale` with the
 * extra `2^Δz` factor needed to align an old tile pyramid against the
 * new one at the same on-screen world area.
 */
@Composable
private fun TileLayer(
    tileZoom: Int,
    centerLat: Double,
    centerLng: Double,
    size: IntSize,
    tileTemplate: String,
    density: Density,
    scale: Float,
    pivot: Offset,
    alpha: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(pivot.x, pivot.y)
                this.alpha = alpha
            }
    ) {
        val center = project(LatLng(centerLat, centerLng), tileZoom)
        val viewportLeftWorldX = center.x - size.width / 2.0
        val viewportTopWorldY = center.y - size.height / 2.0

        // Inverse-transform the screen rect to find which slice of the
        // box's local coords ends up visible after the graphicsLayer
        // applies `scale` around `pivot`. With `scale < 1` this rect
        // is wider than the screen, so the loop renders extra tiles
        // beyond the box boundary; the graphicsLayer scales them into
        // view.
        val invS = 1.0 / scale.toDouble().coerceAtLeast(0.0001)
        val pivotX = pivot.x * size.width
        val pivotY = pivot.y * size.height
        val visibleLeft = pivotX + (0.0 - pivotX) * invS
        val visibleTop = pivotY + (0.0 - pivotY) * invS
        val visibleRight = pivotX + (size.width - pivotX) * invS
        val visibleBottom = pivotY + (size.height - pivotY) * invS

        val firstTileX = floor((viewportLeftWorldX + visibleLeft) / TILE_SIZE_PX).toInt()
        val firstTileY = floor((viewportTopWorldY + visibleTop) / TILE_SIZE_PX).toInt()
        val lastTileX = floor((viewportLeftWorldX + visibleRight) / TILE_SIZE_PX).toInt()
        val lastTileY = floor((viewportTopWorldY + visibleBottom) / TILE_SIZE_PX).toInt()
        val tileMax = (1L shl tileZoom).toInt()
        val tileSizeDp = with(density) { TILE_SIZE_PX.toDp() }

        for (tileY in firstTileY..lastTileY) {
            if (tileY < 0 || tileY >= tileMax) continue
            for (tileX in firstTileX..lastTileX) {
                val wrappedX = ((tileX % tileMax) + tileMax) % tileMax
                val tilePxX = (tileX * TILE_SIZE_PX - viewportLeftWorldX).roundToInt()
                val tilePxY = (tileY * TILE_SIZE_PX - viewportTopWorldY).roundToInt()
                val subdomain = "abcd"[(((tileX + tileY) % 4) + 4) % 4]
                AsyncImage(
                    model = tileTemplate
                        .replace("{s}", subdomain.toString())
                        .replace("{z}", tileZoom.toString())
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
    }
}

@Composable
private fun ThumbnailMarker(
    marker: MapMarker,
    baseUrl: String,
    offsetPx: IntOffset,
    onClick: () -> Unit,
    alpha: Float = 1f,
    interactive: Boolean = true
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
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (interactive) Modifier.pointerInput(marker.id) {
                    detectTapGestures(onTap = { onClick() })
                } else Modifier
            )
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
