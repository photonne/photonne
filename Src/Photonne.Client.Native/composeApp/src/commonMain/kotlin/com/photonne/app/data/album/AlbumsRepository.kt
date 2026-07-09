package com.photonne.app.data.album

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AlbumMemberRole
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.AlbumShareLink
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.SmartRule
import com.photonne.app.data.models.SmartAlbumPreview
import com.photonne.app.data.models.SentShareLink
import com.photonne.app.data.models.ShareUpdateResult
import com.photonne.app.data.models.ShareableUser
import com.photonne.app.data.models.TimelineItem
import kotlinx.datetime.Instant

class AlbumsRepository(
    private val api: PhotonneApi
) {
    suspend fun list(): List<AlbumSummary> = api.getAlbums()

    suspend fun get(albumId: String): AlbumSummary = api.getAlbum(albumId)

    suspend fun assets(albumId: String): List<TimelineItem> = api.getAlbumAssets(albumId)

    suspend fun create(name: String, description: String?): AlbumSummary =
        api.createAlbum(name = name, description = description)

    suspend fun createSmart(name: String, description: String?, rule: SmartRule): AlbumSummary =
        api.createSmartAlbum(name = name, description = description, rule = rule)

    suspend fun preview(rule: SmartRule, sampleSize: Int = 24): SmartAlbumPreview =
        api.previewSmartAlbum(rule = rule, sampleSize = sampleSize)

    suspend fun update(albumId: String, name: String, description: String?): AlbumSummary =
        api.updateAlbum(albumId = albumId, name = name, description = description)

    suspend fun delete(albumId: String) {
        api.deleteAlbum(albumId)
    }

    suspend fun addAsset(albumId: String, assetId: String) {
        api.addAssetToAlbum(albumId = albumId, assetId = assetId)
    }

    suspend fun addAssetsBatch(albumId: String, assetIds: List<String>) {
        api.addAssetsToAlbumBatch(albumId = albumId, assetIds = assetIds)
    }

    suspend fun removeAsset(albumId: String, assetId: String) {
        api.removeAssetFromAlbum(albumId = albumId, assetId = assetId)
    }

    suspend fun setCover(albumId: String, assetId: String): AlbumSummary =
        api.setAlbumCover(albumId = albumId, assetId = assetId)

    suspend fun leave(albumId: String) {
        api.leaveAlbum(albumId)
    }

    suspend fun listShares(albumId: String): List<AlbumShareLink> =
        api.listAlbumShares(albumId)

    suspend fun sentShares(): List<SentShareLink> = api.getSentShares()

    suspend fun createShare(
        albumId: String,
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?
    ): AlbumShareLink = api.createAlbumShare(
        albumId = albumId,
        expiresAt = expiresAt,
        password = password,
        allowDownload = allowDownload,
        maxViews = maxViews
    )

    suspend fun updateShare(
        token: String,
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?
    ): ShareUpdateResult = api.updateShare(
        token = token,
        expiresAt = expiresAt,
        password = password,
        allowDownload = allowDownload,
        maxViews = maxViews
    )

    suspend fun revokeShare(token: String) {
        api.revokeShare(token)
    }

    suspend fun shareableUsers(): List<ShareableUser> = api.getShareableUsers()

    suspend fun listMembers(albumId: String): List<AlbumPermission> =
        api.listAlbumPermissions(albumId)

    suspend fun grantMember(
        albumId: String,
        userId: String,
        role: AlbumMemberRole
    ): AlbumPermission = api.setAlbumPermission(
        albumId = albumId,
        userId = userId,
        canRead = role.canRead,
        canWrite = role.canWrite,
        canDelete = role.canDelete,
        canManagePermissions = role.canManagePermissions
    )

    suspend fun revokeMember(albumId: String, userId: String) {
        api.removeAlbumPermission(albumId, userId)
    }
}
