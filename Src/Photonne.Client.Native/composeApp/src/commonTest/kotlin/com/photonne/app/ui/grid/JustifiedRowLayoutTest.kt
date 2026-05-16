package com.photonne.app.ui.grid

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun item(
    id: String,
    isoDate: String,
    width: Int? = null,
    height: Int? = null
) = TimelineItem(
    id = id,
    fileName = "$id.jpg",
    fullPath = "/photos/$id.jpg",
    fileSize = 1L,
    fileCreatedAt = Instant.parse(isoDate),
    fileModifiedAt = Instant.parse(isoDate),
    extension = ".jpg",
    scannedAt = Instant.parse(isoDate),
    type = "IMAGE",
    width = width,
    height = height
)

class JustifiedRowLayoutTest {

    @Test
    fun aspect_ratio_falls_back_to_square_for_unknown_dimensions() {
        assertEquals(1.0f, aspectRatioOf(item("a", "2026-05-09T10:00:00Z")))
    }

    @Test
    fun aspect_ratio_clamps_extreme_panorama_to_25() {
        val panorama = item("a", "2026-05-09T10:00:00Z", width = 8000, height = 800)
        assertEquals(2.5f, aspectRatioOf(panorama))
    }

    @Test
    fun aspect_ratio_clamps_extreme_portrait_to_05() {
        val tall = item("a", "2026-05-09T10:00:00Z", width = 100, height = 1000)
        assertEquals(0.5f, aspectRatioOf(tall))
    }

    @Test
    fun aspect_ratio_keeps_natural_value_in_range() {
        // 3:2 landscape — within [0.5, 2.5].
        val landscape = item("a", "2026-05-09T10:00:00Z", width = 300, height = 200)
        assertEquals(1.5f, aspectRatioOf(landscape))
    }

    @Test
    fun pack_returns_empty_for_empty_input() {
        assertTrue(
            packJustifiedRows(
                entries = emptyList(),
                containerWidthDp = 400f,
                targetRowHeightDp = 110f,
                spacingDp = 2f
            ).isEmpty()
        )
    }

    @Test
    fun pack_returns_empty_for_non_positive_width() {
        val entries = listOf(
            TimelineEntry.Cell(item("a", "2026-05-09T10:00:00Z"), 0)
        )
        assertTrue(
            packJustifiedRows(
                entries = entries,
                containerWidthDp = 0f,
                targetRowHeightDp = 110f,
                spacingDp = 2f
            ).isEmpty()
        )
    }

    @Test
    fun pack_groups_squares_into_rows_that_fit_width() {
        // 5 unit-aspect cells, container 400, target row 110 → each square
        // would be 110 wide. After 4 of them total width ≈ 440 (440 > 400)
        // so the row flushes with 4 cells at projected height = 400/4 ≈ 100.
        val entries = (1..5).map { i ->
            TimelineEntry.Cell(
                item = item("a$i", "2026-05-09T10:00:00Z", width = 100, height = 100),
                index = i - 1
            )
        }
        val rows = packJustifiedRows(
            entries = entries,
            containerWidthDp = 400f,
            targetRowHeightDp = 110f,
            spacingDp = 0f
        )

        val packedRows = rows.filterIsInstance<JustifiedRowEntry.Row>()
        assertEquals(2, packedRows.size)
        assertEquals(4, packedRows[0].row.cells.size)
        assertEquals(1, packedRows[1].row.cells.size)
        // First row scales down to fit exactly.
        assertEquals(100f, packedRows[0].row.rowHeightDp)
        // Trailing partial row keeps target height (no stretch).
        assertEquals(110f, packedRows[1].row.rowHeightDp)
    }

    @Test
    fun pack_emits_header_entries_in_order_and_resets_pending() {
        val may = "2026-05-09T10:00:00Z"
        val apr = "2026-04-30T10:00:00Z"
        val entries = listOf(
            TimelineEntry.Header("2026-05", "May 2026"),
            TimelineEntry.Cell(item("a", may, 100, 100), 0),
            TimelineEntry.Cell(item("b", may, 100, 100), 1),
            TimelineEntry.Header("2026-04", "Apr 2026"),
            TimelineEntry.Cell(item("c", apr, 100, 100), 2)
        )
        val rows = packJustifiedRows(
            entries = entries,
            containerWidthDp = 400f,
            targetRowHeightDp = 110f,
            spacingDp = 0f
        )
        // header, partial-row(May), header, partial-row(Apr) — both rows
        // are below row-fill threshold so they flush as trailing partials.
        assertEquals(4, rows.size)
        assertTrue(rows[0] is JustifiedRowEntry.Header)
        assertTrue(rows[1] is JustifiedRowEntry.Row)
        assertTrue(rows[2] is JustifiedRowEntry.Header)
        assertTrue(rows[3] is JustifiedRowEntry.Row)
        assertEquals(
            "2026-04",
            (rows[2] as JustifiedRowEntry.Header).key
        )
    }

    @Test
    fun pack_respects_spacing_in_width_budget() {
        // 4 squares, container 400, spacing 10 between them (30 total).
        // Available width for cells = 370, projected row height once 4
        // squares fit = 370 / 4 = 92.5 → below target 110 → flushes 4.
        val entries = (1..4).map { i ->
            TimelineEntry.Cell(
                item = item("a$i", "2026-05-09T10:00:00Z", width = 100, height = 100),
                index = i - 1
            )
        }
        val rows = packJustifiedRows(
            entries = entries,
            containerWidthDp = 400f,
            targetRowHeightDp = 110f,
            spacingDp = 10f
        ).filterIsInstance<JustifiedRowEntry.Row>()
        assertEquals(1, rows.size)
        assertEquals(4, rows[0].row.cells.size)
        assertEquals(92.5f, rows[0].row.rowHeightDp)
    }

    @Test
    fun find_row_index_for_date_matches_header_in_rows_list() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z", 100, 100),
                item("b", "2026-04-30T10:00:00Z", 100, 100),
                item("c", "2025-12-31T10:00:00Z", 100, 100)
            )
        )
        val rows = packJustifiedRows(
            entries = entries,
            containerWidthDp = 400f,
            targetRowHeightDp = 110f,
            spacingDp = 0f
        )
        val index = findRowIndexForDate(rows, LocalDate(2026, 4, 15), TimelineGrouping.Month)
        assertTrue(index >= 0)
        val entry = rows[index] as JustifiedRowEntry.Header
        assertEquals("2026-04", entry.key)
    }

    @Test
    fun find_row_index_for_date_falls_back_to_closest_older_header() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z", 100, 100),
                item("b", "2025-12-31T10:00:00Z", 100, 100),
                item("c", "2025-08-15T10:00:00Z", 100, 100)
            )
        )
        val rows = packJustifiedRows(
            entries = entries,
            containerWidthDp = 400f,
            targetRowHeightDp = 110f,
            spacingDp = 0f
        )
        val index = findRowIndexForDate(rows, LocalDate(2025, 10, 1), TimelineGrouping.Month)
        assertTrue(index >= 0)
        val entry = rows[index] as JustifiedRowEntry.Header
        assertEquals("2025-08", entry.key)
    }

    @Test
    fun find_row_index_for_date_returns_minus_one_when_no_rows() {
        assertEquals(-1, findRowIndexForDate(emptyList(), LocalDate(2026, 1, 1), TimelineGrouping.Month))
    }
}
