package com.photonne.app.data.asset

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.api.serverCode
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.AssetPage
import com.photonne.app.data.models.Face
import com.photonne.app.data.models.PersonAssetsPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Instant

class AssetDetailRepository(
    private val api: PhotonneApi,
    // Por defecto un bus propio: los tests construyen el repo a pelo y no
    // necesitan colectores; en la app Koin inyecta el singleton compartido.
    private val mutationBus: AssetMutationBus = AssetMutationBus(),
) {
    suspend fun getDetail(assetId: String): AssetDetail = api.getAssetDetail(assetId)

    suspend fun getFaces(assetId: String): List<Face> = api.getAssetFaces(assetId)

    suspend fun getText(assetId: String) = api.getAssetText(assetId)

    suspend fun getObjects(assetId: String) = api.getAssetObjects(assetId)

    suspend fun getScenes(assetId: String) = api.getAssetScenes(assetId)

    suspend fun getPersonAssets(personId: String, limit: Int = 12): PersonAssetsPage =
        api.getPersonAssets(personId, limit = limit, offset = 0)

    suspend fun getSameDay(assetId: String, limit: Int = 12): PersonAssetsPage =
        api.getSameDayAssets(assetId, limit = limit)

    suspend fun addTags(assetId: String, tags: List<String>): List<String> =
        api.addAssetTags(assetId, tags)

    suspend fun removeTag(assetId: String, tag: String): List<String> =
        api.removeAssetTag(assetId, tag)

    suspend fun getUserTags(): List<String> = api.getUserTags()

    /**
     * Whether the server has the absolute favorite endpoints. Starts hopeful;
     * the first 404/405 from a server older than them switches to the toggle
     * for the rest of this session, so each tap isn't two requests.
     */
    private var absoluteFavorite = true

    /**
     * Sets the heart of [assetId] to [isFavorite] and returns the server's
     * state, which every list hears through `FavoriteChanged`.
     *
     * Servers without `PUT /favorite` only toggle: the heart the user saw is
     * then the target, and if the server answers the opposite (our copy was
     * stale) it's toggled once more.
     */
    suspend fun setFavorite(assetId: String, isFavorite: Boolean): Boolean {
        val confirmed = if (absoluteFavorite) {
            try {
                api.setFavorite(assetId, isFavorite)
            } catch (error: PhotonneApiException) {
                if (!error.isMissingEndpoint()) throw error
                absoluteFavorite = false
                toggleTowards(assetId, isFavorite)
            }
        } else {
            toggleTowards(assetId, isFavorite)
        }
        mutationBus.emit(AssetMutation.FavoriteChanged(assetId, confirmed))
        return confirmed
    }

    /**
     * Sets the heart of several assets in one request and returns the ids
     * that now have it; the rest failed (gone, unreadable, or a request that
     * didn't go through on an older server, which goes one by one).
     */
    suspend fun setFavorites(assetIds: List<String>, isFavorite: Boolean): Set<String> {
        if (assetIds.isEmpty()) return emptySet()
        if (absoluteFavorite) {
            try {
                val confirmed = api.setFavorites(assetIds, isFavorite).toSet()
                confirmed.forEach { mutationBus.emit(AssetMutation.FavoriteChanged(it, isFavorite)) }
                return confirmed
            } catch (error: PhotonneApiException) {
                if (!error.isMissingEndpoint()) throw error
                absoluteFavorite = false
            }
        }
        val gate = Semaphore(4)
        return coroutineScope {
            assetIds.map { id ->
                async {
                    gate.withPermit {
                        val now = runCatching { setFavorite(id, isFavorite) }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                        id.takeIf { now == isFavorite }
                    }
                }
            }.awaitAll().filterNotNull().toSet()
        }
    }

    private suspend fun toggleTowards(assetId: String, isFavorite: Boolean): Boolean {
        val now = api.toggleFavorite(assetId)
        return if (now == isFavorite) now else api.toggleFavorite(assetId)
    }

    /**
     * A server older than the endpoint: 405 when the route exists for another
     * method, 404 when it doesn't exist at all. A 404 that names the asset
     * (`asset_not_found`) comes from a server that has it.
     */
    private fun PhotonneApiException.isMissingEndpoint(): Boolean =
        status == 405 || (status == 404 && serverCode == null)

    suspend fun archive(assetIds: List<String>) {
        api.archiveAssets(assetIds)
        mutationBus.emit(AssetMutation.Removed(assetIds))
    }

    suspend fun unarchive(assetIds: List<String>) {
        api.unarchiveAssets(assetIds)
        mutationBus.emit(AssetMutation.Restored(assetIds))
    }

    suspend fun unarchiveAll() {
        api.unarchiveAll()
        mutationBus.emit(AssetMutation.AllChanged)
    }

    suspend fun trash(assetIds: List<String>) {
        api.trashAssets(assetIds)
        mutationBus.emit(AssetMutation.Removed(assetIds))
    }

    suspend fun restore(assetIds: List<String>) {
        api.restoreAssets(assetIds)
        mutationBus.emit(AssetMutation.Restored(assetIds))
    }

    suspend fun restoreAllTrash() {
        api.restoreAllTrash()
        mutationBus.emit(AssetMutation.AllChanged)
    }

    suspend fun purge(assetIds: List<String>) {
        api.purgeAssets(assetIds)
        mutationBus.emit(AssetMutation.Purged(assetIds))
    }

    suspend fun emptyTrash() {
        api.emptyTrash()
        mutationBus.emit(AssetMutation.AllChanged)
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
        api.updateAssetCaptureDate(assetId, dateTaken, writeToFile).also { resp ->
            mutationBus.emit(
                AssetMutation.DateChanged(assetId, resp.capturedAt ?: resp.dateTaken ?: dateTaken)
            )
        }

    suspend fun getCaptureDateSuggestion(
        assetId: String
    ): com.photonne.app.data.models.CaptureDateSuggestion =
        api.getCaptureDateSuggestion(assetId)

    suspend fun getMotionFrameCount(assetId: String): Int = api.getMotionFrameCount(assetId)

    /** The new photo lands in the original's folder and day: lists reload to
     *  pick it up (there is no "added" mutation to patch them in place). */
    suspend fun saveMotionFrame(assetId: String, index: Int): String =
        api.saveMotionFrame(assetId, index).also {
            mutationBus.emit(AssetMutation.AllChanged)
        }
}
