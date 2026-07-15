package com.photonne.app.ui.folder

import com.photonne.app.data.models.FolderSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderPartitionTest {

    private val username = "alice"

    private fun folder(
        path: String,
        id: String = path,
        isShared: Boolean = path.startsWith("/assets/shared", ignoreCase = true),
        externalLibraryId: String? = null,
        parentFolderId: String? = null
    ) = FolderSummary(
        id = id,
        path = path,
        name = path.substringAfterLast('/'),
        parentFolderId = parentFolderId,
        createdAt = Instant.parse("2026-05-09T08:00:00Z"),
        isShared = isShared,
        externalLibraryId = externalLibraryId
    )

    @Test
    fun all_scope_lists_personal_shared_and_external_roots() {
        val all = listOf(
            folder("/assets/users/$username/Uploads"),
            folder("/assets/shared/Family"),
            folder("/mnt/photos", id = "lib-root", externalLibraryId = "lib1")
        )

        val p = partitionFolders(all, username)

        assertEquals(listOf("/assets/users/$username/Uploads"), p.personalRoots.map { it.path })
        assertEquals(listOf("/assets/shared/Family"), p.sharedRoots.map { it.path })
        assertEquals(listOf("/mnt/photos"), p.externalRoots.map { it.path })
    }

    /**
     * The buckets are only disjoint if the library guard is applied: an admin can
     * point a library at a path inside the shared space, and a duplicate id makes
     * LazyColumn throw.
     */
    @Test
    fun library_mounted_under_shared_space_lands_in_one_bucket_only() {
        val all = listOf(
            folder("/assets/shared/Lib", id = "lib-root", externalLibraryId = "lib1"),
            folder("/assets/shared/Family")
        )

        val p = partitionFolders(all, username)

        assertEquals(listOf("/assets/shared/Family"), p.sharedRoots.map { it.path })
        assertEquals(listOf("lib-root"), p.externalRoots.map { it.id })
        val allRoots = p.personalRoots + p.sharedRoots + p.externalRoots
        assertEquals(allRoots.size, allRoots.distinctBy { it.id }.size)
    }

    @Test
    fun library_mounted_under_user_home_lands_in_one_bucket_only() {
        val all = listOf(
            folder("/assets/users/$username/Lib", id = "lib-root", externalLibraryId = "lib1"),
            folder("/assets/users/$username/Uploads")
        )

        val p = partitionFolders(all, username)

        assertEquals(listOf("/assets/users/$username/Uploads"), p.personalRoots.map { it.path })
        assertEquals(listOf("lib-root"), p.externalRoots.map { it.id })
        val allRoots = p.personalRoots + p.sharedRoots + p.externalRoots
        assertEquals(allRoots.size, allRoots.distinctBy { it.id }.size)
    }

    @Test
    fun external_scope_shows_roots_not_descendants() {
        val all = listOf(
            folder("/mnt/photos", id = "root", externalLibraryId = "lib1"),
            folder("/mnt/photos/2024", id = "y", externalLibraryId = "lib1", parentFolderId = "root"),
            folder("/mnt/photos/2024/Jul", id = "m", externalLibraryId = "lib1", parentFolderId = "y")
        )

        val p = partitionFolders(all, username)

        assertEquals(listOf("root"), p.externalRoots.map { it.id })
        assertEquals(3, p.externalDescendants.size)
    }

    @Test
    fun external_roots_resolve_one_per_library() {
        val all = listOf(
            folder("/mnt/a", id = "a-root", externalLibraryId = "lib1"),
            folder("/mnt/a/sub", id = "a-sub", externalLibraryId = "lib1", parentFolderId = "a-root"),
            folder("/mnt/b", id = "b-root", externalLibraryId = "lib2")
        )

        val p = partitionFolders(all, username)

        assertEquals(setOf("a-root", "b-root"), p.externalRoots.map { it.id }.toSet())
    }

    @Test
    fun personal_scope_excludes_shared_and_external() {
        val all = listOf(
            folder("/assets/users/$username/Uploads"),
            folder("/assets/users/$username/Team", isShared = true),
            folder("/assets/users/$username/Lib", externalLibraryId = "lib1")
        )

        val p = partitionFolders(all, username)

        assertEquals(listOf("/assets/users/$username/Uploads"), p.personalRoots.map { it.path })
    }

    /** Only the personal bucket depends on knowing who you are. */
    @Test
    fun blank_username_drops_personal_but_keeps_shared_and_external() {
        val all = listOf(
            folder("/assets/users/$username/Uploads"),
            folder("/assets/shared/Family"),
            folder("/mnt/photos", id = "lib-root", externalLibraryId = "lib1")
        )

        val p = partitionFolders(all, username = "")

        assertTrue(p.personalRoots.isEmpty())
        assertTrue(p.personalDescendants.isEmpty())
        assertEquals(listOf("/assets/shared/Family"), p.sharedRoots.map { it.path })
        assertEquals(listOf("lib-root"), p.externalRoots.map { it.id })
    }

    // --- search subtrees -----------------------------------------------------

    /**
     * `GET /api/folders` never populates `subFolders`, so the old flattenFolders()
     * was a no-op and searching Personales only ever matched direct children.
     */
    @Test
    fun personal_descendants_reach_nested_folders() {
        val all = listOf(
            folder("/assets/users/$username/Viajes"),
            folder("/assets/users/$username/Viajes/Japon"),
            folder("/assets/users/$username/Viajes/Japon/Tokio")
        )

        val p = partitionFolders(all, username)

        assertEquals(1, p.personalRoots.size)
        assertEquals(3, p.personalDescendants.size)
        assertTrue(p.personalDescendants.any { it.name == "Tokio" })
    }

    @Test
    fun personal_descendants_exclude_system_folders_at_any_depth() {
        val all = listOf(
            folder("/assets/users/$username/Uploads"),
            folder("/assets/users/$username/_trash"),
            folder("/assets/users/$username/_trash/2026-05-10"),
            folder("/assets/users/$username/_archive/old")
        )

        val p = partitionFolders(all, username)

        assertEquals(listOf("/assets/users/$username/Uploads"), p.personalDescendants.map { it.path })
    }

    @Test
    fun shared_descendants_reach_nested_folders() {
        val all = listOf(
            folder("/assets/shared/Family"),
            folder("/assets/shared/Family/2024"),
            folder("/assets/users/$username/Uploads")
        )

        val p = partitionFolders(all, username)

        assertEquals(2, p.sharedDescendants.size)
    }
}
