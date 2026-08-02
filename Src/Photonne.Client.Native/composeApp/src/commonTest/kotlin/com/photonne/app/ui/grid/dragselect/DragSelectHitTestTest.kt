package com.photonne.app.ui.grid.dragselect

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.grid.TimelineRowEntry
import com.photonne.app.ui.grid.groupTimelineEntries
import com.photonne.app.ui.grid.packUniformRows
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun asset(id: String, isoDate: String) = TimelineItem(
    id = id,
    fileName = "$id.jpg",
    fullPath = "/photos/$id.jpg",
    fileSize = 1L,
    fileCreatedAt = Instant.parse(isoDate),
    fileModifiedAt = Instant.parse(isoDate),
    extension = ".jpg",
    scannedAt = Instant.parse(isoDate),
    type = "IMAGE"
)

/** Una fila de 3 celdas de 100x100 con 2px de separación, a partir de [top]. */
private fun gridRow(row: Int, firstIndex: Int, top: Int) = (0 until 3).map { column ->
    GridItemBounds(
        index = firstIndex + column,
        left = column * 102,
        top = top,
        width = 100,
        height = 100,
        row = row
    )
}

class DragSelectHitTestTest {

    // ---------- Motor A: LazyVerticalGrid ----------

    @Test
    fun finds_the_cell_under_the_pointer() {
        val items = gridRow(row = 0, firstIndex = 0, top = 0) +
            gridRow(row = 1, firstIndex = 3, top = 102)

        assertEquals(0, hitTestGridItems(items, x = 10f, y = 10f)?.index)
        assertEquals(2, hitTestGridItems(items, x = 250f, y = 10f)?.index)
        assertEquals(4, hitTestGridItems(items, x = 150f, y = 150f)?.index)
    }

    @Test
    fun the_gap_between_cells_is_a_miss_that_the_band_shrugs_off() {
        val items = gridRow(row = 0, firstIndex = 0, top = 0)
        // A frame landing in the 2px seam simply doesn't move the band; the
        // next one corrects it, so no hole is left behind.
        assertNull(hitTestGridItems(items, x = 101f, y = 10f))
        assertNull(hitTestGridItems(items, x = 10f, y = 101f))
    }

    @Test
    fun a_pointer_outside_every_cell_hits_nothing() {
        val items = gridRow(row = 0, firstIndex = 0, top = 0)
        assertNull(hitTestGridItems(items, x = -10f, y = 10f))
        assertNull(hitTestGridItems(items, x = 900f, y = 10f))
        assertNull(hitTestGridItems(items, x = 10f, y = -10f))
    }

    @Test
    fun negative_offsets_from_a_scrolled_grid_still_resolve() {
        // Scrolled down: the first visible row sits at a negative offset in
        // item space. Getting the sign wrong here shifts the hit-test by a
        // row and a half — the bug this conversion exists to prevent.
        val items = gridRow(row = 4, firstIndex = 12, top = -40) +
            gridRow(row = 5, firstIndex = 15, top = 62)

        assertEquals(12, hitTestGridItems(items, x = 10f, y = -30f)?.index)
        assertEquals(15, hitTestGridItems(items, x = 10f, y = 70f)?.index)
    }

    @Test
    fun the_row_under_the_pointer_reports_its_whole_ordinal_range() {
        // One full-span header at index 0 → headerCount 1, so the first cell
        // row (indices 1..3) is ordinals 0..2.
        val items = gridRow(row = 1, firstIndex = 1, top = 0) +
            gridRow(row = 2, firstIndex = 4, top = 102)

        assertEquals(DragSelectRow(1, 0..2), rowAtY(items, y = 50f, headerCount = 1))
        assertEquals(DragSelectRow(2, 3..5), rowAtY(items, y = 150f, headerCount = 1))
        assertNull(rowAtY(items, y = 5000f, headerCount = 1))
    }

    @Test
    fun a_partial_trailing_row_reports_only_the_cells_it_has() {
        val items = listOf(
            GridItemBounds(index = 6, left = 0, top = 204, width = 100, height = 100, row = 2),
            GridItemBounds(index = 7, left = 102, top = 204, width = 100, height = 100, row = 2)
        )
        assertEquals(DragSelectRow(2, 6..7), rowAtY(items, y = 250f, headerCount = 0))
    }

    @Test
    fun the_full_span_header_is_never_reported_as_a_row_of_cells() {
        // The adapter filters the header out by contentType before we get
        // here, so a header-only visible list must resolve to nothing.
        assertNull(rowAtY(emptyList(), y = 10f, headerCount = 1))
    }

    // ---------- Motor B: LazyColumn de filas (timeline) ----------

