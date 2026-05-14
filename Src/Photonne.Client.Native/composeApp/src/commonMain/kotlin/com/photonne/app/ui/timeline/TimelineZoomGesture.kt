package com.photonne.app.ui.timeline

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Pinch-only gesture detector for the Timeline grid. Mirrors
 * `detectPinchAndPanGestures` from `ZoomablePagerImage` but:
 *
 * - Ignores single-finger drags entirely so the LazyVerticalGrid keeps
 *   its vertical scroll and pull-to-refresh keeps working.
 * - Only activates once the cumulative zoom motion has crossed the
 *   platform touch slop, so accidental two-finger pans don't claim the
 *   gesture.
 * - Reports start / continuous / end callbacks so the caller can lerp
 *   cell size during the gesture and snap to a discrete level on
 *   release.
 *
 * The reported [onZoom] / [onZoomEnd] zoom factor is cumulative from
 * the start of the gesture (1.0 == no change), not per-event.
 */
suspend fun PointerInputScope.detectTimelinePinch(
    onZoomStart: () -> Unit,
    onZoom: (cumulativeZoom: Float) -> Unit,
    onZoomEnd: (finalZoom: Float) -> Unit
) {
    awaitEachGesture {
        var cumulativeZoom = 1f
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        // Listen on the Initial pass so we see the events before the
        // LazyVerticalGrid's scrollable does — otherwise its vertical-drag
        // detector would consume the first pointer's y-axis change and
        // make this detector bail on `isConsumed`. We only consume here
        // once the user has clearly committed to a pinch (zoom motion
        // past touch slop); until then events pass through to scroll.
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount < 2) {
                // Single-finger gestures belong to the grid's scroll.
                continue
            }

            val zoomChange = event.calculateZoom()
            cumulativeZoom *= zoomChange

            if (!pastTouchSlop) {
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1f - cumulativeZoom) * centroidSize
                if (zoomMotion > touchSlop) {
                    pastTouchSlop = true
                    onZoomStart()
                    onZoom(cumulativeZoom)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } else if (zoomChange != 1f) {
                onZoom(cumulativeZoom)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })

        if (pastTouchSlop) onZoomEnd(cumulativeZoom)
    }
}
