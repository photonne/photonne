package com.photonne.app.ui.asset

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlin.math.abs

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Image that supports pinch-to-zoom, pan and double-tap to toggle
 * between 1x and 2.5x. While [scale] is greater than 1 the parent
 * pager should disable user scroll so horizontal pan is not
 * intercepted — the caller observes the scale via [onScaleChange].
 */
@Composable
fun ZoomablePagerImage(
    model: Any?,
    contentDescription: String?,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    onTap: (() -> Unit)? = null
) {
    var scale by remember { mutableStateOf(MIN_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) { onScaleChange(scale) }

    fun clampOffset(target: Offset, currentScale: Float): Offset {
        if (currentScale <= 1f || size == IntSize.Zero) return Offset.Zero
        val maxX = (size.width * (currentScale - 1f)) / 2f
        val maxY = (size.height * (currentScale - 1f)) / 2f
        return Offset(
            x = target.x.coerceIn(-maxX, maxX),
            y = target.y.coerceIn(-maxY, maxY)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(onTap, zoomEnabled) {
                detectTapGestures(
                    onTap = onTap?.let { { _ -> it() } },
                    // Double-tap zoom only when zooming is enabled (it's disabled
                    // while the asset is acting as the info header/left pane).
                    onDoubleTap = if (!zoomEnabled) null else { tap ->
                        if (scale > 1f) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            // Zoom toward the tapped point (not the centre): shift
                            // the content so the pixel under the finger stays put.
                            // For centre-anchored scaling, the required translation
                            // is (centre - tap) * (scale - 1).
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val target = DOUBLE_TAP_SCALE
                            scale = target
                            offset = clampOffset((center - tap) * (target - 1f), currentScale = target)
                        }
                    }
                )
            }
            .then(
                if (zoomEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectPinchAndPanGestures(isZoomed = { scale > 1f }) { pan, zoom ->
                            val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                            val newOffset = if (newScale > 1f) clampOffset(offset + pan, newScale) else Offset.Zero
                            scale = newScale
                            offset = newOffset
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

// Like detectTransformGestures, but doesn't claim single-finger pan while at
// the resting scale — that lets the parent HorizontalPager keep handling
// horizontal swipes between assets. We only take over once the user pinches
// or once we're already zoomed in.
private suspend fun PointerInputScope.detectPinchAndPanGestures(
    isZoomed: () -> Boolean,
    onGesture: (pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (canceled) break

            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            val pointerCount = event.changes.count { it.pressed }

            if (!pastTouchSlop) {
                zoom *= zoomChange
                pan += panChange

                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1 - zoom) * centroidSize
                val panMotion = pan.getDistance()

                val activate = zoomMotion > touchSlop ||
                    (pointerCount > 1 && panMotion > touchSlop) ||
                    (isZoomed() && panMotion > touchSlop)
                if (activate) pastTouchSlop = true
            }

            if (pastTouchSlop) {
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    onGesture(panChange, zoomChange)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