    /** Filas reales del packer: 4 assets de mayo + 2 de abril, 3 columnas. */
    private fun timelineRows(): List<TimelineRowEntry> {
        val items = listOf(
            asset("a", "2026-05-09T10:00:00Z"),
            asset("b", "2026-05-08T10:00:00Z"),
            asset("c", "2026-05-07T10:00:00Z"),
            asset("d", "2026-05-06T10:00:00Z"),
            asset("e", "2026-04-30T10:00:00Z"),
            asset("f", "2026-04-29T10:00:00Z")
        )
        // 3·110 + 2·2 = 334dp wide → exactly 3 columns of 110dp.
        return packUniformRows(groupTimelineEntries(items), 334f, 110f, 2f)
    }

    @Test
    fun resolves_a_timeline_cell_to_its_global_ordinal() {
        val rows = timelineRows()
        // 0 header, 1 full row (a,b,c), 2 partial row (d), 3 header, 4 row (e,f)
        assertEquals(
            DragSelectCell(ordinal = 0, id = "a"),
            hitTestRowEntry(rows, entryIndex = 1, x = 10f, spacingPx = 2f, dpToPx = 1f)
        )
        assertEquals(
            DragSelectCell(ordinal = 2, id = "c"),
            hitTestRowEntry(rows, entryIndex = 1, x = 230f, spacingPx = 2f, dpToPx = 1f)
        )
        // Ordinals are global across groups, not per-group.
        assertEquals(
            DragSelectCell(ordinal = 4, id = "e"),
            hitTestRowEntry(rows, entryIndex = 4, x = 10f, spacingPx = 2f, dpToPx = 1f)
        )
    }

    @Test
    fun month_bands_and_skeleton_rows_are_inert() {
        val rows = timelineRows()
        assertNull(hitTestRowEntry(rows, entryIndex = 0, x = 10f, spacingPx = 2f, dpToPx = 1f))
        assertNull(hitTestRowEntry(rows, entryIndex = 3, x = 10f, spacingPx = 2f, dpToPx = 1f))

        val skeleton = listOf(
            TimelineRowEntry.SkeletonRow(
                bucketKey = "2026-03", rowIndex = 0, cellCount = 3,
                cellsPerRow = 3, rowHeightDp = 110f
            )
        )
        assertNull(hitTestRowEntry(skeleton, entryIndex = 0, x = 10f, spacingPx = 2f, dpToPx = 1f))
        assertNull(rowOrdinalsOf(skeleton, entryIndex = 0))
    }

    @Test
    fun the_reserved_slots_of_a_partial_row_are_inert() {
        val rows = timelineRows()
        // Row 2 holds only "d"; columns 1 and 2 are the Spacers that keep the
        // cell square, and must not resolve to "d" or to the next group.
        assertEquals(
            DragSelectCell(ordinal = 3, id = "d"),
            hitTestRowEntry(rows, entryIndex = 2, x = 10f, spacingPx = 2f, dpToPx = 1f)
        )
        assertNull(hitTestRowEntry(rows, entryIndex = 2, x = 150f, spacingPx = 2f, dpToPx = 1f))
        assertNull(hitTestRowEntry(rows, entryIndex = 2, x = 300f, spacingPx = 2f, dpToPx = 1f))
    }

    @Test
    fun an_index_past_the_rows_resolves_to_nothing() {
        val rows = timelineRows()
        assertNull(hitTestRowEntry(rows, entryIndex = 99, x = 10f, spacingPx = 2f, dpToPx = 1f))
        assertNull(hitTestRowEntry(rows, entryIndex = -1, x = 10f, spacingPx = 2f, dpToPx = 1f))
        assertNull(rowOrdinalsOf(rows, entryIndex = 99))
    }

    @Test
    fun density_scales_the_reconstructed_cell_width() {
        val rows = timelineRows()
        // At 2x, the same cell spans twice as many pixels: 230px was the third
        // column at 1x and is still the second at 2x.
        assertEquals(
            DragSelectCell(ordinal = 1, id = "b"),
            hitTestRowEntry(rows, entryIndex = 1, x = 230f, spacingPx = 4f, dpToPx = 2f)
        )
    }

    @Test
    fun a_timeline_row_reports_its_contiguous_ordinals() {
        val rows = timelineRows()
        assertEquals(0..2, rowOrdinalsOf(rows, entryIndex = 1))
        assertEquals(3..3, rowOrdinalsOf(rows, entryIndex = 2))
        assertEquals(4..5, rowOrdinalsOf(rows, entryIndex = 4))
        assertNull(rowOrdinalsOf(rows, entryIndex = 0))
    }
}
