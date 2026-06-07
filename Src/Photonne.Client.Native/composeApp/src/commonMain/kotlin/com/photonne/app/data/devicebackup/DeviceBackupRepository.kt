package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.upload.UploadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
    private val stateStore: DeviceBackupStateStore,
    private val ledger: BackupLedger
) {

    val isSupported: Boolean get() = gallery.isSupported

    fun savedFolder(): DeviceFolderRef? = stateStore.savedFolder()

    fun rememberFolder(folder: DeviceFolderRef) {
        stateStore.saveFolder(folder)
    }

    fun forgetFolder() {
        stateStore.clearFolder()
    }

    /** Last scanned media for the saved folder, persisted so the timeline can
     *  show device-only photos instantly on launch before the fresh re-scan
     *  completes. Empty if nothing cached or the saved folder changed. */
    fun cachedMedia(): List<DeviceMedia> {
        val folder = savedFolder() ?: return emptyList()
        return stateStore.cachedMedia(folder.uri)
    }

    fun saveCachedMedia(folderUri: String, media: List<DeviceMedia>) {
        stateStore.saveCachedMedia(folderUri, media)
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

    // ─── Incremental verification (ledger-backed) ────────────────────────────

    /** Progress of a [verifyAgainstServer] pass. Hashing dominates the cost,
     *  so [hashedCount]/[hashTotal] is what a progress bar should show. */
    data class VerificationProgress(
        val hashedCount: Int,
        val hashTotal: Int
    )

    /** Last persisted verdict per uri — instant, no hashing, no network.
     *  Lets the UI seed sync badges the moment a folder scan completes. */
    fun syncStatesFor(folder: DeviceFolderRef): Map<String, DeviceMediaSyncState> =
        ledger.entries(folder.uri).mapValues { (_, entry) -> entry.toSyncState() }

    /**
     * Brings the ledger up to date against a fresh [scanned] folder listing:
     *
     * 1. Reconcile — new/changed files reset to UNKNOWN, deleted rows drop.
     * 2. Hash — SHA-256 only the files without a valid stored hash.
     * 3. Bulk-check — ONE server round-trip per [CHECKSUM_BATCH] hashes,
     *    covering every hashed entry (so server-side deletions or uploads
     *    from another client are picked up on every pass, not just for
     *    new files).
     *
     * Cancellation via [shouldContinue] is cheap: everything done so far is
     * already persisted, so the next pass resumes where this one stopped.
     *
     * Returns the resulting sync state per uri.
     */
    suspend fun verifyAgainstServer(
        folder: DeviceFolderRef,
        scanned: List<DeviceMedia>,
        onProgress: ((VerificationProgress) -> Unit)? = null,
        shouldContinue: () -> Boolean = { true }
    ): Map<String, DeviceMediaSyncState> {
        val entries = ledger.reconcile(folder.uri, scanned).toMutableMap()
        val mediaByUri = scanned.associateBy { it.uri }

        // 2. Hash anything the ledger doesn't have a valid hash for. Files
        //    hash in parallel (bounded — hashing is CPU+IO, not network), but
        //    ledger writes and progress updates stay serialized.
        val needHash = entries.values.filter { it.sha256 == null }
        onProgress?.invoke(VerificationProgress(0, needHash.size))
        if (needHash.isNotEmpty()) {
            val permits = Semaphore(HASH_CONCURRENCY)
            val writes = Mutex()
            var hashed = 0
            coroutineScope {
                needHash.map { entry ->
                    async(Dispatchers.Default) {
                        if (!shouldContinue()) return@async
                        permits.withPermit {
                            if (!shouldContinue()) return@withPermit
                            val media = mediaByUri[entry.uri] ?: return@withPermit
                            val hash = runCatching { gallery.computeSha256(media) }
                                .getOrNull() ?: return@withPermit
                            writes.withLock {
                                ledger.setHash(folder.uri, entry.uri, hash)
                                entries[entry.uri] = entry.copy(sha256 = hash)
                                hashed++
                                onProgress?.invoke(VerificationProgress(hashed, needHash.size))
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        // 3. One bulk lookup over every hashed entry. `checked` only contains
        //    the checksums that actually reached the server, so a cancelled
        //    pass never mislabels unchecked files as NotSynced.
        if (shouldContinue()) {
            var bulkSupported = true
            val hashedEntries = entries.values.filter { it.sha256 != null }
            val byChecksum = hashedEntries.groupBy { it.sha256!! }
            byChecksum.keys.chunked(CHECKSUM_BATCH).forEach { batch ->
                if (!shouldContinue()) return@forEach
                val checked: Map<String, String?> = if (bulkSupported) {
                    try {
                        val found = api.checkChecksums(batch)
                        batch.associateWith { found[it] }
                    } catch (ex: PhotonneApiException) {
                        if (ex.status != 404) throw ex
                        // Older server without /check-checksums (client newer
                        // than the deployment — common in self-hosted setups).
                        // Degrade to the legacy per-file lookup for the rest
                        // of the pass instead of failing the verification.
                        bulkSupported = false
                        lookupChecksumsLegacy(batch, shouldContinue)
                    }
                } else {
                    lookupChecksumsLegacy(batch, shouldContinue)
                }
                val verdicts = checked.entries.flatMap { (checksum, assetId) ->
                    byChecksum.getValue(checksum).map { entry ->
                        val state = when {
                            assetId != null -> LedgerState.Synced
                            // A confirmed upload failure is more useful to
                            // surface than a generic "not synced".
                            entry.state == LedgerState.Failed -> LedgerState.Failed
                            else -> LedgerState.NotSynced
                        }
                        Triple(entry.uri, state, assetId)
                    }
                }
                ledger.setVerdicts(folder.uri, verdicts.filter { it.second != LedgerState.Failed })
                verdicts.forEach { (uri, state, assetId) ->
                    entries[uri]?.let {
                        entries[uri] = it.copy(
                            state = state,
                            assetId = assetId ?: it.assetId.takeIf { _ -> state == LedgerState.Synced }
                        )
                    }
                }
            }
        }

        return entries.mapValues { (_, entry) -> entry.toSyncState() }
    }

    /**
     * Pre-/check-checksums servers: one GET /api/assets/exists/{hash} per
     * checksum. Stops early when [shouldContinue] flips, returning only the
     * checksums it actually verified.
     */
    private suspend fun lookupChecksumsLegacy(
        batch: List<String>,
        shouldContinue: () -> Boolean
    ): Map<String, String?> {
        val results = LinkedHashMap<String, String?>(batch.size)
        for (checksum in batch) {
            if (!shouldContinue()) break
            results[checksum] = api.assetExistsByChecksum(checksum)
        }
        return results
    }

    /** Records a finished upload so the verdict survives restarts. */
    fun markUploaded(folder: DeviceFolderRef, uri: String, assetId: String) {
        ledger.markUploaded(folder.uri, uri, assetId)
    }

    /** Records a failed upload so the failure is visible after restart too. */
    fun markUploadFailed(
        folder: DeviceFolderRef,
        uri: String,
        reason: UploadFailureReason,
        detail: String?
    ) {
        ledger.markUploadFailed(folder.uri, uri, reason, detail)
    }

    // ─── Last completed pass ─────────────────────────────────────────────────

    fun lastRun(): LastBackupRun? = stateStore.lastRun()

    fun recordLastRun(run: LastBackupRun) = stateStore.recordLastRun(run)

    /**
     * Streams [media] to the server without ever holding the payload in
     * memory — large videos OOM the Android heap if read into a ByteArray.
     * The server dedupes by SHA-256 itself, so if the hash check above
     * raced the upload still ends with the right asset id.
     *
     * Transient failures (network, 5xx, 429) are retried with exponential
     * backoff up to [maxAttempts] times, re-opening the source each try.
     * Permanent failures (quota, oversize, forbidden, unauthorized) bail
     * immediately so we don't keep retrying something the server will
     * never accept.
     */
    suspend fun upload(
        media: DeviceMedia,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
    ): com.photonne.app.data.api.UploadAssetResponse {
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return gallery.withUploadSource(media) { source, sizeBytes ->
                    uploads.uploadStream(
                        fileName = media.displayName,
                        mimeType = media.mimeType,
                        source = source,
                        sizeBytes = sizeBytes,
                        destination = MOBILE_BACKUP_DESTINATION,
                        deviceName = currentDeviceName(),
                        fileModifiedAtMillis = media.dateModifiedMillis.takeIf { it > 0 },
                        fileCreatedAtMillis = media.dateCreatedMillis
                    )
                }
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

        // Server caps /check-checksums at 1000 hashes per request; stay under.
        const val CHECKSUM_BATCH = 500

        // Parallel SHA-256 workers during verification. Bounded so a phone
        // doesn't read 4+ large videos into the hash pipeline at once.
        const val HASH_CONCURRENCY = 4

        // Backoff between client-side upload retries. Index = attempt number
        // (0-based) of the FAILED attempt — delay BEFORE the next try.
        // Kept short on purpose: a stuck phone backup shouldn't waste 6h like
        // server-side enrichment does.
        private val retryDelays = listOf(1.seconds, 5.seconds, 30.seconds)

        private fun retryDelayFor(attempt: Int): Duration =
            retryDelays.getOrElse(attempt) { retryDelays.last() }
    }
}
