package com.photonne.app.ui.grid

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import com.photonne.app.data.timeline.TimelineBucketState
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun item(id: String, isoDate: String, localUri: String? = null) = TimelineItem(
    id = id,
    fileName = "$id.jpg",
    fullPath = "/photos/$id.jpg",
    fileSize = 1L,
    fileCreatedAt = Instant.parse(isoDate),
    fileModifiedAt = Instant.parse(isoDate),
    extension = ".jpg",
    scannedAt = Instant.parse(isoDate),
    type = "IMAGE",
    localUri = localUri
)

private fun loaded(key: String, vararg items: TimelineItem) =
    TimelineBucketState(key = key, count = items.size, items = items.toList())

private fun skeleton(key: String, count: Int) =
    TimelineBucketState(key = key, count = count)

class BucketTimelineEntriesTest {

    @Test
    fun loaded_and_unloaded_buckets_interleave_headers_cells_and_skeletons() {
        val result = buildBucketEntries(
            buckets = listOf(
                loaded("2026-02", item("feb-1", "2026-02-20T10:00:00Z")),
                skeleton("2026-01", 42),
                loaded("2025-12", item("dec-1", "2025-12-25T10:00:00Z"))
            ),
            localItems = emptyList(),
            grouping = TimelineGrouping.Month
        )

        val kinds = result.entries.map { it::class.simpleName }
        assertEquals(
            listOf("Header", "Cell", "Header", "SkeletonBucket", "Header", "Cell"),
            kinds
        )
        val skeletonEntry = result.entries.filterIsInstance<TimelineEntry.SkeletonBucket>().single()
        assertEquals("2026-01", skeletonEntry.bucketKey)
        assertEquals(42, skeletonEntry.count)
        // Merged list only contains loaded items, with sequential indices.
        assertEquals(listOf("feb-1", "dec-1"), result.mergedItems.map { it.id })
        assertEquals(
            listOf(0, 1),
            result.entries.filterIsInstance<TimelineEntry.Cell>().map { it.index }
        )
    }

    @Test
    fun year_grouping_emits_one_header_per_year_across_buckets() {
        val result = buildBucketEntries(
            buckets = listOf(
                loaded("2026-02", item("feb-1", "2026-02-20T10:00:00Z")),
                skeleton("2026-01", 10),
                loaded("2025-12", item("dec-1", "2025-12-25T10:00:00Z"))
            ),
            localItems = emptyList(),
            grouping = TimelineGrouping.Year
        )

        val headers = result.entries.filterIsInstance<TimelineEntry.Header>()
        assertEquals(listOf("2026", "2025"), headers.map { it.key })
    }

    @Test
    fun day_grouping_emits_day_headers_inside_loaded_buckets() {
        val result = buildBucketEntries(
            buckets = listOf(
                loaded(
                    "2026-02",
                    item("feb-1", "2026-02-20T10:00:00Z"),
                    item("feb-2", "2026-02-20T09:00:00Z"),
                    item("feb-3", "2026-02-10T10:00:00Z")
                ),
                skeleton("2026-01", 5)
            ),
            localItems = emptyList(),
            grouping = TimelineGrouping.Day
        )

        val headers = result.entries.filterIsInstance<TimelineEntry.Header>()
        // Two day headers for February + the month-level fallback header for
        // the unloaded January bucket.
        assertEquals(3, headers.size)
        assertEquals("2026-01", headers.last().key)
    }

    @Test
    fun local_only_months_become_synthetic_buckets_in_order() {
        val result = buildBucketEntries(
            buckets = listOf(
                loaded("2026-02", item("feb-1", "2026-02-20T10:00:00Z")),
                loaded("2025-12", item("dec-1", "2025-12-25T10:00:00Z"))
            ),
            localItems = listOf(item("local-jan", "2026-01-10T10:00:00Z", localUri = "uri")),
            grouping = TimelineGrouping.Month
        )

        assertEquals(listOf("2026-02", "2026-01", "2025-12"), result.bucketOrder)
        assertEquals(listOf("feb-1", "local-jan", "dec-1"), result.mergedItems.map { it.id })
    }

