package com.photonne.app.ui.grid

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
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

class GroupedAssetGridZoomTest {

    // ---------- Day grouping ----------

    @Test
    fun day_grouping_emits_one_header_per_day() {
        // Use noon UTC so the local-time conversion in monthKeyOf/dayKeyOf
        // doesn't straddle midnight in non-UTC test timezones.
        val items = listOf(
            item("a", "2026-05-09T16:00:00Z"),
            item("b", "2026-05-09T12:00:00Z"),
            item("c", "2026-05-08T12:00:00Z"),
            item("d", "2026-04-30T12:00:00Z")
        )
        val entries = groupTimelineEntries(items, TimelineGrouping.Day)
        val headers = entries.filterIsInstance<TimelineEntry.Header>()
        assertEquals(3, headers.size)
        // Keys are zero-padded YYYY-MM-DD so they sort lexicographically.
        assertEquals(listOf("2026-05-09", "2026-05-08", "2026-04-30"), headers.map { it.key })
        assertEquals(4, entries.filterIsInstance<TimelineEntry.Cell>().size)
    }

    // ---------- Year grouping ----------

    @Test
    fun year_grouping_emits_one_header_per_year() {
        val items = listOf(
            item("a", "2026-05-09T10:00:00Z"),
            item("b", "2026-01-01T10:00:00Z"),
            item("c", "2025-12-31T10:00:00Z"),
            item("d", "2024-06-15T10:00:00Z")
        )
        val entries = groupTimelineEntries(items, TimelineGrouping.Year)
        val headers = entries.filterIsInstance<TimelineEntry.Header>()
        assertEquals(3, headers.size)
        assertEquals(listOf("2026", "2025", "2024"), headers.map { it.key })
        // Year header titles are just the bare year string.
        assertEquals(listOf("2026", "2025", "2024"), headers.map { it.title })
    }

    @Test
    fun default_grouping_is_month_so_legacy_callers_keep_working() {
        val items = listOf(
            item("a", "2026-05-09T10:00:00Z"),
            item("b", "2026-04-30T10:00:00Z")
        )
        val entries = groupTimelineEntries(items)
        val headers = entries.filterIsInstance<TimelineEntry.Header>()
        assertEquals(listOf("2026-05", "2026-04"), headers.map { it.key })
    }

    // ---------- findEntryIndexForDate ----------

    @Test
    fun find_entry_index_exact_match_at_year_level() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2024-06-15T10:00:00Z")
            ),
            TimelineGrouping.Year
        )
        val index = findEntryIndexForDate(entries, LocalDate(2024, 7, 1), TimelineGrouping.Year)
        assertTrue(index >= 0)
        assertEquals("2024", (entries[index] as TimelineEntry.Header).key)
    }

    @Test
    fun find_entry_index_falls_back_to_closest_older_year() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2023-06-15T10:00:00Z")
            ),
            TimelineGrouping.Year
        )
        // No 2025 header — should land on 2023.
        val index = findEntryIndexForDate(entries, LocalDate(2025, 3, 1), TimelineGrouping.Year)
        assertEquals("2023", (entries[index] as TimelineEntry.Header).key)
    }

    @Test
    fun find_entry_index_exact_match_at_day_level() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2026-05-08T10:00:00Z"),
                item("c", "2026-04-30T10:00:00Z")
            ),
            TimelineGrouping.Day
        )
        val index = findEntryIndexForDate(entries, LocalDate(2026, 5, 8), TimelineGrouping.Day)
        assertEquals("2026-05-08", (entries[index] as TimelineEntry.Header).key)
    }

    @Test
    fun find_entry_index_falls_back_to_closest_older_day() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2026-05-05T10:00:00Z"),
                item("c", "2026-04-30T10:00:00Z")
            ),
            TimelineGrouping.Day
        )
        // 2026-05-07 has no exact match: should fall back to 2026-05-05.
        val index = findEntryIndexForDate(entries, LocalDate(2026, 5, 7), TimelineGrouping.Day)
        assertEquals("2026-05-05", (entries[index] as TimelineEntry.Header).key)
    }

    @Test
    fun find_entry_index_returns_minus_one_for_empty_entries_at_each_level() {
        val target = LocalDate(2026, 1, 1)
        assertEquals(-1, findEntryIndexForDate(emptyList(), target, TimelineGrouping.Year))
        assertEquals(-1, findEntryIndexForDate(emptyList(), target, TimelineGrouping.Month))
        assertEquals(-1, findEntryIndexForDate(emptyList(), target, TimelineGrouping.Day))
    }

    @Test
    fun legacy_findEntryIndexForMonth_delegates_to_findEntryIndexForDate() {
        val entries = groupTimelineEntries(
            listOf(
                item("a", "2026-05-09T10:00:00Z"),
                item("b", "2026-04-30T10:00:00Z")
            )
        )
        val target = LocalDate(2026, 4, 15)
        assertEquals(
            findEntryIndexForDate(entries, target, TimelineGrouping.Month),
            findEntryIndexForMonth(entries, target)
        )
    }
}
