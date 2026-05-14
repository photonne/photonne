package com.photonne.app.data.utilities

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.FolderTreeNode
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.UserDuplicateGroup

/**
 * Backs the three sub-pages of the Utilities hub. Each call hits a
 * dedicated `/api/utilities/...` endpoint that's user-scoped on the
 * server (not admin-gated), so this repository doesn't need to know
 * anything about the caller's role.
 *
 * Bulk delete from the duplicates sub-page reuses the same
 * `/api/assets/delete` endpoint that the timeline's "Move to trash"
 * action already calls via [PhotonneApi.trashAssets], which keeps
 * the trash bookkeeping and per-user quota in sync without us
 * inventing a parallel pipeline.
 */
class UtilitiesRepository(private val api: PhotonneApi) {

    suspend fun duplicates(): List<UserDuplicateGroup> = api.utilitiesDuplicates()

    suspend fun largeFiles(count: Int): List<TimelineItem> =
        api.utilitiesLargeFiles(count.coerceIn(1, 200))

    suspend fun folderTree(): List<FolderTreeNode> = api.utilitiesFolderTree()

    suspend fun deleteAssets(assetIds: List<String>) {
        if (assetIds.isEmpty()) return
        api.trashAssets(assetIds)
    }
}