    @Test
    fun local_items_merge_into_their_loaded_bucket() {
        val result = buildBucketEntries(
            buckets = listOf(loaded("2026-02", item("feb-1", "2026-02-20T10:00:00Z"))),
            localItems = listOf(item("local-feb", "2026-02-25T10:00:00Z", localUri = "uri")),
            grouping = TimelineGrouping.Month
        )

        // Newer local item sorts before the server item, same bucket.
        assertEquals(listOf("local-feb", "feb-1"), result.mergedItems.map { it.id })
        assertEquals(1, result.loadedRanges.size)
    }

    @Test
    fun local_items_of_unloaded_months_stay_hidden_until_load() {
        val result = buildBucketEntries(
            buckets = listOf(skeleton("2026-02", 30)),
            localItems = listOf(item("local-feb", "2026-02-25T10:00:00Z", localUri = "uri")),
            grouping = TimelineGrouping.Month
        )

        assertTrue(result.mergedItems.isEmpty())
        assertEquals(30, result.entries.filterIsInstance<TimelineEntry.SkeletonBucket>().single().count)
    }

    @Test
    fun empty_unloaded_buckets_are_skipped_entirely() {
        val result = buildBucketEntries(
            buckets = listOf(
                loaded("2026-02", item("feb-1", "2026-02-20T10:00:00Z")),
                skeleton("2026-01", 0)
            ),
            localItems = emptyList(),
            grouping = TimelineGrouping.Month
        )

        assertEquals(listOf("2026-02"), result.bucketOrder)
        assertEquals(1, result.entries.filterIsInstance<TimelineEntry.Header>().size)
    }

    @Test
    fun contiguous_run_is_bounded_by_unloaded_buckets() {
        val result = buildBucketEntries(
            buckets = listOf(
                loaded("2026-03", item("mar-1", "2026-03-05T10:00:00Z")),
                loaded("2026-02", item("feb-1", "2026-02-20T10:00:00Z")),
                skeleton("2026-01", 10),
                loaded("2025-12", item("dec-1", "2025-12-25T10:00:00Z"))
            ),
            localItems = emptyList(),
            grouping = TimelineGrouping.Month
        )

        // Clicking feb-1 (index 1): run covers mar+feb, not dec.
        val run = contiguousRunAround(mergedIndex = 1, result = result)
        assertNotNull(run)
        assertEquals(listOf("mar-1", "feb-1"), run.first.map { it.id })
        assertEquals(0, run.second)

        // Clicking dec-1 (index 2): its own single-bucket run.
        val decRun = contiguousRunAround(mergedIndex = 2, result = result)
        assertNotNull(decRun)
        assertEquals(listOf("dec-1"), decRun.first.map { it.id })
        assertEquals(2, decRun.second)

        // An index outside every loaded range has no run.
        assertNull(contiguousRunAround(mergedIndex = 99, result = result))
    }

    @Test
    fun neighbor_expansion_adds_adjacent_buckets_only() {
        val order = listOf("2026-03", "2026-02", "2026-01", "2025-12")

        assertEquals(
            listOf("2026-02", "2026-03", "2026-01"),
            expandWithNeighborBuckets(listOf("2026-02"), order)
        )
        assertEquals(
            listOf("2026-03", "2026-02"),
            expandWithNeighborBuckets(listOf("2026-03"), order)
        )
        // Unknown keys are dropped silently.
        assertTrue(expandWithNeighborBuckets(listOf("1999-01"), order).isEmpty())
    }

    @Test
    fun skeleton_buckets_pack_into_deterministic_row_count() {
        val entries = listOf(
            TimelineEntry.Header(key = "2026-01", title = "enero de 2026"),
            TimelineEntry.SkeletonBucket(bucketKey = "2026-01", count = 10)
        )

        // 350dp wide, 100dp target rows → 3 cells/row → ceil(10/3) = 4 rows,
        // last row with a single cell.
        val rows = packJustifiedRows(
            entries = entries,
            containerWidthDp = 350f,
            targetRowHeightDp = 100f,
            spacingDp = 2f
        )

        val skeletonRows = rows.filterIsInstance<JustifiedRowEntry.SkeletonRow>()
        assertEquals(4, skeletonRows.size)
        assertEquals(listOf(3, 3, 3, 1), skeletonRows.map { it.cellCount })
        assertTrue(skeletonRows.all { it.cellsPerRow == 3 })
        assertTrue(skeletonRows.all { it.rowHeightDp == 100f })
    }
}
