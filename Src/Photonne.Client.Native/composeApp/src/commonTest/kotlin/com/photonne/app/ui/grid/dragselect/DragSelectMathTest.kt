package com.photonne.app.ui.grid.dragselect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DragSelectMathTest {

    // ---------- bandOf ----------

    @Test
    fun band_grows_in_either_direction_from_the_anchor() {
        assertEquals(5..9, bandOf(anchor = 5, current = 9))
        assertEquals(2..5, bandOf(anchor = 5, current = 2))
        assertEquals(5..5, bandOf(anchor = 5, current = 5))
    }

    // ---------- bandDiff ----------

    @Test
    fun growing_downwards_only_reports_the_new_tail() {
        val diff = bandDiff(previous = 5..7, next = 5..10)
        assertEquals(listOf(8..10), diff.entering)
        assertEquals(emptyList(), diff.leaving)
    }

    @Test
    fun shrinking_reports_what_left() {
        val diff = bandDiff(previous = 5..10, next = 5..7)
        assertEquals(emptyList(), diff.entering)
        assertEquals(listOf(8..10), diff.leaving)
    }

    @Test
    fun crossing_the_anchor_swaps_sides_in_one_move() {
        // Dragging past the anchor to the other side: the old tail leaves and
        // the new head enters in the same frame.
        val diff = bandDiff(previous = 5..8, next = 2..5)
        assertEquals(listOf(2..4), diff.entering)
        assertEquals(listOf(6..8), diff.leaving)
    }

    @Test
    fun an_unchanged_band_diffs_to_nothing() {
        assertTrue(bandDiff(5..8, 5..8).isEmpty)
    }

    @Test
    fun a_disjoint_jump_swaps_both_ranges_whole() {
        val diff = bandDiff(previous = 0..3, next = 20..25)
        assertEquals(listOf(20..25), diff.entering)
        assertEquals(listOf(0..3), diff.leaving)
    }

    // ---------- columnAt ----------

    @Test
    fun the_gap_after_a_cell_belongs_to_that_cell() {
        // 100px cells, 2px apart: 0..99 is cell 0, and so is the 100..101 gap.
        assertEquals(0, columnAt(x = 0f, cellSizePx = 100f, spacingPx = 2f, columns = 4))
        assertEquals(0, columnAt(x = 99f, cellSizePx = 100f, spacingPx = 2f, columns = 4))
        assertEquals(0, columnAt(x = 101f, cellSizePx = 100f, spacingPx = 2f, columns = 4))
        assertEquals(1, columnAt(x = 102f, cellSizePx = 100f, spacingPx = 2f, columns = 4))
    }

    @Test
    fun a_pointer_outside_the_row_has_no_column() {
        assertEquals(-1, columnAt(x = -5f, cellSizePx = 100f, spacingPx = 2f, columns = 4))
        // Past the last column (4 columns → 0..3).
        assertEquals(-1, columnAt(x = 500f, cellSizePx = 100f, spacingPx = 2f, columns = 4))
        assertEquals(-1, columnAt(x = 10f, cellSizePx = 100f, spacingPx = 2f, columns = 0))
        assertEquals(-1, columnAt(x = 10f, cellSizePx = 0f, spacingPx = 0f, columns = 4))
    }

    @Test
    fun a_single_column_row_maps_everything_to_zero() {
        assertEquals(0, columnAt(x = 0f, cellSizePx = 300f, spacingPx = 2f, columns = 1))
        assertEquals(0, columnAt(x = 299f, cellSizePx = 300f, spacingPx = 2f, columns = 1))
    }

    // ---------- autoScrollVelocity ----------

    @Test
    fun the_middle_of_the_viewport_does_not_scroll() {
        assertEquals(
            0f,
            autoScrollVelocity(y = 500f, topEdge = 0f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1800f)
        )
    }

    @Test
    fun the_edges_scroll_towards_themselves_and_saturate() {
        val up = autoScrollVelocity(y = 10f, topEdge = 0f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1800f)
        val down = autoScrollVelocity(y = 990f, topEdge = 0f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1800f)
        assertTrue(up < 0f, "the top edge must scroll up, was $up")
        assertTrue(down > 0f, "the bottom edge must scroll down, was $down")

        // Dragging past the viewport clamps instead of running away.
        assertEquals(
            -1800f,
            autoScrollVelocity(y = -400f, topEdge = 0f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1800f)
        )
        assertEquals(
            1800f,
            autoScrollVelocity(y = 1400f, topEdge = 0f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1800f)
        )
    }

    @Test
    fun the_ramp_is_gentler_near_the_zone_entrance_than_deep_in_it() {
        // Quadratic easing: brushing the zone must not lurch.
        val shallow = autoScrollVelocity(y = 95f, topEdge = 0f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1000f)
        val deep = autoScrollVelocity(y = 20f, topEdge = 0f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1000f)
        assertTrue(-shallow < 10f, "entering the zone should barely move, was $shallow")
        assertTrue(-deep > -shallow * 10, "deep in the zone should be much faster, was $deep")
    }

    @Test
    fun the_zone_respects_the_useful_edges_not_the_viewport() {
        // A 200px top inset (the solid selection bar) must not make the finger
        // scroll while it is still well inside the visible grid.
        assertEquals(
            0f,
            autoScrollVelocity(y = 350f, topEdge = 200f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1800f)
        )
        assertTrue(
            autoScrollVelocity(y = 250f, topEdge = 200f, bottomEdge = 1000f, zonePx = 100f, maxPxPerSecond = 1800f) < 0f
        )
    }

    @Test
    fun a_short_viewport_still_has_a_neutral_midpoint() {
        // Zones would overlap at 100px each in a 120px viewport; capping them
        // at half the height leaves them meeting exactly at the centre.
        assertEquals(
            0f,
            autoScrollVelocity(y = 60f, topEdge = 0f, bottomEdge = 120f, zonePx = 100f, maxPxPerSecond = 1000f)
        )
        assertTrue(autoScrollVelocity(y = 5f, topEdge = 0f, bottomEdge = 120f, zonePx = 100f, maxPxPerSecond = 1000f) < 0f)
        assertTrue(autoScrollVelocity(y = 115f, topEdge = 0f, bottomEdge = 120f, zonePx = 100f, maxPxPerSecond = 1000f) > 0f)
    }

    @Test
    fun a_degenerate_viewport_never_scrolls() {
        assertEquals(0f, autoScrollVelocity(y = 5f, topEdge = 100f, bottomEdge = 100f, zonePx = 50f, maxPxPerSecond = 1000f))
        assertEquals(0f, autoScrollVelocity(y = 5f, topEdge = 0f, bottomEdge = 500f, zonePx = 0f, maxPxPerSecond = 1000f))
    }

    // ---------- commit heuristics ----------

    @Test
    fun a_drag_under_the_slop_commits_to_nothing() {
        assertFalse(isHorizontalCommit(dx = 5f, dy = 0f, slopPx = 12f))
        assertFalse(isVerticalCommit(dx = 0f, dy = 5f, slopPx = 12f))
    }

    @Test
    fun a_diagonal_is_not_a_horizontal_commit() {
        // 45°: the bias makes the gesture fall through to the scroll.
        assertFalse(isHorizontalCommit(dx = 40f, dy = 40f, slopPx = 12f))
        assertFalse(isHorizontalCommit(dx = 40f, dy = 30f, slopPx = 12f))
        assertTrue(isHorizontalCommit(dx = 40f, dy = 10f, slopPx = 12f))
        // Direction is irrelevant — dragging left selects too.
        assertTrue(isHorizontalCommit(dx = -40f, dy = 10f, slopPx = 12f))
    }

    @Test
    fun the_rail_only_answers_to_a_vertical_sweep() {
        // The system back gesture is horizontal, so it can never arm the rail.
        assertFalse(isVerticalCommit(dx = 60f, dy = 10f, slopPx = 12f))
        assertTrue(isVerticalCommit(dx = 10f, dy = 60f, slopPx = 12f))
        assertTrue(isVerticalCommit(dx = 10f, dy = -60f, slopPx = 12f))
    }

    // ---------- rail geometry ----------

    @Test
    fun the_rail_starts_after_the_system_gesture_inset() {
        // 24px inset, 32px rail → live between 24 and 55.
        assertFalse(railContains(x = 23f, railStartPx = 24f, railWidthPx = 32f))
        assertTrue(railContains(x = 24f, railStartPx = 24f, railWidthPx = 32f))
        assertTrue(railContains(x = 55f, railStartPx = 24f, railWidthPx = 32f))
        assertFalse(railContains(x = 56f, railStartPx = 24f, railWidthPx = 32f))
        assertFalse(railContains(x = 30f, railStartPx = 24f, railWidthPx = 0f))
    }

    // ---------- coordinate space ----------

    @Test
    fun pointer_y_shifts_by_the_leading_content_padding() {
        // 100px of top content padding → viewportStartOffset -100, so the
        // first item (offset 0) sits 100px below the top of the viewport.
        assertEquals(0f, toItemSpaceY(pointerY = 100f, viewportStartOffset = -100))
        assertEquals(-100f, toItemSpaceY(pointerY = 0f, viewportStartOffset = -100))
        assertEquals(50f, toItemSpaceY(pointerY = 50f, viewportStartOffset = 0))
    }
}
