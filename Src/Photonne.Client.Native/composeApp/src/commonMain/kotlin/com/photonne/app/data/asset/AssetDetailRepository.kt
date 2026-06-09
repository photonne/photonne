package com.photonne.app.data.asset

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.AssetPage
import com.photonne.app.data.models.Face
import com.photonne.app.data.models.PersonAssetsPage
import kotlinx.datetime.Instant

class AssetDetailRepository(
    private val api: PhotonneApi
) {
    suspend fun getDetail(assetId: String): AssetDetail = api.getAssetDetail(assetId)

    suspend fun getFaces(assetId: String): List<Face> = api.getAssetFaces(assetId)

    suspend fun getPersonAssets(personId: String, limit: Int = 12): PersonAssetsPage =
        api.getPersonAssets(personId, limit = limit, offset = 0)

    suspend fun getSameDay(assetId: String, limit: Int = 12): PersonAssetsPage =
        api.getSameDayAssets(assetId, limit = limit)

    suspend fun addTags(assetId: String, tags: List<String>): List<String> =
        api.addAssetTags(assetId, tags)

    suspend fun removeTag(assetId: String, tag: String): List<String> =
        api.removeAssetTag(assetId, tag)

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

    suspend fun updateCaptureDate(
        assetId: String,
        dateTaken: Instant,
        writeToFile: Boolean
    ): com.photonne.app.data.models.CaptureDateUpdateResponse =
        api.updateAssetCaptureDate(assetId, dateTaken, writeToFile)

    suspend fun getCaptureDateSuggestion(
        assetId: String
    ): com.photonne.app.data.models.CaptureDateSuggestion =
        api.getCaptureDateSuggestion(assetId)
}
