package com.photonne.app.ui.album

import com.photonne.app.data.models.RemovedAlbumAsset
import com.photonne.app.data.models.TimelineItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AlbumRemovalTest {

    private fun removal(id: String, at: String, user: String? = "u1") = RemovedAlbumAsset(
        asset = TimelineItem(
            id = id,
            fileName = "$id.jpg",
            fullPath = "/$id.jpg",
            fileSize = 1,
            fileCreatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            fileModifiedAt = Instant.parse("2026-01-01T00:00:00Z"),
            extension = ".jpg",
            scannedAt = Instant.parse("2026-01-01T00:00:00Z"),
            type = "Image"
        ),
        removedAt = Instant.parse(at),
        removedByUserId = user,
        removedByUsername = user?.let { "name-$it" }
    )

    @Test
    fun groups_what_one_removal_took_out() {
        val groups = groupRemovals(
            listOf(
                removal("a", "2026-10-25T10:00:00Z"),
                removal("b", "2026-10-25T10:00:00Z"),
                removal("c", "2026-10-25T09:00:00Z"),
                removal("d", "2026-10-25T09:00:00Z", user = "u2"),
                removal("e", "2026-10-25T09:00:00Z", user = null)
            )
        )

        assertEquals(
            listOf(listOf("a", "b"), listOf("c"), listOf("d"), listOf("e")),
            groups.map { group -> group.items.map { it.asset.id } }
        )
        assertEquals("name-u1", groups[0].removedBy)
        assertNull(groups[3].removedBy)
    }

    @Test
    fun nothing_removed_means_no_groups() {
        assertTrue(groupRemovals(emptyList()).isEmpty())
    }

    @Test
    fun asks_only_for_large_removals() {
        assertFalse(albumRemovalNeedsConfirm(1))
        assertFalse(albumRemovalNeedsConfirm(ALBUM_REMOVE_CONFIRM_FROM - 1))
        assertTrue(albumRemovalNeedsConfirm(ALBUM_REMOVE_CONFIRM_FROM))
    }
}
