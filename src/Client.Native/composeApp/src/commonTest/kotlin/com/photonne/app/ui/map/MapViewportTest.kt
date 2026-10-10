package com.photonne.app.ui.map

import com.photonne.app.data.models.MapPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MapViewportTest {

    private fun point(id: String, lat: Double, lng: Double, date: String = "2026-01-01T00:00:00Z") =
        MapPoint(
            id = id,
            latitude = lat,
            longitude = lng,
            hasThumbnail = true,
            date = Instant.parse(date)
        )

    @Test
    fun viewport_keeps_only_points_inside_the_rectangle() {
        val points = listOf(
            point("bcn", 41.3874, 2.1686),
            point("bcn2", 41.3900, 2.1700),
            point("madrid", 40.4168, -3.7038)
        )
        val visible = pointsInViewport(
            points = points,
            centerLat = 41.3874,
            centerLng = 2.1686,
            zoom = 12,
            widthPx = 1080,
            heightPx = 1920
        )
        assertEquals(setOf("bcn", "bcn2"), visible.map { it.id }.toSet())
    }

    @Test
    fun viewport_is_sorted_newest_first_with_stable_ties() {
        val points = listOf(
            point("old", 41.38, 2.16, "2020-01-01T00:00:00Z"),
            point("b", 41.38, 2.16, "2025-06-01T00:00:00Z"),
            point("a", 41.38, 2.16, "2025-06-01T00:00:00Z"),
            point("new", 41.38, 2.16, "2026-03-01T00:00:00Z")
        )
        val visible = pointsInViewport(points, 41.38, 2.16, 10, 800, 800)
        assertEquals(listOf("new", "a", "b", "old"), visible.map { it.id })
    }

    @Test
    fun viewport_without_measured_size_is_empty() {
        val points = listOf(point("a", 0.0, 0.0))
        assertTrue(pointsInViewport(points, 0.0, 0.0, 5, 0, 0).isEmpty())
    }

    @Test
    fun zooming_out_widens_the_viewport() {
        val points = listOf(point("bcn", 41.3874, 2.1686), point("madrid", 40.4168, -3.7038))
        val close = pointsInViewport(points, 41.3874, 2.1686, 12, 1080, 1920)
        val far = pointsInViewport(points, 41.3874, 2.1686, 5, 1080, 1920)
        assertEquals(1, close.size)
        assertEquals(2, far.size)
    }

    @Test
    fun anchors_are_ordered_top_to_bottom() {
        val anchors = mapSheetAnchorPositions(
            heightPx = 2000f,
            expandedTopPx = 200f
        )
        val expanded = assertNotNull(anchors[MapSheetValue.Expanded])
        val half = assertNotNull(anchors[MapSheetValue.Half])
        val hidden = assertNotNull(anchors[MapSheetValue.Hidden])
        assertEquals(2000f, hidden)
        assertEquals(200f, expanded)
        assertEquals(2000f * (1f - MAP_SHEET_HALF_FRACTION), half)
        assertTrue(expanded < half && half < hidden)
    }

    @Test
    fun half_anchor_is_dropped_when_it_does_not_fit() {
        // Ventana muy baja: media altura quedaría pegada a la hoja asomada.
        val anchors = mapSheetAnchorPositions(
            heightPx = 500f,
            expandedTopPx = 240f
        )
        assertFalse(MapSheetValue.Half in anchors)
        assertTrue(MapSheetValue.Hidden in anchors)
        assertTrue(MapSheetValue.Expanded in anchors)
    }

    @Test
    fun cluster_center_lands_in_the_middle_of_the_visible_gap() {
        val cluster = listOf(point("a", 41.38, 2.16), point("b", 41.40, 2.18))
        val zoom = 12
        val mapHeight = 2000
        val visibleTop = 200f
        val visibleBottom = 1100f
        val (lat, lng) = assertNotNull(
            centerToRevealCluster(cluster, zoom, mapHeight, visibleTop, visibleBottom)
        )
        // Donde cae el centroide en pantalla con ese centro.
        val centroidY = cluster.sumOf { latToWorldY(it.latitude, zoom) } / cluster.size
        val centroidX = cluster.sumOf { lonToWorldX(it.longitude, zoom) } / cluster.size
        val screenY = centroidY - latToWorldY(lat, zoom) + mapHeight / 2.0
        assertTrue(abs(screenY - (visibleTop + visibleBottom) / 2.0) < 0.5, "screenY=$screenY")
        assertTrue(abs(lonToWorldX(lng, zoom) - centroidX) < 0.5)
    }

    @Test
    fun cluster_center_needs_points_and_size() {
        assertNull(centerToRevealCluster(emptyList(), 10, 1000, 0f, 500f))
        assertNull(centerToRevealCluster(listOf(point("a", 0.0, 0.0)), 10, 0, 0f, 500f))
    }
}
