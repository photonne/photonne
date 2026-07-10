package com.photonne.app.data.organize

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.TimelinePage
import kotlinx.datetime.Instant

/**
 * "Para organizar" inbox: the assets still sitting under MobileBackup (dropped
 * by automatic backup, not yet filed into a folder). Moving an asset out of
 * MobileBackup is what marks it organized, so the inbox drains to zero.
 */
class OrganizeRepository(
    private val api: PhotonneApi
) {
    suspend fun inbox(cursor: Instant? = null): TimelinePage =
        api.getOrganizeInbox(cursor)

    suspend fun count(): Int =
        api.getOrganizeCount()

    /** Physical move out of MobileBackup into [targetFolderId]. Source folders
     *  differ per asset (one per device), so we pass a null source and let the
     *  server authorize per asset. */
    suspend fun moveAssets(targetFolderId: String, assetIds: List<String>) {
        api.moveFolderAssets(
            sourceFolderId = null,
            targetFolderId = targetFolderId,
            assetIds = assetIds
        )
    }
}
