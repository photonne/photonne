package com.photonne.app.ui.map

import com.photonne.app.data.models.MapPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnimatedMarkersTest {

    private val sample = MapPoint(
        id = "p",
        latitude = 0.0,
        longitude = 0.0,
        hasThumbnail = false,
        date = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun marker(id: String, x: Double, y: Double): MapMarker =
        MapMarker(id = id, worldX = x, worldY = y, points = listOf(sample.copy(id = id)))

    private fun cluster(id: String, x: Double, y: Double, pointIds: List<String>): MapMarker =
        MapMarker(id = id, worldX = x, worldY = y, points = pointIds.map { sample.copy(id = it) })

    @Test
    fun zoom_in_split_explodes_children_from_reprojected_parent() {
        val previous = listOf(marker("parent:3", 100.0, 100.0))
        val children = listOf(
            marker("a:1", 200.0, 195.0),
            marker("b:1", 210.0, 205.0),
            marker("c:1", 190.0, 200.0)
        )

        val out = buildMarkerTransitions(
            previousMarkers = previous,
            previousZoom = 4,
            newMarkers = children,
            newZoom = 5
        )

        assertEquals(4, out.size)
        val explosions = out.filter { it.endAlpha == 1f }
        assertEquals(3, explosions.size)
        for (e in explosions) {
            assertEquals(0f, e.startAlpha)
            assertEquals(200.0, e.startWorldX)
            assertEquals(200.0, e.startWorldY)
            val match = children.first { it.id == e.marker.id }
            assertEquals(match.worldX, e.endWorldX)
            assertEquals(match.worldY, e.endWorldY)
        }

        val ghost = out.single { it.endAlpha == 0f }
        assertEquals("parent:3", ghost.marker.id)
        assertEquals(ghost.startWorldX, ghost.endWorldX)
        assertEquals(ghost.startWorldY, ghost.endWorldY)
        assertEquals(200.0, ghost.startWorldX)
        assertEquals(200.0, ghost.startWorldY)
    }

    @Test
    fun zoom_out_merge_collapses_children_into_new_cluster() {
        val previous = listOf(
            marker("a:1", 200.0, 200.0),
            marker("b:1", 210.0, 205.0),
            marker("c:1", 190.0, 200.0)
        )
        val merged = listOf(marker("merged:3", 100.0, 100.0))

        val out = buildMarkerTransitions(
            previousMarkers = previous,
            previousZoom = 5,
            newMarkers = merged,
            newZoom = 4
        )

        assertEquals(4, out.size)
        val arriving = out.single { it.marker.id == "merged:3" }
        assertEquals(0f, arriving.startAlpha)
        assertEquals(1f, arriving.endAlpha)
        assertEquals(100.0, arriving.endWorldX)
        assertEquals(100.0, arriving.endWorldY)

        val ghosts = out.filter { it.endAlpha == 0f }
        assertEquals(3, ghosts.size)
        for (g in ghosts) {
            assertEquals(1f, g.startAlpha)
            assertEquals(100.0, g.endWorldX)
            assertEquals(100.0, g.endWorldY)
        }
    }

    @Test
    fun identical_sets_at_same_zoom_skip_animation() {
        val set = listOf(
            marker("a:2", 100.0, 100.0),
            marker("b:1", 200.0, 200.0)
        )

        val out = buildMarkerTransitions(
            previousMarkers = set,
            previousZoom = 5,
            newMarkers = set,
            newZoom = 5
        )

        assertTrue(out.isEmpty(), "expected empty transitions, got $out")
    }

    @Test
    fun survivor_interpolates_position_without_alpha_change() {
        val previous = listOf(marker("a:5", 100.0, 100.0))
        val next = listOf(marker("a:5", 110.0, 102.0))

        val out = buildMarkerTransitions(
            previousMarkers = previous,
            previousZoom = 5,
            newMarkers = next,
            newZoom = 5
        )

        assertEquals(1, out.size)
        val tr = out.single()
        assertEquals(1f, tr.startAlpha)
        assertEquals(1f, tr.endAlpha)
        assertEquals(100.0, tr.startWorldX)
        assertEquals(110.0, tr.endWorldX)
    }

    @Test
    fun hybrid_one_survives_one_splits() {
        val previous = listOf(
            marker("a:1", 50.0, 50.0),
            marker("b:4", 200.0, 200.0)
        )
        val next = listOf(
            marker("a:1", 100.0, 100.0),   // re-projected position at zoom 6
            marker("b1:2", 410.0, 400.0),
            marker("b2:2", 390.0, 410.0)
        )

        val out = buildMarkerTransitions(
            previousMarkers = previous,
            previousZoom = 5,
            newMarkers = next,
            newZoom = 6
        )

        assertEquals(4, out.size)

        val survivor = out.single { it.marker.id == "a:1" }
        assertEquals(1f, survivor.startAlpha)
        assertEquals(1f, survivor.endAlpha)
        assertEquals(100.0, survivor.startWorldX)
        assertEquals(100.0, survivor.endWorldX)

        val explosions = out.filter { it.marker.id.startsWith("b") && it.endAlpha == 1f }
        assertEquals(2, explosions.size)
        for (e in explosions) {
            assertEquals(0f, e.startAlpha)
            assertEquals(400.0, e.startWorldX)
            assertEquals(400.0, e.startWorldY)
        }

        val ghost = out.single { it.endAlpha == 0f }
        assertEquals("b:4", ghost.marker.id)
        assertNull(next.firstOrNull { it.id == "b:4" })
    }

    @Test
    fun point_membership_matches_children_beyond_distance_radius() {
        // A wide parent cluster (e.g. one produced by the post-cluster
        // merge pass) can have children at zoom+1 that sit further
        // than matchRadiusPx from the parent's re-projected centroid.
        // A distance-only matcher would orphan those children and they
        // would pop in at full alpha. Membership matching tracks them
        // by their points and still treats the wide parent as source.
        val previous = listOf(
            cluster("big:6", 100.0, 100.0, listOf("a", "b", "c", "d", "e", "f"))
        )
        // Re-projected parent at zoom 6 lives at (200, 200). Children
        // ~200px+ from there in either direction — outside the default
        // matchRadiusPx of 160 — must still find `big:6` as parent via
        // point membership.
        val next = listOf(
            cluster("left:3", 0.0, 200.0, listOf("a", "b", "c")),
            cluster("right:3", 500.0, 200.0, listOf("d", "e", "f"))
        )
        val out = buildMarkerTransitions(
            previousMarkers = previous,
            previousZoom = 5,
            newMarkers = next,
            newZoom = 6
        )
        val left = out.single { it.marker.id == "left:3" }
        val right = out.single { it.marker.id == "right:3" }
        assertEquals(200.0, left.startWorldX)
        assertEquals(200.0, left.startWorldY)
        assertEquals(0f, left.startAlpha)
        assertEquals(200.0, right.startWorldX)
        assertEquals(200.0, right.startWorldY)
        assertEquals(0f, right.startAlpha)
        val ghost = out.single { it.endAlpha == 0f }
        assertEquals("big:6", ghost.marker.id)
        assertEquals(200.0, ghost.startWorldX)
        assertEquals(ghost.startWorldX, ghost.endWorldX)
    }

    @Test
    fun distant_newcomer_with_no_parent_fades_in_place() {
        val previous = listOf(marker("a:1", 100.0, 100.0))
        val next = listOf(
            marker("a:1", 100.0, 100.0),
            marker("far:1", 5000.0, 5000.0)
        )

        val out = buildMarkerTransitions(
            previousMarkers = previous,
            previousZoom = 5,
            newMarkers = next,
            newZoom = 5
        )

        val emerging = out.single { it.marker.id == "far:1" }
        assertEquals(0f, emerging.startAlpha)
        assertEquals(1f, emerging.endAlpha)
        assertEquals(emerging.startWorldX, emerging.endWorldX)
        assertEquals(emerging.startWorldY, emerging.endWorldY)
        assertEquals(5000.0, emerging.endWorldX)
    }

    @Test
    fun both_sides_empty_returns_no_transitions() {
        val out = buildMarkerTransitions(
            previousMarkers = emptyList(),
            previousZoom = 4,
            newMarkers = emptyList(),
            newZoom = 4
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun first_load_from_empty_previous_fades_all_new_markers_in() {
        val next = listOf(
            marker("a:1", 100.0, 100.0),
            marker("b:1", 200.0, 200.0)
        )
        val out = buildMarkerTransitions(
            previousMarkers = emptyList(),
            previousZoom = 5,
            newMarkers = next,
            newZoom = 5
        )
        assertEquals(2, out.size)
        for (tr in out) {
            assertEquals(0f, tr.startAlpha)
            assertEquals(1f, tr.endAlpha)
            assertEquals(tr.startWorldX, tr.endWorldX)
            assertEquals(tr.startWorldY, tr.endWorldY)
        }
        assertNotNull(out.firstOrNull { it.marker.id == "a:1" })
    }
}
