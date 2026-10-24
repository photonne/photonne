package com.photonne.app.ui.collections

import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.FolderSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class MergePinnedTest {

    private fun album(id: String, pinnedAt: String, position: Int? = null) = AlbumSummary(
        id = id,
        name = id,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        isPinned = true,
        pinnedAt = Instant.parse(pinnedAt),
        pinPosition = position
    )

    private fun folder(id: String, pinnedAt: String, position: Int? = null) = FolderSummary(
        id = id,
        name = id,
        path = "/assets/users/ana/$id",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        isPinned = true,
        pinnedAt = Instant.parse(pinnedAt),
        pinPosition = position
    )

    private fun keys(albums: List<AlbumSummary>, folders: List<FolderSummary>) =
        mergePinned(albums, folders).map { it.key }

    @Test
    fun followsTheUsersOrderAcrossAlbumsAndFolders() {
        assertEquals(
            listOf("folder:camara", "album:viaje", "album:perros"),
            keys(
                listOf(
                    album("perros", "2026-10-01T00:00:00Z", position = 2),
                    album("viaje", "2026-09-01T00:00:00Z", position = 1)
                ),
                listOf(folder("camara", "2026-09-15T00:00:00Z", position = 0))
            )
        )
    }

    @Test
    fun aPinJustMadeHereGoesOnTop() {
        assertEquals(
            listOf("album:nuevo", "folder:camara", "album:viaje"),
            keys(
                listOf(
                    album("viaje", "2026-09-01T00:00:00Z", position = 1),
                    album("nuevo", "2026-10-20T00:00:00Z")
                ),
                listOf(folder("camara", "2026-09-15T00:00:00Z", position = 0))
            )
        )
    }

    @Test
    fun withoutPositionsTheLastPinnedGoesFirst() {
        assertEquals(
            listOf("album:perros", "folder:camara", "album:viaje"),
            keys(
                listOf(album("viaje", "2026-09-01T00:00:00Z"), album("perros", "2026-10-01T00:00:00Z")),
                listOf(folder("camara", "2026-09-15T00:00:00Z"))
            )
        )
    }
}
