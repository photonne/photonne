package com.photonne.app.ui.util

import com.photonne.app.data.models.TimelineItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private fun item(id: String, favorite: Boolean = false): TimelineItem {
    val date = Instant.parse("2024-01-01T00:00:00Z")
    return TimelineItem(
        id = id,
        fileName = "$id.jpg",
        fullPath = "/photos/$id.jpg",
        fileSize = 1L,
        fileCreatedAt = date,
        fileModifiedAt = date,
        extension = ".jpg",
        scannedAt = date,
        type = "IMAGE",
        isFavorite = favorite,
    )
}

class RestoreRemovedTest {

    private val previous = listOf("a", "b", "c", "d", "e").map { item(it) }

    @Test
    fun puts_removed_items_back_in_their_original_positions() {
        val current = previous.filterNot { it.id == "b" || it.id == "d" }
        val restored = current.withRestored(previous, setOf("b", "d"))
        assertEquals(listOf("a", "b", "c", "d", "e"), restored.map { it.id })
    }

    @Test
    fun keeps_changes_made_while_the_request_was_in_flight() {
        // "c" was favourited and "e" removed elsewhere meanwhile.
        val current = listOf(item("a"), item("c", favorite = true))
        val restored = current.withRestored(previous, setOf("b"))
        assertEquals(listOf("a", "b", "c"), restored.map { it.id })
        assertEquals(true, restored.single { it.id == "c" }.isFavorite)
    }

    @Test
    fun items_new_to_the_list_keep_their_neighbours() {
        val current = listOf(item("a"), item("x"), item("c"), item("e"))
        val restored = current.withRestored(previous, setOf("b", "d"))
        assertEquals(listOf("a", "x", "b", "c", "d", "e"), restored.map { it.id })
    }

    @Test
    fun does_not_duplicate_items_already_present() {
        val restored = previous.withRestored(previous, setOf("a", "b"))
        assertEquals(previous.map { it.id }, restored.map { it.id })
    }
}
