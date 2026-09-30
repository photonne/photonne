package com.photonne.app.ui.album

import com.photonne.app.data.models.AlbumSummary
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlbumSelectionActionsTest {

    private val at = Instant.parse("2026-09-30T08:00:00Z")

    private fun album(
        id: String,
        isOwner: Boolean = true,
        canWrite: Boolean = false,
        canDelete: Boolean = false,
        canManagePermissions: Boolean = false,
    ) = AlbumSummary(
        id = id,
        name = id,
        createdAt = at,
        updatedAt = at,
        isOwner = isOwner,
        canWrite = canWrite,
        canDelete = canDelete,
        canManagePermissions = canManagePermissions
    )

    @Test
    fun singleOwnedAlbumKeepsTheUsualActions() {
        val actions = albumSelectionActions(listOf(album("a")))
        assertEquals(
            AlbumSelectionActions(canEdit = true, canManageMembers = true, canLeave = false, canDelete = true),
            actions
        )
    }

    @Test
    fun singleSharedWithMeAlbumCanBeLeftButNotDeletedWithoutPermission() {
        val actions = albumSelectionActions(listOf(album("a", isOwner = false)))
        assertTrue(actions.canLeave)
        assertFalse(actions.canDelete)
        assertFalse(actions.canEdit)
        assertFalse(actions.canManageMembers)
    }

    @Test
    fun severalAlbumsDropPerAlbumActions() {
        val actions = albumSelectionActions(listOf(album("a"), album("b")))
        assertFalse(actions.canEdit)
        assertFalse(actions.canManageMembers)
        assertTrue(actions.canDelete)
    }

    @Test
    fun deleteNeedsEveryAlbumDeletable() {
        val mixed = listOf(album("a"), album("b", isOwner = false))
        assertFalse(albumSelectionActions(mixed).canDelete)
        val granted = listOf(album("a"), album("b", isOwner = false, canDelete = true))
        assertTrue(albumSelectionActions(granted).canDelete)
    }

    @Test
    fun leaveNeedsNoOwnedAlbum() {
        val mixed = listOf(album("a"), album("b", isOwner = false))
        assertFalse(albumSelectionActions(mixed).canLeave)
        val shared = listOf(album("a", isOwner = false), album("b", isOwner = false))
        assertTrue(albumSelectionActions(shared).canLeave)
    }

    @Test
    fun emptySelectionOffersNothing() {
        val actions = albumSelectionActions(emptyList())
        assertFalse(actions.canEdit || actions.canManageMembers || actions.canLeave || actions.canDelete)
    }
}
