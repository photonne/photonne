package com.photonne.app.data.devicesync

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.upload.UploadRepository

/**
 * Coordinates between the local device gallery and the server. Three
 * roles:
 *
 * 1. Lists media inside the user-picked folder (delegated to
 *    [DeviceGallery]).
 * 2. Streams each file through SHA-256 once on demand and asks the
 *    server whether the hash is already on the account. The server
 *    answers via `GET /api/assets/exists/{sha256}` — 200 with the
 *    matching `assetId` for a hit, 404 for a miss.
 * 3. Uploads selected items through the shared
 *    [UploadRepository] so they reuse the rest of the app's upload
 *    queue, retry, and dedup-on-name handling.
 */
class DeviceSyncRepository(
    private val gallery: DeviceGallery,
    private val api: PhotonneApi,
    private val uploads: UploadRepository,
    private val stateStore: DeviceSyncStateStore
) {

    val isSupported: Boolean get() = gallery.isSupported

    fun savedFolder(): DeviceFolderRef? = stateStore.savedFolder()

    fun rememberFolder(folder: DeviceFolderRef) {
        stateStore.saveFolder(folder)
    }

    fun forgetFolder() {
        stateStore.clearFolder()
    }

    suspend fun restoreFolder(uri: String): DeviceFolderRef? =
        gallery.restoreFolder(uri)

    suspend fun listMedia(folder: DeviceFolderRef): List<DeviceMedia> =
        gallery.listMedia(folder)

    /**
     * Hashes [media] then queries the server. Returns the resulting
     * sync state and the SHA-256 so the caller can cache it on the
     * `DeviceMedia` for later upload de-duplication.
     */
    suspend fun checkSyncStatus(media: DeviceMedia): Pair<String, DeviceMediaSyncState> {
        val hash = media.sha256 ?: gallery.computeSha256(media)
        val existingId = api.assetExistsByChecksum(hash)
        val state = if (existingId != null) {
            DeviceMediaSyncState.Synced(existingId)
        } else {
            DeviceMediaSyncState.NotSynced
        }
        return hash to state
    }

    /**
     * Reads [media] in full and posts it to the server. The server
     * dedupes by SHA-256 itself, so if the hash check above raced the
     * upload still ends with the right asset id.
     */
    suspend fun upload(media: DeviceMedia): com.photonne.app.data.api.UploadAssetResponse {
        val bytes = gallery.readBytes(media)
        return uploads.upload(
            fileName = media.displayName,
            mimeType = media.mimeType,
            bytes = bytes
        )
    }

    fun thumbnailModel(media: DeviceMedia): String = gallery.thumbnailModel(media)

    /** Deletes [media] from the device storage. */
    suspend fun deleteLocal(media: DeviceMedia): Boolean = gallery.deleteFile(media)
}
