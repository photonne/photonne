package com.photonne.app.ui.grid

import com.photonne.app.data.models.TimelineItem
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun item(id: String, isoDate: String) = TimelineItem(
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

class GroupedAssetGridTest {

    @Test
    fun emits_one_header_per_month_and_keeps_global_indices() {
        val items = listOf(
            item("a", "2026-05-09T10:00:00Z"),
            item("b", "2026-05-08T10:00:00Z"),
            item("c", "2026-04-30T10:00:00Z"),
            item("d", "2026-04-01T10:00:00Z"),
            item("e", "2025-12-31T12:00:00Z")
        )

        val entries = groupTimelineEntries(items)
        // 3 headers + 5 cells
        assertEquals(8, entries.size)

        val headers = entries.filterIsInstance<TimelineEntry.Header>()
        assertEquals(3, headers.size)
        assertEquals("2026-05", headers[0].key)
        assertEquals("2026-04", headers[1].key)
        assertEquals("2025-12", headers[2].key)

        val cells = entries.filterIsInstance<TimelineEntry.Cell>()
        assertEquals(listOf(0, 1, 2, 3, 4), cells.map { it.index })
        assertEquals(listOf("a", "b", "c", "d", "e"), cells.map { it.item.id })
    }

    @Test
    fun handles_empty_list_without_headers() {
        assertTrue(groupTimelineEntries(emptyList()).isEmpty())
    }

    @Test
    fun consecutive_items_in_same_month_share_the_header() {
        val items = listOf(
            item("a", "2026-05-09T10:00:00Z"),
            item("b", "2026-05-08T10:00:00Z"),
            item("c", "2026-05-01T10:00:00Z")
        )
        val entries = groupTimelineEntries(items)
        assertEquals(1, entries.filterIsInstance<TimelineEntry.Header>().size)
        assertEquals(3, entries.filterIsInstance<TimelineEntry.Cell>().size)
    }

    @Test
    fun find_entry_index_returns_exact_match_for_existing_month() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2026-04-30T10:00:00Z"),
                item("c", "2026-04-01T10:00:00Z"),
                item("d", "2025-12-31T10:00:00Z")
            )
        )
        val target = kotlinx.datetime.LocalDate(2026, 4, 15)
        val index = findEntryIndexForMonth(entries, target)
        assertTrue(index >= 0)
        val entry = entries[index]
        assertTrue(entry is TimelineEntry.Header)
        assertEquals("2026-04", (entry as TimelineEntry.Header).key)
    }

    @Test
    fun find_entry_index_falls_back_to_closest_older_month() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2025-12-31T10:00:00Z"),
                item("c", "2025-08-15T10:00:00Z")
            )
        )
        val target = kotlinx.datetime.LocalDate(2025, 10, 1) // no October header
        val index = findEntryIndexForMonth(entries, target)
        assertTrue(index >= 0)
        val entry = entries[index] as TimelineEntry.Header
        assertEquals("2025-08", entry.key)
    }

    @Test
    fun find_entry_index_returns_minus_one_for_empty_entries() {
        val target = kotlinx.datetime.LocalDate(2026, 1, 1)
        assertEquals(-1, findEntryIndexForMonth(emptyList(), target))
    }

    @Test
    fun merge_with_local_interleaves_by_date_descending() {
        val server = listOf(
            item("s1", "2026-05-10T10:00:00Z"),
            item("s2", "2026-05-08T10:00:00Z"),
            item("s3", "2026-04-30T10:00:00Z")
        )
        val local = listOf(
            item("l1", "2026-05-09T10:00:00Z"),
            item("l2", "2026-05-07T10:00:00Z")
        )
        val merged = mergeTimelineWithLocal(server, local)
        assertEquals(listOf("s1", "l1", "s2", "l2", "s3"), merged.map { it.id })
    }

    @Test
    fun merge_with_empty_local_returns_server_unchanged() {
        val server = listOf(item("s1", "2026-05-10T10:00:00Z"))
        assertEquals(server, mergeTimelineWithLocal(server, emptyList()))
    }

    @Test
    fun merge_dedups_local_against_server_by_filename_and_size() {
        // The device knows about "shared.jpg" (size 100) but so does the
        // server — the local copy should be filtered out so the cell
        // doesn't appear twice. "only_local.jpg" survives because the
        // server doesn't have a matching (name, size) pair.
        val server = listOf(
            TimelineItem(
                id = "s1", fileName = "shared.jpg",
                fullPath = "/photos/shared.jpg", fileSize = 100L,
                fileCreatedAt = Instant.parse("2026-05-09T10:00:00Z"),
                fileModifiedAt = Instant.parse("2026-05-09T10:00:00Z"),
                extension = ".jpg",
                scannedAt = Instant.parse("2026-05-09T10:00:00Z"),
                type = "IMAGE"
            )
        )
        val local = listOf(
            TimelineItem(
                id = "device:1", fileName = "shared.jpg",
                fullPath = "shared.jpg", fileSize = 100L,
                fileCreatedAt = Instant.parse("2026-05-09T10:00:00Z"),
                fileModifiedAt = Instant.parse("2026-05-09T10:00:00Z"),
                extension = ".jpg",
                scannedAt = Instant.parse("2026-05-09T10:00:00Z"),
                type = "IMAGE", localUri = "1"
            ),
            TimelineItem(
                id = "device:2", fileName = "only_local.jpg",
                fullPath = "only_local.jpg", fileSize = 200L,
                fileCreatedAt = Instant.parse("2026-05-08T10:00:00Z"),
                fileModifiedAt = Instant.parse("2026-05-08T10:00:00Z"),
                extension = ".jpg",
                scannedAt = Instant.parse("2026-05-08T10:00:00Z"),
                type = "IMAGE", localUri = "2"
            )
        )
        val merged = mergeTimelineWithLocal(server, local)
        assertEquals(listOf("s1", "device:2"), merged.map { it.id })
    }

    @Test
    fun merge_dedups_local_against_server_by_checksum() {
        val server = listOf(
            TimelineItem(
                id = "s1", fileName = "renamed.jpg",
                fullPath = "/photos/renamed.jpg", fileSize = 999L,
                fileCreatedAt = Instant.parse("2026-05-09T10:00:00Z"),
                fileModifiedAt = Instant.parse("2026-05-09T10:00:00Z"),
                extension = ".jpg",
                scannedAt = Instant.parse("2026-05-09T10:00:00Z"),
                type = "IMAGE", checksum = "abc123"
            )
        )
        val local = listOf(
            TimelineItem(
                id = "device:1", fileName = "original.jpg",
                fullPath = "original.jpg", fileSize = 100L,
                fileCreatedAt = Instant.parse("2026-05-09T10:00:00Z"),
                fileModifiedAt = Instant.parse("2026-05-09T10:00:00Z"),
                extension = ".jpg",
                scannedAt = Instant.parse("2026-05-09T10:00:00Z"),
                type = "IMAGE", checksum = "abc123", localUri = "1"
            )
        )
        // Server hash trumps the (filename, size) mismatch.
        assertEquals(listOf("s1"), mergeTimelineWithLocal(server, local).map { it.id })
    }

    @Test
    fun merge_with_empty_server_returns_local_sorted() {
        val local = listOf(
            item("l1", "2026-05-07T10:00:00Z"),
            item("l2", "2026-05-09T10:00:00Z")
        )
        val merged = mergeTimelineWithLocal(emptyList(), local)
        assertEquals(listOf("l2", "l1"), merged.map { it.id })
    }
}
