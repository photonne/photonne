package com.photonne.app.data.actions

import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AssetContentBytes
import com.photonne.app.data.models.DownloadFormat
import com.photonne.app.data.models.DownloadOptions

/**
 * Wraps the endpoints we need for the selection action bar:
 * `POST /api/assets/download-zip` for bulk downloads,
 * `GET  /api/assets/{id}/content` for single-asset shortcuts and
 * `POST /api/assets/download-options` to know whether there is a format to
 * ask about before either.
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
    suspend fun downloadOptions(assetIds: List<String>): DownloadOptions =
        api.getDownloadOptions(assetIds)

    /** [format] only changes the RAW and HEIC/HEIF entries; null leaves every original. */
    suspend fun downloadZip(
        assetIds: List<String>,
        fileName: String? = null,
        format: DownloadFormat? = null
    ): ByteArray = api.downloadAssetsZip(assetIds, fileName, format)

    /**
     * One asset's file. [format] only matters for a RAW or HEIC/HEIF; null is
     * what the server did before there was a choice (HEIC as JPG, RAW as it is).
     */
    suspend fun downloadAsset(assetId: String, format: DownloadFormat? = null): AssetContentBytes =
        api.getAssetContent(assetId, format)

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
            maxViews = null,
            allowUpload = false
        )
        return SharedAssetsLink(albumId = album.id, url = link.shareUrl)
    }
}

data class SharedAssetsLink(val albumId: String, val url: String)
