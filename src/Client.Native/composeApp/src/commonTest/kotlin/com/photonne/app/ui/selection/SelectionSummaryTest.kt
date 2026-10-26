package com.photonne.app.ui.selection

import com.photonne.app.data.models.TimelineItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SelectionSummaryTest {
    private fun item(id: String, capturedAt: String, size: Long, localUri: String? = null) = TimelineItem(
        id = id,
        fileName = "$id.jpg",
        fullPath = "/assets/$id.jpg",
        fileSize = size,
        fileCreatedAt = Instant.parse(capturedAt),
        fileModifiedAt = Instant.parse(capturedAt),
        extension = ".jpg",
        scannedAt = Instant.parse(capturedAt),
        type = "IMAGE",
        localUri = localUri
    )

    private val items = listOf(
        item("a", "2024-03-12T10:00:00Z", 1024 * 1024),
        item("b", "2024-04-03T10:00:00Z", 1024 * 1024),
        item("c", "2024-03-12T22:00:00Z", 512 * 1024),
        item("local", "2020-01-01T00:00:00Z", 999, localUri = "content://x")
    )

    @Test
    fun spansTheSelectedDatesAndAddsUpTheirSize() {
        val summary = selectionSummary(items, setOf("a", "b"))!!
        assertTrue(" – " in summary, summary)
        assertTrue(summary.endsWith(" · 2 MB"), summary)
    }

    @Test
    fun saysOneDateWhenEverythingIsFromTheSameDay() {
        val summary = selectionSummary(items, setOf("a", "c"))!!
        assertTrue(" – " !in summary, summary)
        assertTrue(summary.endsWith(" · 1,5 MB"), summary)
    }

    @Test
    fun leavesOutDeviceOnlyFilesAndEmptySelections() {
        assertEquals(selectionSummary(items, setOf("a")), selectionSummary(items, setOf("a", "local")))
        assertNull(selectionSummary(items, setOf("local")))
        assertNull(selectionSummary(items, emptySet()))
    }
}
