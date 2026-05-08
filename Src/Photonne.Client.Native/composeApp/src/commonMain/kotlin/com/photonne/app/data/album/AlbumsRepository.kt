package com.photonne.app.data.album

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.TimelineItem

class AlbumsRepository(
    private val api: PhotonneApi
) {
    suspend fun list(): List<AlbumSummary> = api.getAlbums()

    suspend fun assets(albumId: String): List<TimelineItem> = api.getAlbumAssets(albumId)
}
