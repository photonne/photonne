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
            item("e", "2025-12-31T23:59:59Z")
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
}
