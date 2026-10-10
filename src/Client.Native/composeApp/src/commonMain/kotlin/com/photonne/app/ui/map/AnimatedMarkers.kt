package com.photonne.app.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.pow

/**
 * A cluster marker plus an animated world-pixel position and alpha,
 * used by [OsmMap] to render explosion/collapse transitions when the
 * underlying [clusterPoints] output changes (typically after a zoom
 * commit). The `(worldX, worldY)` are always expressed at the current
 * integer zoom, so the map composable can subtract the viewport origin
 * to get screen pixels without any extra conversion.
 */
data class AnimatedMarker(
    val marker: MapMarker,
    val worldX: Double,
    val worldY: Double,
    val alpha: Float
)

/**
 * The path one marker follows during a transition. `marker` carries the
 * payload that should be rendered (a survivor / newcomer from the new
 * cluster set or a "ghost" from the previous set that's fading out
 * toward its merger).
 */
internal data class MarkerTransition(
    val marker: MapMarker,
    val startWorldX: Double,
    val startWorldY: Double,
    val endWorldX: Double,
    val endWorldY: Double,
    val startAlpha: Float,
    val endAlpha: Float
)

/**
 * Pair each marker in [newMarkers] with its likely "parent" from
 * [previousMarkers] and emit a transition for it; emit ghost
 * transitions for previous markers that have no successor by id.
 *
 * Matching is **point-membership based**: each cluster carries a list
 * of `MapPoint` ids, and a new cluster's parent is the previous cluster
 * that contains the most of its points. This survives any centroid
 * drift after the merge-pass in [clusterPoints], where a cluster's
 * extent can exceed the distance-matching radius. A distance fallback
 * handles the rare orphan case (asset that wasn't in the previous
 * frame at all).
 *
 * Returns an empty list when the previous and new sets are identical
 * AND the zoom is unchanged, so callers can skip the animation entirely.
 */
