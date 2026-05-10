package com.photonne.app.data.folder

import com.photonne.app.data.models.FolderSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalFolderFilterTest {

    private val userId = "11111111-1111-1111-1111-111111111111"

    private fun folder(path: String, id: String = path) = FolderSummary(
        id = id,
        path = path,
        name = path.substringAfterLast('/'),
        createdAt = Instant.parse("2026-05-09T08:00:00Z")
    )

    @Test
    fun keeps_only_descendants_of_user_home() {
        val input = listOf(
            folder("/assets/users/$userId"),
            folder("/assets/users/$userId/Uploads"),
            folder("/assets/users/$userId/Uploads/2026"),
            folder("/assets/users/22222222-2222-2222-2222-222222222222/Uploads"),
            folder("/assets/shared/Family"),
            folder("/external/library/Photos")
        )

        val visible = filterPersonalFolders(input, userId)

        assertEquals(
            listOf(
                "/assets/users/$userId/Uploads",
                "/assets/users/$userId/Uploads/2026"
            ),
            visible.map { it.path }
        )
    }

    @Test
    fun hides_trash_and_archive_branches() {
        val input = listOf(
            folder("/assets/users/$userId/Uploads"),
            folder("/assets/users/$userId/_trash"),
            folder("/assets/users/$userId/_trash/2026-05-10"),
            folder("/assets/users/$userId/_archive"),
            folder("/assets/users/$userId/_archive/old")
        )

        val visible = filterPersonalFolders(input, userId)

        assertEquals(listOf("/assets/users/$userId/Uploads"), visible.map { it.path })
    }

    @Test
    fun matches_path_case_insensitively() {
        val mixed = "/Assets/Users/$userId/Photos"
        val visible = filterPersonalFolders(listOf(folder(mixed)), userId)
        assertEquals(listOf(mixed), visible.map { it.path })
    }

    @Test
    fun returns_empty_when_user_id_is_blank() {
        val visible = filterPersonalFolders(
            listOf(folder("/assets/users/$userId/Uploads")),
            userId = ""
        )
        assertTrue(visible.isEmpty())
    }
}
