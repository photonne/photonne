package com.photonne.app.data.asset

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.AssetPage
import kotlinx.datetime.Instant

class AssetDetailRepository(
    private val api: PhotonneApi
) {
    suspend fun getDetail(assetId: String): AssetDetail = api.getAssetDetail(assetId)

    suspend fun toggleFavorite(assetId: String): Boolean = api.toggleFavorite(assetId)

    suspend fun archive(assetIds: List<String>) {
        api.archiveAssets(assetIds)
    }

    suspend fun unarchive(assetIds: List<String>) {
        api.unarchiveAssets(assetIds)
    }

    suspend fun unarchiveAll() {
        api.unarchiveAll()
    }

    suspend fun trash(assetIds: List<String>) {
        api.trashAssets(assetIds)
    }

    suspend fun restore(assetIds: List<String>) {
        api.restoreAssets(assetIds)
    }

    suspend fun restoreAllTrash() {
        api.restoreAllTrash()
    }

    suspend fun purge(assetIds: List<String>) {
        api.purgeAssets(assetIds)
    }

    suspend fun emptyTrash() {
        api.emptyTrash()
    }

    suspend fun listArchived(cursor: Instant? = null, pageSize: Int? = null): AssetPage =
        if (pageSize != null) api.getArchivedAssets(cursor, pageSize) else api.getArchivedAssets(cursor)

    suspend fun listTrashed(cursor: Instant? = null, pageSize: Int? = null): AssetPage =
        if (pageSize != null) api.getTrashedAssets(cursor, pageSize) else api.getTrashedAssets(cursor)

    suspend fun listFavorites(cursor: Instant? = null, pageSize: Int? = null): AssetPage =
        if (pageSize != null) api.getFavoriteAssets(cursor, pageSize) else api.getFavoriteAssets(cursor)

    suspend fun updateDescription(assetId: String, description: String?) {
        api.updateAssetDescription(assetId, description)
    }
}
