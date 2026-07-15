package com.photonne.app.ui.folder

import com.photonne.app.data.models.FolderSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises [FoldersUiState.visibleFolders] — the list the screen actually renders. */
class FoldersScopeTest {

    private fun folder(
        path: String,
        id: String = path,
        isShared: Boolean = false,
        externalLibraryId: String? = null
    ) = FolderSummary(
        id = id,
        path = path,
        name = path.substringAfterLast('/'),
        createdAt = Instant.parse("2026-05-09T08:00:00Z"),
        isShared = isShared,
        externalLibraryId = externalLibraryId
    )

    private val personal = folder("/assets/users/alice/Uploads", id = "p1")
    private val shared = folder("/assets/shared/Family", id = "s1", isShared = true)
    private val external = folder("/mnt/photos", id = "e1", externalLibraryId = "lib1")
    private val nested = folder("/assets/users/alice/Viajes/Japon", id = "p2")

    private fun state(
        scope: FoldersScope,
        query: String = ""
    ) = FoldersUiState(
        personalFolders = listOf(personal),
        sharedFolders = listOf(shared),
        externalRoots = listOf(external),
        personalDescendants = listOf(personal, nested),
        sharedDescendants = listOf(shared),
        externalDescendants = listOf(external),
        scope = scope,
        searchQuery = query
    )

    @Test
    fun all_scope_shows_every_bucket() {
        assertEquals(
            setOf("p1", "s1", "e1"),
            state(FoldersScope.All).visibleFolders.map { it.id }.toSet()
        )
    }

    @Test
    fun each_scope_shows_only_its_bucket() {
        assertEquals(listOf("p1"), state(FoldersScope.Personal).visibleFolders.map { it.id })
        assertEquals(listOf("s1"), state(FoldersScope.Shared).visibleFolders.map { it.id })
        assertEquals(listOf("e1"), state(FoldersScope.External).visibleFolders.map { it.id })
    }

    /** A duplicate id would make LazyColumn throw, not just repeat a row. */
    @Test
    fun all_scope_never_yields_duplicate_ids() {
        val dupe = folder("/assets/shared/Lib", id = "dupe", isShared = true, externalLibraryId = "lib2")
        val s = FoldersUiState(
            sharedFolders = listOf(dupe),
            externalRoots = listOf(dupe),
            scope = FoldersScope.All
        )

        assertEquals(listOf("dupe"), s.visibleFolders.map { it.id })
    }

    @Test
    fun search_reaches_nested_folders_in_personal_scope() {
        val hits = state(FoldersScope.Personal, query = "japon").visibleFolders
        assertEquals(listOf("p2"), hits.map { it.id })
    }

    @Test
    fun search_in_all_scope_spans_every_subtree() {
        // "japon" only exists below a personal root, "family" is shared, "photos"
        // is an external root: All has to reach all three.
        assertEquals(
            listOf("p2"),
            state(FoldersScope.All, query = "japon").visibleFolders.map { it.id }
        )
        assertEquals(
            listOf("s1"),
            state(FoldersScope.All, query = "family").visibleFolders.map { it.id }
        )
        assertEquals(
            listOf("e1"),
            state(FoldersScope.All, query = "photos").visibleFolders.map { it.id }
        )
    }

    @Test
    fun browsing_shows_roots_not_descendants() {
        val browsing = state(FoldersScope.Personal).visibleFolders
        assertTrue(browsing.none { it.id == "p2" }, "nested folder must not appear while browsing")
    }

    @Test
    fun filter_active_only_outside_all() {
        assertEquals(false, state(FoldersScope.All).isFilterActive)
        assertEquals(true, state(FoldersScope.External).isFilterActive)
    }

    @Test
    fun find_folder_reaches_external_roots_and_nested_folders() {
        val s = state(FoldersScope.All)
        assertEquals("e1", s.findFolder("e1")?.id)
        assertEquals("p2", s.findFolder("p2")?.id)
        assertEquals(null, s.findFolder("nope"))
        assertEquals(null, s.findFolder(null))
    }
}
