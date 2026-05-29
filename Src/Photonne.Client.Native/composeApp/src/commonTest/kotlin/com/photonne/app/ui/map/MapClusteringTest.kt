package com.photonne.app.ui.map

import com.photonne.app.data.models.MapPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapClusteringTest {

    private fun point(id: String, lat: Double, lng: Double): MapPoint = MapPoint(
        id = id,
        latitude = lat,
        longitude = lng,
        hasThumbnail = true,
        date = Instant.parse("2026-01-01T00:00:00Z")
    )

    @Test
    fun nearby_points_at_low_zoom_collapse_into_one_cluster() {
        val points = listOf(
            point("a", 41.380, 2.170),
            point("b", 41.381, 2.171),
            point("c", 41.382, 2.169)
        )
        val markers = clusterPoints(points, zoom = 4)
        assertEquals(1, markers.size)
        assertEquals(3, markers.first().count)
    }

    @Test
    fun far_apart_points_stay_separate() {
        // Intercontinental triangle — even at low zoom and a generous
        // clusterRadius these are thousands of world-pixels apart so
        // they must never collapse. Kept far enough that the test
        // survives future radius bumps without revisiting the fixture.
        val markers = clusterPoints(
            points = listOf(
                point("a", 40.71, -74.00),  // New York
                point("b", 35.68, 139.65),  // Tokyo
                point("c", -33.86, 151.21)  // Sydney
            ),
            zoom = 5
        )
        assertEquals(3, markers.size)
        assertTrue(markers.all { it.count == 1 })
    }

    @Test
    fun clusters_split_when_zoom_increases_with_explicit_small_radius() {
        // Pin the radius so the assertion isn't sensitive to the
        // production default (currently 300 px). With radius 80, two
        // points 0.020° apart in lng end up ~230 px at zoom 14, so
        // they split — but only ~58 px at zoom 12, where they merge.
        val points = listOf(
            point("a", 41.380, 2.170),
            point("b", 41.395, 2.150)
        )
        val zoomedOut = clusterPoints(points, zoom = 6, radiusPx = 80.0)
        val zoomedIn = clusterPoints(points, zoom = 14, radiusPx = 80.0)
        assertEquals(1, zoomedOut.size)
        assertEquals(2, zoomedIn.size)
    }

    @Test
    fun merge_pass_eliminates_overlapping_clusters() {
        // Five points laid out so the greedy single-pass would leave
        // two separate buckets ~75 px apart (their centroids drift
        // inside the 80 px radius after a few absorptions, but only
        // the merge pass catches that and combines them).
        val points = listOf(
            point("a", 41.380, 2.170),
            point("b", 41.380, 2.171),
            point("c", 41.381, 2.171),
            point("d", 41.381, 2.172),
            point("e", 41.382, 2.172)
        )
        val markers = clusterPoints(points, zoom = 14, radiusPx = 80.0)
        assertEquals(1, markers.size, "expected one merged cluster, got ${markers.size}: $markers")
        assertEquals(5, markers.first().count)
    }

    @Test
    fun no_two_clusters_end_within_radius() {
        // Sanity check across a denser layout: whatever the greedy
        // order produces, the post-merge pass must guarantee that
        // every pair of final clusters is at least `radiusPx` apart.
        val points = (0 until 30).map { i ->
            val rowLat = 41.380 + (i / 6) * 0.0005
            val colLng = 2.170 + (i % 6) * 0.0005
            point("p$i", rowLat, colLng)
        }
        val markers = clusterPoints(points, zoom = 13, radiusPx = 80.0)
        val r = 80.0
        for (i in 0 until markers.size - 1) {
            val mi = markers[i]
            for (j in (i + 1) until markers.size) {
                val mj = markers[j]
                val dx = mi.worldX - mj.worldX
                val dy = mi.worldY - mj.worldY
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                assertTrue(d >= r, "clusters $i and $j only $d px apart (< $r)")
            }
        }
    }

    @Test
    fun disable_at_max_zoom_keeps_every_point_separate() {
        val points = listOf(
            point("a", 41.380, 2.170),
            point("b", 41.380001, 2.170001) // almost identical
        )
        val markers = clusterPoints(points, zoom = 18, disableAtZoom = 18)
        assertEquals(2, markers.size)
    }
}
