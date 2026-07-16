package com.photonne.app.ui.folder

import com.photonne.app.data.models.FolderSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderForestTest {

    private fun folder(
        id: String,
        name: String,
        parentFolderId: String? = null,
        isShared: Boolean = false,
        path: String = name,
    ) = FolderSummary(
        id = id,
        path = path,
        name = name,
        parentFolderId = parentFolderId,
        createdAt = Instant.parse("2026-05-09T08:00:00Z"),
        isShared = isShared,
    )

    @Test
    fun builds_nested_tree_from_parent_ids() {
        val all = listOf(
            folder("v", "Viajes"),
            folder("j", "Japon", parentFolderId = "v"),
            folder("t", "Tokio", parentFolderId = "j"),
        )

        val roots = buildFolderForest(all)

        assertEquals(listOf("Viajes"), roots.map { it.name })
        val japon = roots.single().children.single()
        assertEquals("Japon", japon.name)
        assertEquals(listOf("Tokio"), japon.children.map { it.name })
    }

    @Test
    fun orphans_whose_parent_is_missing_become_roots() {
        // The moved folder's subtree is pruned before building, so a child can be
        // left pointing at an absent parent — it must still surface as a root.
        val all = listOf(
            folder("j", "Japon", parentFolderId = "v"), // parent "v" not in the list
            folder("t", "Tokio", parentFolderId = "j"),
        )

        val roots = buildFolderForest(all)

        assertEquals(listOf("Japon"), roots.map { it.name })
        assertEquals(listOf("Tokio"), roots.single().children.map { it.name })
    }

    @Test
    fun children_and_roots_are_sorted_by_name_case_insensitively() {
        val all = listOf(
            folder("b", "banana"),
            folder("a", "Apple"),
            folder("c1", "zebra", parentFolderId = "a"),
            folder("c2", "Ant", parentFolderId = "a"),
        )

        val roots = buildFolderForest(all)

        assertEquals(listOf("Apple", "banana"), roots.map { it.name })
        assertEquals(listOf("Ant", "zebra"), roots.first().children.map { it.name })
    }

    @Test
    fun filter_keeps_matching_branch_and_its_ancestors() {
        val roots = buildFolderForest(
            listOf(
                folder("v", "Viajes"),
                folder("j", "Japon", parentFolderId = "v"),
                folder("t", "Tokio", parentFolderId = "j"),
                folder("f", "Familia"),
            )
        )

        val filtered = filterFolderTree(roots, "tokio")

        assertEquals(listOf("Viajes"), filtered.map { it.name })
        assertEquals("Japon", filtered.single().children.single().name)
        assertEquals("Tokio", filtered.single().children.single().children.single().name)
    }

    @Test
    fun collect_folder_ids_returns_every_node() {
        val roots = buildFolderForest(
            listOf(
                folder("v", "Viajes"),
                folder("j", "Japon", parentFolderId = "v"),
                folder("t", "Tokio", parentFolderId = "j"),
            )
        )

        assertEquals(setOf("v", "j", "t"), collectFolderIds(roots))
    }

    @Test
    fun toggle_member_adds_and_removes() {
        assertTrue("x" in emptySet<String>().toggleMember("x"))
        assertTrue("x" !in setOf("x").toggleMember("x"))
    }
}
