package com.photonne.app.data.actions

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AssetContentBytes

/**
 * Wraps the two endpoints we need for the selection action bar:
 * `POST /api/assets/download-zip` for bulk downloads and
 * `GET  /api/assets/{id}/content` for single-asset shortcuts.
 *
 * Also exposes a tiny orchestration helper that creates a public
 * Photonne share link for an arbitrary set of assets by packaging
 * them into a fresh album behind the scenes — the backend only knows
 * how to share albums, never raw asset sets.
 */
class AssetActionsRepository(
    private val api: PhotonneApi,
    private val albums: AlbumsRepository
) {
    suspend fun downloadZip(
        assetIds: List<String>,
        fileName: String? = null
    ): ByteArray = api.downloadAssetsZip(assetIds, fileName)

    suspend fun downloadOriginal(assetId: String): AssetContentBytes =
        api.getAssetContent(assetId)

    /**
     * Creates a brand-new album with the given assets and returns a
     * public share link for it. The album sticks around afterwards;
     * the caller is expected to either surface it in the user's album
     * list or document the side effect.
     */
    suspend fun createShareLinkForAssets(
        assetIds: List<String>,
        albumName: String,
        allowDownload: Boolean = true
    ): SharedAssetsLink {
        val album = albums.create(name = albumName, description = null)
        if (assetIds.isNotEmpty()) {
            albums.addAssetsBatch(albumId = album.id, assetIds = assetIds)
        }
        val link = albums.createShare(
            albumId = album.id,
            expiresAt = null,
            password = null,
            allowDownload = allowDownload,
            maxViews = null
        )
        return SharedAssetsLink(albumId = album.id, url = link.url)
    }
}

data class SharedAssetsLink(val albumId: String, val url: String)
