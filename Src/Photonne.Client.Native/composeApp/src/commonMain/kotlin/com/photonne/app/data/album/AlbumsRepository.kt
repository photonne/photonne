package com.photonne.app.data.album

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.TimelineItem

class AlbumsRepository(
    private val api: PhotonneApi
) {
    suspend fun list(): List<AlbumSummary> = api.getAlbums()

    suspend fun assets(albumId: String): List<TimelineItem> = api.getAlbumAssets(albumId)

    suspend fun create(name: String, description: String?): AlbumSummary =
        api.createAlbum(name = name, description = description)

    suspend fun update(albumId: String, name: String, description: String?): AlbumSummary =
        api.updateAlbum(albumId = albumId, name = name, description = description)

    suspend fun delete(albumId: String) {
        api.deleteAlbum(albumId)
    }

    suspend fun addAsset(albumId: String, assetId: String) {
        api.addAssetToAlbum(albumId = albumId, assetId = assetId)
    }
}
