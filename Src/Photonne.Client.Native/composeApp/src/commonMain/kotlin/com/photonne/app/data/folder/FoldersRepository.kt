package com.photonne.app.data.folder

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AlbumPermission
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.TimelineItem

class FoldersRepository(
    private val api: PhotonneApi
) {
    suspend fun list(): List<FolderSummary> = api.getFolders()

    suspend fun get(folderId: String): FolderSummary = api.getFolder(folderId)

    suspend fun assets(folderId: String): List<TimelineItem> = api.getFolderAssets(folderId)

    suspend fun create(name: String, parentFolderId: String?, isSharedSpace: Boolean): FolderSummary =
        api.createFolder(name = name, parentFolderId = parentFolderId, isSharedSpace = isSharedSpace)

    suspend fun update(folderId: String, name: String, parentFolderId: String?): FolderSummary =
        api.updateFolder(folderId = folderId, name = name, parentFolderId = parentFolderId)

    suspend fun delete(folderId: String) {
        api.deleteFolder(folderId)
    }

    /** Per-user opt-out: include/exclude a shared folder from my timeline, memories, people and search. */
    suspend fun setTimelineIncluded(folderId: String, included: Boolean) {
        api.setFolderTimelineIncluded(folderId, included)
    }

    suspend fun listMembers(folderId: String): List<AlbumPermission> =
        api.listFolderPermissions(folderId)

    suspend fun grantMember(
        folderId: String,
        userId: String,
        role: com.photonne.app.data.models.AlbumMemberRole
    ): AlbumPermission = api.setFolderPermission(
        folderId = folderId,
        userId = userId,
        canRead = role.canRead,
        canWrite = role.canWrite,
        canDelete = role.canDelete,
        canManagePermissions = role.canManagePermissions
    )

    suspend fun revokeMember(folderId: String, userId: String) {
        api.removeFolderPermission(folderId, userId)
    }

    suspend fun moveAssets(
        sourceFolderId: String?,
        targetFolderId: String,
        assetIds: List<String>
    ) {
        api.moveFolderAssets(
            sourceFolderId = sourceFolderId,
            targetFolderId = targetFolderId,
            assetIds = assetIds
        )
    }
}
