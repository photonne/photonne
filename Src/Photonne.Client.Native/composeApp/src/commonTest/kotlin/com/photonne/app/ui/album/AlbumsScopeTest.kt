package com.photonne.app.ui.album

import com.photonne.app.data.models.AlbumSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AlbumsScopeTest {

    private fun album(
        id: String,
        isOwner: Boolean = true,
        isShared: Boolean = false
    ) = AlbumSummary(
        id = id,
        name = id,
        createdAt = Instant.parse("2026-05-09T08:00:00Z"),
        updatedAt = Instant.parse("2026-05-09T08:00:00Z"),
        isOwner = isOwner,
        isShared = isShared
    )

    private val mine = album("mine")
    private val mineShared = album("mine-shared", isOwner = true, isShared = true)
    private val theirs = album("theirs", isOwner = false)
    private val all = listOf(mine, mineShared, theirs)

    private fun state(scope: AlbumsScope) = AlbumsUiState(albums = all, scope = scope)

    @Test
    fun all_scope_returns_every_album() {
        assertEquals(
            all.map { it.id }.toSet(),
            state(AlbumsScope.All).visibleAlbums.map { it.id }.toSet()
        )
    }

    @Test
    fun mine_scope_excludes_albums_i_shared_with_others() {
        assertEquals(listOf("mine"), state(AlbumsScope.Mine).visibleAlbums.map { it.id })
    }

    @Test
    fun shared_scope_covers_both_directions_of_sharing() {
        assertEquals(
            setOf("mine-shared", "theirs"),
            state(AlbumsScope.Shared).visibleAlbums.map { it.id }.toSet()
        )
    }

    /**
     * A property rather than an example: Mine and Shared must tile All exactly,
     * or "Todos" is lying. Catches any future tweak to the predicates that opens
     * a gap or an overlap.
     */
    @Test
    fun mine_and_shared_partition_all_exactly() {
        val everything = state(AlbumsScope.All).visibleAlbums.map { it.id }.toSet()
        val mineIds = state(AlbumsScope.Mine).visibleAlbums.map { it.id }.toSet()
        val sharedIds = state(AlbumsScope.Shared).visibleAlbums.map { it.id }.toSet()

        assertEquals(emptySet(), mineIds intersect sharedIds, "scopes must not overlap")
        assertEquals(everything, mineIds + sharedIds, "scopes must cover every album")
    }

    @Test
    fun filter_active_only_outside_all() {
        assertEquals(false, state(AlbumsScope.All).isFilterActive)
        assertEquals(true, state(AlbumsScope.Mine).isFilterActive)
        assertEquals(true, state(AlbumsScope.Shared).isFilterActive)
    }
}
