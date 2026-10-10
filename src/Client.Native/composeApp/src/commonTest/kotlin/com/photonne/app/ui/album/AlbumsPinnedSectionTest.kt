package com.photonne.app.ui.album

import com.photonne.app.data.models.AlbumSummary
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlbumsPinnedSectionTest {

    private fun album(id: String, created: String, pinnedAt: String? = null, isOwner: Boolean = true) =
        AlbumSummary(
            id = id,
            name = id,
            createdAt = Instant.parse(created),
            updatedAt = Instant.parse(created),
            isOwner = isOwner,
            isPinned = pinnedAt != null,
            pinnedAt = pinnedAt?.let { Instant.parse(it) }
        )

    private val old = album("old", "2026-01-01T00:00:00Z", pinnedAt = "2026-09-01T00:00:00Z")
    private val recentPin = album("recentPin", "2026-02-01T00:00:00Z", pinnedAt = "2026-09-20T00:00:00Z")
    private val plain = album("plain", "2026-03-01T00:00:00Z")
    private val theirs = album("theirs", "2026-04-01T00:00:00Z", isOwner = false)
    private val all = listOf(old, recentPin, plain, theirs)

    @Test
    fun pinnedGoFirstByMostRecentPinAndAreNotRepeatedBelow() {
        val state = AlbumsUiState(albums = all)
        assertTrue(state.showsPinnedSection)
        assertEquals(listOf("recentPin", "old"), state.pinnedAlbums.map { it.id })
        // Debajo, el orden elegido (fecha descendente) sin los fijados.
        assertEquals(listOf("theirs", "plain"), state.unpinnedAlbums.map { it.id })
    }

    @Test
    fun searchingHidesTheSectionAndKeepsEveryResult() {
        val state = AlbumsUiState(albums = all, searchQuery = "o")
        assertFalse(state.showsPinnedSection)
        assertTrue(state.pinnedAlbums.isEmpty())
        assertEquals(state.visibleAlbums, state.unpinnedAlbums)
    }

    @Test
    fun noPinsMeansNoSection() {
        val state = AlbumsUiState(albums = listOf(plain, theirs))
        assertFalse(state.showsPinnedSection)
        assertEquals(state.visibleAlbums, state.unpinnedAlbums)
    }

    @Test
    fun scopeStillFiltersThePinnedSection() {
        val state = AlbumsUiState(albums = all, scope = AlbumsScope.Shared)
        assertTrue(state.pinnedAlbums.isEmpty())
        assertEquals(listOf("theirs"), state.unpinnedAlbums.map { it.id })
    }
}
