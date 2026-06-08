package com.photonne.app.ui.timeline

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.grid.TimelineRow
import com.photonne.app.ui.grid.TimelineRowEntry
import com.photonne.app.ui.grid.TimelineEntry
import com.photonne.app.ui.grid.findRowIndexForAsset
import com.photonne.app.ui.grid.formatLocalizedMonth
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private fun item(id: String) = TimelineItem(
    id = id,
    fileName = "$id.jpg",
    fullPath = "/photos/$id.jpg",
    fileSize = 1L,
    fileCreatedAt = Instant.parse("2026-01-01T10:00:00Z"),
    fileModifiedAt = Instant.parse("2026-01-01T10:00:00Z"),
    extension = ".jpg",
    scannedAt = Instant.parse("2026-01-01T10:00:00Z"),
    type = "IMAGE"
)

private fun header(key: String) = TimelineRowEntry.Header(key = key, title = key)

private fun row(id: String, heightDp: Float = 100f) = TimelineRowEntry.Row(
    TimelineRow(
        cells = listOf(TimelineEntry.Cell(item(id), index = 0)),
        rowHeightDp = heightDp,
        columns = 1
    )
)

private fun skeletonRow(bucketKey: String, i: Int, heightDp: Float = 100f) =
    TimelineRowEntry.SkeletonRow(
        bucketKey = bucketKey,
        rowIndex = i,
        cellCount = 3,
        cellsPerRow = 3,
        rowHeightDp = heightDp
    )

class TimelineScrubberMathTest {

    @Test
    fun prefix_sums_accumulate_header_row_and_skeleton_heights() {
        val rows = listOf(
            header("2026-02"),       // 56 + 2
            row("a", 100f),          // 100 + 2
            skeletonRow("2026-01", 0, 100f) // 100 + 2
        )

        val prefix = rowHeightPrefixSums(rows, headerHeightDp = 56f, spacingDp = 2f)

        assertEquals(listOf(0f, 58f, 160f, 262f), prefix.toList())
    }

    @Test
    fun fraction_maps_to_the_row_whose_span_contains_it() {
        val rows = listOf(header("2026-02"), row("a"), row("b"), row("c"))
        val prefix = rowHeightPrefixSums(rows) // total = 58 + 102*3 = 364

        assertEquals(0, rowIndexForFraction(prefix, 0f))
        // 0.5 * 364 = 182 → inside row "a"+"b" span: 58..160 = a, 160..262 = b
        assertEquals(2, rowIndexForFraction(prefix, 0.5f))
        assertEquals(3, rowIndexForFraction(prefix, 1f))
        // Out-of-range fractions clamp instead of crashing.
        assertEquals(0, rowIndexForFraction(prefix, -1f))
        assertEquals(3, rowIndexForFraction(prefix, 2f))
    }

    @Test
    fun labels_carry_the_preceding_header_forward_and_localize_months() {
        val rows = listOf(
            header("2026"),          // year-only key → plain year
            row("a"),
            header("2025-12-31"),    // day key → month label
            row("b"),
            skeletonRow("2025-11", 0)
        )
        // The skeleton row keeps the last header's label ("2025-12-31" month).
        val expectedDec = formatLocalizedMonth(LocalDate(2025, 12, 1))

        val labels = scrubberRowLabels(rows)

        assertEquals(listOf("2026", "2026", expectedDec, expectedDec, expectedDec), labels)
    }

    @Test
    fun find_row_index_for_asset_scans_row_cells() {
        val rows = listOf(header("2026-02"), row("a"), row("b"), skeletonRow("2026-01", 0))

        assertEquals(2, findRowIndexForAsset(rows, "b"))
        assertEquals(-1, findRowIndexForAsset(rows, "missing"))
    }
}