internal fun buildMarkerTransitions(
    previousMarkers: List<MapMarker>,
    previousZoom: Int,
    newMarkers: List<MapMarker>,
    newZoom: Int,
    matchRadiusPx: Double = 160.0
): List<MarkerTransition> {
    if (previousMarkers.isEmpty() && newMarkers.isEmpty()) return emptyList()

    val zoomDelta = newZoom - previousZoom
    val scale = 2.0.pow(zoomDelta)
    val previousProjected = DoubleArray(previousMarkers.size * 2).also { arr ->
        previousMarkers.forEachIndexed { i, m ->
            arr[i * 2] = m.worldX * scale
            arr[i * 2 + 1] = m.worldY * scale
        }
    }
    val prevIndexById = HashMap<String, Int>(previousMarkers.size).also { map ->
        previousMarkers.forEachIndexed { i, m -> map[m.id] = i }
    }
    val newIndexById = HashMap<String, Int>(newMarkers.size).also { map ->
        newMarkers.forEachIndexed { i, m -> map[m.id] = i }
    }

    // Point-membership index: same asset id can only belong to one
    // cluster at each zoom, so a single map per side lets us look up
    // the containing marker in O(1).
    val pointToPrevIndex = HashMap<String, Int>()
    for (i in previousMarkers.indices) {
        for (p in previousMarkers[i].points) pointToPrevIndex[p.id] = i
    }
    val pointToNewIndex = HashMap<String, Int>()
    for (i in newMarkers.indices) {
        for (p in newMarkers[i].points) pointToNewIndex[p.id] = i
    }

    // Cheap fast-path: same zoom, same set, same positions — no
    // transition work needed.
    if (zoomDelta == 0 && previousMarkers.size == newMarkers.size) {
        var identical = true
        for (p in previousMarkers) {
            val nIdx = newIndexById[p.id]
            if (nIdx == null) { identical = false; break }
            val n = newMarkers[nIdx]
            if (p.worldX != n.worldX || p.worldY != n.worldY) { identical = false; break }
        }
        if (identical) return emptyList()
    }

    val transitions = ArrayList<MarkerTransition>(newMarkers.size + previousMarkers.size)
    val parentClaimedByNew = BooleanArray(previousMarkers.size)
    val matchRadiusSq = matchRadiusPx * matchRadiusPx

    for (newMarker in newMarkers) {
        val survivorIdx = prevIndexById[newMarker.id]
        if (survivorIdx != null) {
            parentClaimedByNew[survivorIdx] = true
            transitions += MarkerTransition(
                marker = newMarker,
                startWorldX = previousProjected[survivorIdx * 2],
                startWorldY = previousProjected[survivorIdx * 2 + 1],
                endWorldX = newMarker.worldX,
                endWorldY = newMarker.worldY,
                startAlpha = 1f,
                endAlpha = 1f
            )
            continue
        }

        // Parent = previous marker containing the most of this marker's
        // points. Counts let two splits from the same parent both find
        // it as their start without one stealing the other's signal.
        var parentIdx = -1
        var parentCount = 0
        val parentCounts = HashMap<Int, Int>()
        for (point in newMarker.points) {
            val idx = pointToPrevIndex[point.id] ?: continue
            val c = (parentCounts[idx] ?: 0) + 1
            parentCounts[idx] = c
            if (c > parentCount) { parentCount = c; parentIdx = idx }
        }

        if (parentIdx >= 0) {
            parentClaimedByNew[parentIdx] = true
            transitions += MarkerTransition(
                marker = newMarker,
                startWorldX = previousProjected[parentIdx * 2],
                startWorldY = previousProjected[parentIdx * 2 + 1],
                endWorldX = newMarker.worldX,
                endWorldY = newMarker.worldY,
                startAlpha = 0f,
                endAlpha = 1f
            )
            continue
        }

        // Fallback for truly new points (asset added between frames):
        // drift in from the nearest old marker if any sit within
        // matchRadiusPx; otherwise fade in place.
        var bestIdx = -1
        var bestDistSq = matchRadiusSq
        for (i in previousMarkers.indices) {
            val dx = previousProjected[i * 2] - newMarker.worldX
            val dy = previousProjected[i * 2 + 1] - newMarker.worldY
            val d2 = dx * dx + dy * dy
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestIdx = i
            }
        }
        if (bestIdx >= 0) {
            parentClaimedByNew[bestIdx] = true
            transitions += MarkerTransition(
                marker = newMarker,
                startWorldX = previousProjected[bestIdx * 2],
                startWorldY = previousProjected[bestIdx * 2 + 1],
                endWorldX = newMarker.worldX,
                endWorldY = newMarker.worldY,
                startAlpha = 0f,
                endAlpha = 1f
            )
        } else {
            transitions += MarkerTransition(
                marker = newMarker,
                startWorldX = newMarker.worldX,
                startWorldY = newMarker.worldY,
                endWorldX = newMarker.worldX,
                endWorldY = newMarker.worldY,
                startAlpha = 0f,
                endAlpha = 1f
            )
        }
    }

    for (i in previousMarkers.indices) {
        val prev = previousMarkers[i]
        if (newIndexById.containsKey(prev.id)) continue

        val startX = previousProjected[i * 2]
        val startY = previousProjected[i * 2 + 1]

        if (parentClaimedByNew[i]) {
            // Previous cluster spawned children that explode outward
            // from this position; fade in place to avoid a duplicate
            // motion track.
            transitions += MarkerTransition(
                marker = prev,
                startWorldX = startX,
                startWorldY = startY,
                endWorldX = startX,
                endWorldY = startY,
                startAlpha = 1f,
                endAlpha = 0f
            )
            continue
        }

        // Merge target = the new marker that absorbed the most of this
        // old marker's points. Same membership logic as the parent
        // lookup above.
        var targetIdx = -1
        var targetCount = 0
        val targetCounts = HashMap<Int, Int>()
        for (point in prev.points) {
            val idx = pointToNewIndex[point.id] ?: continue
            val c = (targetCounts[idx] ?: 0) + 1
            targetCounts[idx] = c
            if (c > targetCount) { targetCount = c; targetIdx = idx }
        }

        val endX: Double
        val endY: Double
        if (targetIdx >= 0) {
            endX = newMarkers[targetIdx].worldX
            endY = newMarkers[targetIdx].worldY
        } else {
            // No shared points (the points were removed from the
            // dataset entirely). Collapse toward the closest visible
            // new marker.
            var bestIdx = -1
            var bestDistSq = Double.MAX_VALUE
            for (j in newMarkers.indices) {
                val dx = newMarkers[j].worldX - startX
                val dy = newMarkers[j].worldY - startY
                val d2 = dx * dx + dy * dy
                if (d2 < bestDistSq) { bestDistSq = d2; bestIdx = j }
            }
            endX = if (bestIdx >= 0) newMarkers[bestIdx].worldX else startX
            endY = if (bestIdx >= 0) newMarkers[bestIdx].worldY else startY
        }
        transitions += MarkerTransition(
            marker = prev,
            startWorldX = startX,
            startWorldY = startY,
            endWorldX = endX,
            endWorldY = endY,
            startAlpha = 1f,
            endAlpha = 0f
        )
    }

    return transitions
}

/**
 * Drives a 0→1 [Animatable] each time [markers] or [zoom] change and
 * exposes the interpolated cluster positions. On the very first
 * emission the markers appear instantly so the map doesn't run an
 * animation on cold-load. Mid-animation cancellation is handled by
 * re-seeding the next transition's start positions from the *currently
 * displayed* frame, so a second pinch never causes a visible jump.
 *
 * The reader also applies a `2^(zoom - snapshot.targetZoom)` correction
 * to handle the one-frame window where the composable is called with a
 * new zoom but the LaunchedEffect hasn't yet updated the snapshot —
 * without it, markers would render in the old zoom's world-pixel space
 * against the new zoom's viewport and fly off-screen for that frame.
 */
