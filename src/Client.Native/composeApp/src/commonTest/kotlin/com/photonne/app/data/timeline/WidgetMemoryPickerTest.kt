package com.photonne.app.data.timeline

import com.photonne.app.data.models.Memory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class WidgetMemoryPickerTest {
    private fun memory(id: String, kind: String = "CuratedScene", cover: String? = "cover-$id") = Memory(
        id = id,
        kind = kind,
        title = id,
        coverAssetId = cover,
        windowStart = Instant.fromEpochSeconds(0),
        windowEnd = Instant.fromEpochSeconds(0),
    )

    @Test
    fun `on this day wins over a better scored memory`() {
        val feed = listOf(memory("beach"), memory("today", kind = "OnThisDay"))
        assertEquals("today", pickWidgetMemory(feed, epochDay = 20_000)?.id)
    }

    @Test
    fun `without on this day it rotates through the best ones day by day`() {
        val feed = listOf(memory("a"), memory("b"), memory("c"))
        assertEquals("a", pickWidgetMemory(feed, epochDay = 3)?.id)
        assertEquals("b", pickWidgetMemory(feed, epochDay = 4)?.id)
        assertEquals("b", pickWidgetMemory(feed, epochDay = 4)?.id)
    }

    @Test
    fun `memories without a cover are skipped`() {
        assertNull(pickWidgetMemory(listOf(memory("a", cover = null)), epochDay = 0))
        assertEquals("b", pickWidgetMemory(listOf(memory("a", cover = null), memory("b")), epochDay = 0)?.id)
    }

    @Test
    fun `an empty feed shows nothing`() {
        assertNull(pickWidgetMemory(emptyList(), epochDay = 0))
    }
}
