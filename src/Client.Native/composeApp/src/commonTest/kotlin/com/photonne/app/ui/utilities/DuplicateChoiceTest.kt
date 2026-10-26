package com.photonne.app.ui.utilities

import com.photonne.app.data.models.TimelineItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DuplicateChoiceTest {
    private fun copy(id: String, path: String, day: Int = 1, favorite: Boolean = false): TimelineItem {
        val date = Instant.parse("2024-01-${day.toString().padStart(2, '0')}T10:00:00Z")
        return TimelineItem(
            id = id,
            fileName = path.substringAfterLast('/'),
            fullPath = path,
            fileSize = 100L,
            fileCreatedAt = date,
            fileModifiedAt = date,
            extension = ".jpg",
            scannedAt = date,
            type = "IMAGE",
            isFavorite = favorite,
        )
    }

    @Test
    fun `a favourite is the best copy wherever it lives`() {
        val group = listOf(copy("a", "/fotos/x.jpg"), copy("b", "/fotos/backup/x.jpg", favorite = true))
        assertEquals("b", bestCopy(group)?.id)
    }

    @Test
    fun `otherwise the copy closest to the root, then the oldest`() {
        val group = listOf(
            copy("deep", "/fotos/backup/2024/x.jpg", day = 1),
            copy("new", "/fotos/x.jpg", day = 5),
            copy("old", "/fotos/x2.jpg", day = 2)
        )
        assertEquals("old", bestCopy(group)?.id)
    }

    @Test
    fun `the last copy left can't be marked`() {
        val group = listOf(copy("a", "/a/x.jpg"), copy("b", "/b/x.jpg"))
        val marked = toggleCopy(group, emptySet(), "a")
        assertEquals(setOf("a"), marked)
        assertEquals(setOf("a"), toggleCopy(group, marked, "b"))
        assertEquals(emptySet(), toggleCopy(group, marked, "a"))
    }

    @Test
    fun `keep only marks every other copy`() {
        val group = listOf(copy("a", "/a/x.jpg"), copy("b", "/b/x.jpg"), copy("c", "/c/x.jpg"))
        assertEquals(setOf("a", "c"), keepOnlyCopy(group, "b"))
    }

    @Test
    fun `folders split into the shared part and what sets each copy apart`() {
        assertEquals(
            listOf(FolderSplit("/fotos/2024/", "Camera"), FolderSplit("/fotos/2024/", "Backup/old")),
            splitFolders(listOf("/fotos/2024/Camera", "/fotos/2024/Backup/old"))
        )
        assertEquals(
            listOf(FolderSplit("/fotos", ""), FolderSplit("/fotos", "")),
            splitFolders(listOf("/fotos", "/fotos"))
        )
    }
}
