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

class UniformRowLayoutTest {

    @Test
    fun column_count_matches_grid_cells_adaptive() {
        // Largest n with minCell·n + spacing·(n-1) ≤ width.
        // 400 wide, 110 min, 0 spacing → floor(400/110) = 3.
        assertEquals(3, columnCountFor(400f, 110f, 0f))
        // 350 wide, 100 min, 2 spacing → floor(352/102) = 3.
        assertEquals(3, columnCountFor(350f, 100f, 2f))
        // Never below 1, even when a single cell can't fit.
        assertEquals(1, columnCountFor(80f, 110f, 0f))
    }

    @Test
    fun cell_size_splits_width_after_spacing() {
        // 3 columns of 350 wide with 2dp spacing → (350 - 2·2)/3 = 115.333.
        assertEquals(115.33333f, cellSizeFor(350f, 3, 2f), absoluteTolerance = 0.001f)
        // No spacing → exact even split.
        assertEquals(100f, cellSizeFor(400f, 4, 0f))
    }

    @Test
    fun pack_returns_empty_for_empty_input() {
        assertTrue(
            packUniformRows(
                entries = emptyList(),
                containerWidthDp = 400f,
                minCellSizeDp = 110f,
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
            packUniformRows(
                entries = entries,
                containerWidthDp = 0f,
                minCellSizeDp = 110f,
                spacingDp = 2f
            ).isEmpty()
        )
    }

    @Test
    fun pack_chunks_cells_into_uniform_rows() {
        // 5 cells, container 400, min 110, spacing 0 → 3 columns.
        // ceil(5/3) = 2 rows: [3, 2]. Every row is square at cellSize.
        val entries = (1..5).map { i ->
            TimelineEntry.Cell(
                item = item("a$i", "2026-05-09T10:00:00Z"),
                index = i - 1
            )
        }
        val rows = packUniformRows(
            entries = entries,
            containerWidthDp = 400f,
            minCellSizeDp = 110f,
            spacingDp = 0f
        )

        val packedRows = rows.filterIsInstance<TimelineRowEntry.Row>()
        assertEquals(2, packedRows.size)
        assertEquals(3, packedRows[0].row.cells.size)
        assertEquals(2, packedRows[1].row.cells.size)
        // Both rows carry the column count and the same square height.
        val cellSize = cellSizeFor(400f, 3, 0f) // 133.333
        assertTrue(packedRows.all { it.row.columns == 3 })
        assertTrue(packedRows.all { it.row.rowHeightDp == cellSize })
    }

    @Test
    fun pack_emits_header_entries_in_order_and_resets_pending() {
        val may = "2026-05-09T10:00:00Z"
        val apr = "2026-04-30T10:00:00Z"
        val entries = listOf(
            TimelineEntry.Header("2026-05", "May 2026"),
            TimelineEntry.Cell(item("a", may), 0),
            TimelineEntry.Cell(item("b", may), 1),
            TimelineEntry.Header("2026-04", "Apr 2026"),
            TimelineEntry.Cell(item("c", apr), 2)
        )
        val rows = packUniformRows(
            entries = entries,
            containerWidthDp = 400f,
            minCellSizeDp = 110f,
            spacingDp = 0f
        )
        // header, partial-row(May, 2 cells), header, partial-row(Apr, 1 cell).
        assertEquals(4, rows.size)
        assertTrue(rows[0] is TimelineRowEntry.Header)
        assertTrue(rows[1] is TimelineRowEntry.Row)
        assertTrue(rows[2] is TimelineRowEntry.Header)
        assertTrue(rows[3] is TimelineRowEntry.Row)
        assertEquals(
            "2026-04",
            (rows[2] as TimelineRowEntry.Header).key
        )
        // Headers don't share a row with cells; each group flushes its own.
        assertEquals(2, (rows[1] as TimelineRowEntry.Row).row.cells.size)
        assertEquals(1, (rows[3] as TimelineRowEntry.Row).row.cells.size)
    }

    @Test
    fun skeleton_buckets_pack_with_the_same_height_as_real_rows() {
        // 350 wide, 100 min, 2 spacing → 3 columns, cellSize = (350-4)/3 = 115.33.
        // A 10-count skeleton → ceil(10/3) = 4 rows, last row 1 cell.
        val columns = columnCountFor(350f, 100f, 2f)
        val cellSize = cellSizeFor(350f, columns, 2f)

        val entries = listOf(
            TimelineEntry.Header(key = "2026-01", title = "enero de 2026"),
            TimelineEntry.SkeletonBucket(bucketKey = "2026-01", count = 10)
        )
        val rows = packUniformRows(
            entries = entries,
            containerWidthDp = 350f,
            minCellSizeDp = 100f,
            spacingDp = 2f
        )

        val skeletonRows = rows.filterIsInstance<TimelineRowEntry.SkeletonRow>()
        assertEquals(4, skeletonRows.size)
        assertEquals(listOf(3, 3, 3, 1), skeletonRows.map { it.cellCount })
        assertTrue(skeletonRows.all { it.cellsPerRow == columns })
        // Anti-shift invariant: the skeleton height equals the real cell size,
        // so a bucket reserves exactly what its content will occupy.
        assertTrue(skeletonRows.all { it.rowHeightDp == cellSize })

        // And N real cells produce exactly the same number of rows at the same
        // height — hydration replaces the skeleton without moving anything.
        val realEntries = listOf(TimelineEntry.Header("2026-01", "enero de 2026")) +
            (1..10).map { TimelineEntry.Cell(item("a$it", "2026-01-05T10:00:00Z"), it - 1) }
        val realRows = packUniformRows(realEntries, 350f, 100f, 2f)
            .filterIsInstance<TimelineRowEntry.Row>()
        assertEquals(skeletonRows.size, realRows.size)
        assertTrue(realRows.all { it.row.rowHeightDp == cellSize })
    }

    @Test
    fun find_row_index_for_date_matches_header_in_rows_list() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2026-04-30T10:00:00Z"),
                item("c", "2025-12-31T10:00:00Z")
            )
        )
        val rows = packUniformRows(
            entries = entries,
            containerWidthDp = 400f,
            minCellSizeDp = 110f,
            spacingDp = 0f
        )
        val index = findRowIndexForDate(rows, LocalDate(2026, 4, 15), TimelineGrouping.Month)
        assertTrue(index >= 0)
        val entry = rows[index] as TimelineRowEntry.Header
        assertEquals("2026-04", entry.key)
    }

    @Test
    fun find_row_index_for_date_falls_back_to_closest_older_header() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2025-12-31T10:00:00Z"),
                item("c", "2025-08-15T10:00:00Z")
            )
        )
        val rows = packUniformRows(
            entries = entries,
            containerWidthDp = 400f,
            minCellSizeDp = 110f,
            spacingDp = 0f
        )
        val index = findRowIndexForDate(rows, LocalDate(2025, 10, 1), TimelineGrouping.Month)
        assertTrue(index >= 0)
        val entry = rows[index] as TimelineRowEntry.Header
        assertEquals("2025-08", entry.key)
    }

    @Test
    fun find_row_index_for_date_returns_minus_one_when_no_rows() {
        assertEquals(-1, findRowIndexForDate(emptyList(), LocalDate(2026, 1, 1), TimelineGrouping.Month))
    }
}
