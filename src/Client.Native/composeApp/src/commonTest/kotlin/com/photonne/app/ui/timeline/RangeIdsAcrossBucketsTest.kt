package com.photonne.app.ui.timeline

import com.photonne.app.data.models.TimelineItem
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

class RangeIdsAcrossBucketsTest {

    private val keys = listOf("2026-03", "2026-02", "2026-01")
    private val contents = mapOf(
        "2026-03" to listOf(item("m1"), item("m2")),
        // February was never loaded in the grid: it comes from contentsOf.
        "2026-02" to listOf(item("f1"), item("f2"), item("f3")),
        "2026-01" to listOf(item("j1"), item("j2"))
    )

    @Test
    fun range_covers_the_months_in_between() {
        assertEquals(
            listOf("m2", "f1", "f2", "f3", "j1"),
            rangeIdsAcrossBuckets(keys, contents, anchorId = "m2", targetId = "j1")
        )
    }

    @Test
    fun range_works_backwards_too() {
        assertEquals(
            listOf("m2", "f1", "f2", "f3", "j1"),
            rangeIdsAcrossBuckets(keys, contents, anchorId = "j1", targetId = "m2")
        )
    }

    @Test
    fun unknown_end_gives_nothing() {
        assertTrue(rangeIdsAcrossBuckets(keys, contents, anchorId = "m1", targetId = "zz").isEmpty())
    }
}
