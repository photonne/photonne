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
        val markers = clusterPoints(
            points = listOf(
                point("a", 41.38, 2.17),   // Barcelona
                point("b", 40.42, -3.70),  // Madrid
                point("c", 48.85, 2.35)    // Paris
            ),
            zoom = 5
        )
        assertEquals(3, markers.size)
        assertTrue(markers.all { it.count == 1 })
    }

    @Test
    fun clusters_split_when_zoom_increases() {
        val points = listOf(
            point("a", 41.380, 2.170),
            point("b", 41.395, 2.150)
        )
        val zoomedOut = clusterPoints(points, zoom = 6)
        val zoomedIn = clusterPoints(points, zoom = 14)
        assertEquals(1, zoomedOut.size)
        assertEquals(2, zoomedIn.size)
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