@Composable
fun rememberAnimatedMarkers(
    markers: List<MapMarker>,
    zoom: Int,
    // 450 ms with EaseIn (slow at start, accelerating to the end).
    // The slow start matters: it makes the *moment of zoom commit*
    // imperceptible because no marker visibly moves for the first ~80
    // ms — there's no perceptible derivative at t=0 to telegraph that
    // a discrete recompute just fired. The snap-back animation in
    // OsmMap.kt uses the same 450 ms / EaseIn pair so both motions
    // start and end together as one unified transition.
    durationMs: Int = 450
): List<AnimatedMarker> {
    var snapshot by remember { mutableStateOf<MarkerSnapshot?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(markers, zoom) {
        val previous = snapshot
        if (previous == null) {
            snapshot = MarkerSnapshot(
                transitions = markers.map { m ->
                    MarkerTransition(
                        marker = m,
                        startWorldX = m.worldX,
                        startWorldY = m.worldY,
                        endWorldX = m.worldX,
                        endWorldY = m.worldY,
                        startAlpha = 1f,
                        endAlpha = 1f
                    )
                },
                targetZoom = zoom
            )
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        val t = progress.value
        val displayMarkers = ArrayList<MapMarker>(previous.transitions.size)
        val displayAlpha = HashMap<String, Float>(previous.transitions.size)
        for (tr in previous.transitions) {
            val a = lerp(tr.startAlpha, tr.endAlpha, t)
            if (a <= 0.02f) continue
            val cx = lerp(tr.startWorldX, tr.endWorldX, t)
            val cy = lerp(tr.startWorldY, tr.endWorldY, t)
            displayMarkers += MapMarker(
                id = tr.marker.id,
                worldX = cx,
                worldY = cy,
                points = tr.marker.points
            )
            displayAlpha[tr.marker.id] = a
        }

        val rawTransitions = buildMarkerTransitions(
            previousMarkers = displayMarkers,
            previousZoom = previous.targetZoom,
            newMarkers = markers,
            newZoom = zoom
        )

        if (rawTransitions.isEmpty()) {
            snapshot = previous.copy(targetZoom = zoom)
            progress.snapTo(1f)
            return@LaunchedEffect
        }

        // Preserve current opacity for any marker still mid-fade,
        // otherwise newly-emerging children would briefly become opaque
        // before the next animation step had a chance to apply.
        val patched = rawTransitions.map { tr ->
            val current = displayAlpha[tr.marker.id]
            if (current != null && current < tr.startAlpha) tr.copy(startAlpha = current) else tr
        }

        snapshot = MarkerSnapshot(transitions = patched, targetZoom = zoom)
        progress.snapTo(0f)
        // Scale duration with how many integer zoom steps we crossed:
        // delta=0 → baseline, delta=1 → ×1.5, delta=2 → ×2, etc. Bigger
        // commits mean markers have to travel further (4× the distance
        // per +1 of delta), so keeping the duration constant would
        // make a fast multi-step pinch feel like a "swoosh" of motion.
        // Stretching the animation keeps the per-frame velocity in
        // the same ballpark across delta sizes.
        val deltaAbs = kotlin.math.abs(zoom - previous.targetZoom)
        val effectiveDuration = (durationMs * (1.0 + deltaAbs * 0.5)).toInt()
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = effectiveDuration, easing = EaseIn)
        )
    }

    val current = snapshot ?: return emptyList()
    val t = progress.value
    // After a zoom change the snapshot still lives at the previous
    // targetZoom for one frame — the LaunchedEffect runs *after*
    // composition. Without this correction the markers would render in
    // the old zoom's world-pixel space against the new zoom's
    // viewport, displacing them off-screen for that frame and looking
    // like a jump. Once the LaunchedEffect catches up, the correction
    // is 1.0 and this is a no-op.
    val zoomCorrection = 2.0.pow(zoom - current.targetZoom)
    val result = ArrayList<AnimatedMarker>(current.transitions.size)
    for (tr in current.transitions) {
        val a = lerp(tr.startAlpha, tr.endAlpha, t)
        if (a <= 0f) continue
        result += AnimatedMarker(
            marker = tr.marker,
            worldX = lerp(tr.startWorldX, tr.endWorldX, t) * zoomCorrection,
            worldY = lerp(tr.startWorldY, tr.endWorldY, t) * zoomCorrection,
            alpha = a
        )
    }
    return result
}

private data class MarkerSnapshot(
    val transitions: List<MarkerTransition>,
    val targetZoom: Int
)

private fun lerp(start: Double, end: Double, t: Float): Double = start + (end - start) * t
private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
