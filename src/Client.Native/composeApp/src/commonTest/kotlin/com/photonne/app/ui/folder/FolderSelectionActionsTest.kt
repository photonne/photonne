package com.photonne.app.ui.folder

import com.photonne.app.data.models.FolderSummary
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderSelectionActionsTest {

    private fun folder(
        path: String,
        isOwner: Boolean = true,
        isShared: Boolean = false,
        canWrite: Boolean = true,
        canDelete: Boolean = true,
        externalLibraryId: String? = null,
    ) = FolderSummary(
        id = path,
        path = path,
        name = path.substringAfterLast('/'),
        createdAt = Instant.parse("2026-09-30T08:00:00Z"),
        isOwner = isOwner,
        isShared = isShared,
        canWrite = canWrite,
        canDelete = canDelete,
        externalLibraryId = externalLibraryId
    )

    @Test
    fun singleSharedFolderKeepsTheUsualActions() {
        val actions = folderSelectionActions(listOf(folder("/assets/shared/Family", isShared = true)))
        assertEquals(
            FolderSelectionActions(
                canRename = true,
                canManageMembers = true,
                canToggleTimeline = true,
                canMove = true,
                canDelete = true,
                canPin = true
            ),
            actions
        )
    }

    @Test
    fun externalLibraryIsNeverRenamedMovedOrDeleted() {
        val actions = folderSelectionActions(listOf(folder("/mnt/photos", externalLibraryId = "lib")))
        assertFalse(actions.canRename)
        assertFalse(actions.canMove)
        assertFalse(actions.canDelete)
        assertFalse(actions.canToggleTimeline)
    }

    @Test
    fun severalFoldersKeepOnlyBulkActions() {
        val actions = folderSelectionActions(
            listOf(folder("/assets/users/a/X"), folder("/assets/users/a/Y"))
        )
        assertFalse(actions.canRename)
        assertFalse(actions.canManageMembers)
        assertFalse(actions.canToggleTimeline)
        assertTrue(actions.canMove)
        assertTrue(actions.canDelete)
        // Fijar es de una carpeta concreta, como en los álbumes.
        assertFalse(actions.canPin)
    }

    @Test
    fun oneBlockedFolderHidesTheActionForAll() {
        val selection = listOf(
            folder("/assets/users/a/X"),
            folder("/assets/shared/Y", isShared = true, canWrite = false, canDelete = false)
        )
        val actions = folderSelectionActions(selection)
        assertFalse(actions.canMove)
        assertFalse(actions.canDelete)
        val withExternal = listOf(folder("/assets/users/a/X"), folder("/mnt/p", externalLibraryId = "lib"))
        assertFalse(folderSelectionActions(withExternal).canDelete)
    }

    @Test
    fun topmostFoldersDropsDescendantsOfOtherSelectedFolders() {
        val parent = folder("/assets/users/a/Viajes")
        val child = folder("/assets/users/a/Viajes/Japon")
        val sibling = folder("/assets/users/a/Viajes 2024")
        assertEquals(
            listOf(parent, sibling),
            topmostFolders(listOf(child, parent, sibling))
        )
    }
}
