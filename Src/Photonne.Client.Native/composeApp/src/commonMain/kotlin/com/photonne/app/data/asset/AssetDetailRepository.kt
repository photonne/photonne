package com.photonne.app.data.asset

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AssetDetail

class AssetDetailRepository(
    private val api: PhotonneApi
) {
    suspend fun getDetail(assetId: String): AssetDetail = api.getAssetDetail(assetId)

    suspend fun toggleFavorite(assetId: String): Boolean = api.toggleFavorite(assetId)

    suspend fun archive(assetIds: List<String>) {
        api.archiveAssets(assetIds)
    }

    suspend fun trash(assetIds: List<String>) {
        api.trashAssets(assetIds)
    }

    suspend fun updateDescription(assetId: String, description: String?) {
        api.updateAssetDescription(assetId, description)
    }
}
