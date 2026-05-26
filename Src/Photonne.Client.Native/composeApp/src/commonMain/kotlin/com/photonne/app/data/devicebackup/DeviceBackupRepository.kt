package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.upload.UploadRepository
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
class DeviceBackupRepository(
    private val gallery: DeviceGallery,
    private val api: PhotonneApi,
    private val uploads: UploadRepository,
    private val stateStore: DeviceBackupStateStore
) {

    val isSupported: Boolean get() = gallery.isSupported

    fun savedFolder(): DeviceFolderRef? = stateStore.savedFolder()

    fun rememberFolder(folder: DeviceFolderRef) {
        stateStore.saveFolder(folder)
    }

    fun forgetFolder() {
        stateStore.clearFolder()
    }

    fun isBackupEnabled(): Boolean = stateStore.isBackupEnabled()

    fun setBackupEnabled(enabled: Boolean) {
        stateStore.setBackupEnabled(enabled)
    }

    // ─── Background sync passthrough ─────────────────────────────────────────

    fun backgroundSyncPreferences(): BackgroundSyncPreferences =
        stateStore.backgroundSyncPreferences()

    fun setAutoBackupEnabled(enabled: Boolean) = stateStore.setAutoBackupEnabled(enabled)
    fun setRequireWifi(value: Boolean) = stateStore.setRequireWifi(value)
    fun setRequireCharging(value: Boolean) = stateStore.setRequireCharging(value)

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
     *
     * Transient failures (network, 5xx, 429) are retried with exponential
     * backoff up to [maxAttempts] times. Permanent failures (quota, oversize,
     * forbidden, unauthorized) bail immediately so we don't keep retrying
     * something the server will never accept.
     */
    suspend fun upload(
        media: DeviceMedia,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    ): com.photonne.app.data.api.UploadAssetResponse {
        val bytes = gallery.readBytes(media)
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return uploads.upload(
                    fileName = media.displayName,
                    mimeType = media.mimeType,
                    bytes = bytes,
                    destination = MOBILE_BACKUP_DESTINATION,
                    deviceName = currentDeviceName()
                )
            } catch (ex: Throwable) {
                lastError = ex
                if (!ex.toUploadFailureReason().isRetryable) {
                    throw ex // permanent — no point retrying
                }
                if (attempt < maxAttempts - 1) {
                    delay(retryDelayFor(attempt))
                }
            }
        }
        throw lastError ?: RuntimeException("Upload failed after $maxAttempts attempts")
    }

    fun thumbnailModel(media: DeviceMedia): String = gallery.thumbnailModel(media)

    /** Deletes [media] from the device storage. */
    suspend fun deleteLocal(media: DeviceMedia): Boolean = gallery.deleteFile(media)

    companion object {
        const val MOBILE_BACKUP_DESTINATION = "mobile-backup"
        const val DEFAULT_MAX_ATTEMPTS = 3

        // Backoff between client-side upload retries. Index = attempt number
        // (0-based) of the FAILED attempt — delay BEFORE the next try.
        // Kept short on purpose: a stuck phone backup shouldn't waste 6h like
        // server-side enrichment does.
        private val retryDelays = listOf(1.seconds, 5.seconds, 30.seconds)

        private fun retryDelayFor(attempt: Int): Duration =
            retryDelays.getOrElse(attempt) { retryDelays.last() }
    }
}
