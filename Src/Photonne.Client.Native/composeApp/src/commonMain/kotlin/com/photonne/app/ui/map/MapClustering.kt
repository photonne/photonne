package com.photonne.app.ui.map

import com.photonne.app.data.models.MapPoint

/**
 * One marker that the map composable should render. May represent a
 * single asset (`points.size == 1`) or a group of nearby assets. The
 * `(x, y)` are the world-pixel coordinates at the current zoom — the
 * composable converts them into screen offsets after subtracting the
 * viewport origin.
 */
data class MapMarker(
    val id: String,
    val worldX: Double,
    val worldY: Double,
    val points: List<MapPoint>
) {
    val count: Int get() = points.size
    val firstPoint: MapPoint get() = points.first()
}

/**
 * Greedy screen-space clusterer that mirrors the PWA's
 * Leaflet.markercluster behaviour: for each point we look up the cluster
 * whose centroid is closest in pixel distance and merge if it sits
 * within [radiusPx]; otherwise we open a new cluster.
 *
 * The grid index is a flat HashMap keyed by `cellX:cellY`, with each
 * cell holding the indices of clusters whose centroid falls inside it.
 * That keeps the per-point search bounded to a 3x3 neighbourhood, so
 * the algorithm is roughly O(n) for the typical case where
 * `radiusPx ≈ tile spacing`.
 */
fun clusterPoints(
    points: List<MapPoint>,
    zoom: Int,
    radiusPx: Double = 80.0,
    disableAtZoom: Int = 18
): List<MapMarker> {
    if (points.isEmpty()) return emptyList()
    val cellSize = radiusPx
    val noClustering = zoom >= disableAtZoom

    data class Bucket(
        var sumX: Double,
        var sumY: Double,
        val items: MutableList<MapPoint>
    )

    val buckets = mutableListOf<Bucket>()
    val grid = HashMap<Long, MutableList<Int>>()

    fun cellKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) or (cy.toLong() and 0xFFFFFFFFL)

    for (point in points) {
        val px = lonToWorldX(point.longitude, zoom)
        val py = latToWorldY(point.latitude, zoom)

        if (noClustering) {
            buckets += Bucket(px, py, mutableListOf(point))
            continue
        }

        val cx = (px / cellSize).toInt()
        val cy = (py / cellSize).toInt()

        var bestIndex = -1
        var bestDistanceSq = radiusPx * radiusPx
        for (dx in -1..1) {
            for (dy in -1..1) {
                val cell = grid[cellKey(cx + dx, cy + dy)] ?: continue
                for (idx in cell) {
                    val bucket = buckets[idx]
                    val centroidX = bucket.sumX / bucket.items.size
                    val centroidY = bucket.sumY / bucket.items.size
                    val ddx = centroidX - px
                    val ddy = centroidY - py
                    val d2 = ddx * ddx + ddy * ddy
                    if (d2 < bestDistanceSq) {
                        bestDistanceSq = d2
                        bestIndex = idx
                    }
                }
            }
        }

        if (bestIndex >= 0) {
            val bucket = buckets[bestIndex]
            bucket.items += point
            bucket.sumX += px
            bucket.sumY += py
        } else {
            val bucket = Bucket(px, py, mutableListOf(point))
            buckets += bucket
            val newIndex = buckets.size - 1
            grid.getOrPut(cellKey(cx, cy)) { mutableListOf() } += newIndex
        }
    }

    return buckets.map { bucket ->
        val centroidX = bucket.sumX / bucket.items.size
        val centroidY = bucket.sumY / bucket.items.size
        // Build a stable id from the cluster's first asset id + size so
        // Compose can keep the marker key across recompositions without
        // recreating the AsyncImage when the centroid drifts a pixel.
        val id = bucket.items.first().id + ":" + bucket.items.size
        MapMarker(
            id = id,
            worldX = centroidX,
            worldY = centroidY,
            points = bucket.items
        )
    }
}
