package com.photonne.app.data.folder

import com.photonne.app.data.models.FolderSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalFolderFilterTest {

    private val username = "alice"

    private fun folder(path: String, id: String = path) = FolderSummary(
        id = id,
        path = path,
        name = path.substringAfterLast('/'),
        createdAt = Instant.parse("2026-05-09T08:00:00Z")
    )

    @Test
    fun keeps_only_descendants_of_user_home() {
        val input = listOf(
            folder("/assets/users/$username"),
            folder("/assets/users/$username/Uploads"),
            folder("/assets/users/$username/Uploads/2026"),
            folder("/assets/users/bob/Uploads"),
            folder("/assets/shared/Family"),
            folder("/external/library/Photos")
        )

        val visible = filterPersonalFolders(input, username)

        assertEquals(
            listOf(
                "/assets/users/$username/Uploads",
                "/assets/users/$username/Uploads/2026"
            ),
            visible.map { it.path }
        )
    }

    @Test
    fun hides_trash_and_archive_branches() {
        val input = listOf(
            folder("/assets/users/$username/Uploads"),
            folder("/assets/users/$username/_trash"),
            folder("/assets/users/$username/_trash/2026-05-10"),
            folder("/assets/users/$username/_archive"),
            folder("/assets/users/$username/_archive/old")
        )

        val visible = filterPersonalFolders(input, username)

        assertEquals(listOf("/assets/users/$username/Uploads"), visible.map { it.path })
    }

    @Test
    fun matches_path_case_insensitively() {
        val mixed = "/Assets/Users/$username/Photos"
        val visible = filterPersonalFolders(listOf(folder(mixed)), username)
        assertEquals(listOf(mixed), visible.map { it.path })
    }

    @Test
    fun returns_empty_when_username_is_blank() {
        val visible = filterPersonalFolders(
            listOf(folder("/assets/users/$username/Uploads")),
            username = ""
        )
        assertTrue(visible.isEmpty())
    }
}
