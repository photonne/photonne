package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.devicelibrary.DeviceIdentity
import com.photonne.app.data.devicelibrary.DeviceIdentityMap
import com.photonne.app.data.upload.UploadRepository
import kotlinx.coroutines.CancellationException
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
    private val ledger: BackupLedger,
    private val identityMap: DeviceIdentityMap,
    private val progress: BackupProgressBus,
    private val tokenStorage: com.photonne.app.data.auth.TokenStorage
) {

    val isSupported: Boolean get() = gallery.isSupported

    fun savedFolders(): List<DeviceFolderRef> = stateStore.savedFolders()

    fun rememberFolder(folder: DeviceFolderRef) {
        stateStore.addFolder(folder)
    }

    /** Stops backing up one folder. Its ledger rows go too — otherwise a later
     *  re-add would trust verdicts computed while it wasn't being watched. */
    fun forgetFolder(folderUri: String) {
        stateStore.removeFolder(folderUri)
        ledger.clearFolder(folderUri)
    }

    /** Stops backing up one folder but keeps its ledger rows for now, so an
     *  "undo" brings it back with its verdicts and "Omitir" choices intact.
     *  [purgeForgottenFolder] drops them once the undo window is over. */
    fun detachFolder(folderUri: String) {
        stateStore.removeFolder(folderUri)
    }

    /** Drops the ledger rows of a folder [detachFolder] removed — unless it was
     *  added back meanwhile, in which case those rows are live again. */
    fun purgeForgottenFolder(folderUri: String) {
        if (stateStore.savedFolders().any { it.uri == folderUri }) return
        ledger.clearFolder(folderUri)
    }

    /** Last scanned media per saved folder, persisted so the timeline can show
     *  device-only photos instantly on launch before the fresh re-scan
     *  completes. Empty for folders never scanned. */
    fun cachedMedia(): Map<DeviceFolderRef, List<DeviceMedia>> =
        savedFolders().associateWith { stateStore.cachedMedia(it.uri) }
            .filterValues { it.isNotEmpty() }

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
    fun setTurboEnabled(value: Boolean) = stateStore.setTurboEnabled(value)

    /** Current upload fan-out, widened when the user has Turbo on. Shared by
     *  the foreground sync and the background runner via [uploadInParallel]. */
    fun uploadConcurrency(): UploadConcurrency =
        if (stateStore.isTurboEnabled()) {
            UploadConcurrency(photo = PHOTO_CONCURRENCY_TURBO, video = VIDEO_CONCURRENCY_TURBO)
        } else {
            UploadConcurrency(photo = PHOTO_CONCURRENCY, video = VIDEO_CONCURRENCY)
        }

    suspend fun restoreFolder(uri: String, fallbackName: String? = null): DeviceFolderRef? =
        gallery.restoreFolder(uri, fallbackName)

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
    fun syncStatesFor(folderUri: String): Map<String, DeviceMediaSyncState> =
        ledger.entries(folderUri).mapValues { (_, entry) -> entry.toSyncState() }

    /** Same, merged across every backed-up folder — URIs are unique per file,
     *  so the union needs no disambiguation. */
    fun syncStatesFor(folders: List<DeviceFolderRef>): Map<String, DeviceMediaSyncState> =
        folders.fold(emptyMap()) { acc, folder -> acc + syncStatesFor(folder.uri) }

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
        shouldContinue: () -> Boolean = { true },
        fullReconcile: Boolean = true
    ): Map<String, DeviceMediaSyncState> {
        val entries = ledger.reconcile(folder.uri, scanned).toMutableMap()
        val mediaByUri = scanned.associateBy { it.uri }

        // 1b. Seed hashes from the device↔server identity map before paying
        //     for any hashing: whatever the timeline's viewport resolver (or
        //     a previous ledger, via the bridge) already hashed transfers
        //     over — by uri for MediaStore-bucket folders, whose uris match
        //     the map's keys exactly, and by (size, mtime-seconds)
        //     fingerprint for legacy SAF uris. On a folder freshly added
        //     this can collapse the initial hash pass to nothing.
        seedHashesFromIdentityMap(folder.uri, entries, mediaByUri)
        // Reconcile + seeding may have created rows (fresh UNKNOWNs are what
        // the timeline badges as "pending" for backup-covered files).
        progress.bumpLedger()

        // 2. Hash anything the ledger doesn't have a valid hash for. Files
        //    hash in parallel (bounded — hashing is CPU+IO, not network), but
        //    ledger writes and progress updates stay serialized.
        // Ignored entries are excluded everywhere: the user explicitly skipped
        // them, so we neither re-hash (which would re-fail an unreadable file
        // straight back out of IGNORED) nor re-check them against the server.
        val needHash = entries.values.filter {
            it.sha256 == null && it.state != LedgerState.Ignored
        }
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
                            val hashResult = runCatching { gallery.computeSha256(media) }
                            val hash = hashResult.getOrNull()
                            if (hash == null) {
                                // A file we can't read (deleted mid-scan, codec
                                // error, permission revoked) used to stay UNKNOWN
                                // forever and show as "pending" indefinitely. Mark
                                // it FAILED so it surfaces and can be skipped.
                                writes.withLock {
                                    val detail = hashResult.exceptionOrNull()?.message
                                    ledger.markUploadFailed(
                                        folder.uri, entry.uri,
                                        UploadFailureReason.FileUnreadable, detail
                                    )
                                    entries[entry.uri] = entry.copy(
                                        state = LedgerState.Failed,
                                        failureReason = UploadFailureReason.FileUnreadable.name,
                                        failureDetail = detail
                                    )
                                    hashed++
                                    onProgress?.invoke(VerificationProgress(hashed, needHash.size))
                                }
                                return@withPermit
                            }
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
            val hashedEntries = entries.values.filter {
                it.sha256 != null && it.state != LedgerState.Ignored
            }.filter {
                // A full reconcile re-asks about every file, which is how we
                // notice server-side deletions and uploads from other clients.
                // It's also 40 round-trips for a 20k library, so a scheduled
                // wake only re-asks about files without a verdict yet and
                // leaves the sweep to the once-a-day full pass.
                fullReconcile || it.state == LedgerState.Unknown
            }
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
                        // A confirmed upload (we hold the server's assetId) is
                        // ground truth: never auto-downgrade it to NotSynced
                        // because this checksum lookup missed. The local hash
                        // and the server's stored checksum can legitimately
                        // differ for some files, and re-queuing an already
                        // backed-up file forever is the worse failure.
                        val keepConfirmed = assetId == null &&
                            entry.state == LedgerState.Synced &&
                            !entry.assetId.isNullOrEmpty()
                        val state = when {
                            assetId != null -> LedgerState.Synced
                            keepConfirmed -> LedgerState.Synced
                            // A confirmed upload failure is more useful to
                            // surface than a generic "not synced".
                            entry.state == LedgerState.Failed -> LedgerState.Failed
                            else -> LedgerState.NotSynced
                        }
                        val resolvedAssetId = assetId ?: entry.assetId.takeIf { keepConfirmed }
                        Triple(entry.uri, state, resolvedAssetId)
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
                // Verdicts just landed — let ledger observers (the pending
                // screen AND the timeline's badge overlay) re-read now
                // instead of at the end of a potentially long pass.
                progress.bumpLedger()
            }
        }

        return entries.mapValues { (_, entry) -> entry.toSyncState() }
    }

    /** See the 1b step in [verifyAgainstServer]. Seeded entries keep their
     *  UNKNOWN state on purpose: only the bulk server check hands out
     *  verdicts — this transfers the hash work, not the conclusion. */
    private fun seedHashesFromIdentityMap(
        folderUri: String,
        entries: MutableMap<String, LedgerEntry>,
        mediaByUri: Map<String, DeviceMedia>
    ) {
        val missing = entries.values.filter {
            it.sha256 == null && it.state != LedgerState.Ignored
        }
        if (missing.isEmpty()) return
        val identities = runCatching { identityMap.all() }.getOrNull() ?: return
        if (identities.isEmpty()) return

        val byFingerprint = HashMap<String, DeviceIdentity>()
        identities.values.forEach { identity ->
            // The fingerprint bridge needs all THREE factors — size, mtime
            // second AND file name. A bare (size, mtime) pair can collide
            // across distinct files (batch exports writing same-size files
            // in the same second), and seeding a wrong sha here would mark
            // a never-uploaded file as Synced — a silent hole in the backup
            // that "Liberar espacio" could then turn into data loss. Rows
            // from before the v4 migration have no name and never bridge;
            // they still seed through the exact-uri path.
            if (identity.sha256 != null && identity.sizeBytes > 0L &&
                identity.displayName != null
            ) {
                val key =
                    "${identity.sizeBytes}|${identity.dateModifiedMillis / 1000}|${identity.displayName}"
                byFingerprint[key] = identity
            }
        }
        for (entry in missing) {
            val media = mediaByUri[entry.uri] ?: continue
            // Seconds precision on the mtime — SAF carries millis, MediaStore
            // only seconds; same normalization as the store's ledger bridge.
            val identity = identities[entry.uri]?.takeIf {
                it.sha256 != null && it.sizeBytes == media.sizeBytes &&
                    it.dateModifiedMillis / 1000 == media.dateModifiedMillis / 1000
            } ?: byFingerprint[
                "${media.sizeBytes}|${media.dateModifiedMillis / 1000}|${media.displayName}"
            ]
            val sha = identity?.sha256 ?: continue
            ledger.setHash(folderUri, entry.uri, sha)
            entries[entry.uri] = entry.copy(sha256 = sha)
        }
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

    /**
     * Parks [uris] where the foreground worker can pick them up, returning the
     * key to hand it. Goes through the ledger rather than WorkManager's input
     * `Data` because that caps out around 10 KB — a few hundred content URIs
     * already exceed it.
     */
    /**
     * True when the next pass should re-ask the server about every known
     * checksum rather than only the unverified ones. Consuming it stamps
     * "now", so the expensive sweep happens about once a day instead of on
     * every 15-minute wake.
     */
    fun consumeFullReconcileDue(nowMillis: Long): Boolean {
        val last = ledger.meta(RECONCILE_META_KEY)?.toLongOrNull() ?: 0L
        if (nowMillis - last < FULL_RECONCILE_INTERVAL_MILLIS) return false
        ledger.putMeta(RECONCILE_META_KEY, nowMillis.toString())
        return true
    }

    fun stashSelection(uris: Collection<String>): String {
        ledger.putMeta(SELECTION_META_KEY, uris.joinToString("\n"))
        return SELECTION_META_KEY
    }

    /** Records a finished upload so the verdict survives restarts. */
    fun markUploaded(folderUri: String, uri: String, assetId: String) {
        ledger.markUploaded(folderUri, uri, assetId)
    }

    /** Records a failed upload so the failure is visible after restart too. */
    fun markUploadFailed(
        folderUri: String,
        uri: String,
        reason: UploadFailureReason,
        detail: String?
    ) {
        ledger.markUploadFailed(folderUri, uri, reason, detail)
    }

    /** User chose to skip this file — it stops counting as pending and is never
     *  re-queued or re-verified until [unignore] puts it back in the pipeline. */
    fun markIgnored(folderUri: String, uri: String) {
        ledger.markIgnored(folderUri, uri)
    }

    /** Bulk skip: every currently-failed file in [folder] becomes ignored. */
    fun ignoreFailed(folderUri: String) {
        ledger.ignoreFailed(folderUri)
    }

    /** Reverses [markIgnored]: the file returns to UNKNOWN so the next
     *  verification re-hashes and re-checks it from scratch. */
    fun unignore(folderUri: String, uri: String) {
        ledger.unignore(folderUri, uri)
    }

    // ─── Last completed pass ─────────────────────────────────────────────────

    fun lastRun(): LastBackupRun? = stateStore.lastRun()

    fun recordLastRun(run: LastBackupRun) = stateStore.recordLastRun(run)

    // ─── Pasada cortada por la cuenta (lote L11) ─────────────────────────────

    fun passBlock(): BackupPassBlock? = stateStore.passBlock()

    fun clearPassBlock() = stateStore.clearPassBlock()

    /**
     * Guarda por qué se cortó la pasada. Con cuota llena se anota también el
     * uso y la cuota del servidor: lo que pinta la tarjeta y lo que dice
     * cuándo ha cambiado algo.
     */
    suspend fun recordPassBlock(reason: UploadFailureReason, nowMillis: Long) {
        val storage = if (reason == UploadFailureReason.QuotaExceeded) storageOrNull() else null
        stateStore.savePassBlock(
            BackupPassBlock(
                reason = reason,
                usedBytes = storage?.usedBytes,
                quotaBytes = storage?.quotaBytes,
                sessionKey = currentSessionKey(),
                atMillis = nowMillis
            )
        )
    }

    /**
     * Para una pasada programada: true mientras el bloqueo siga en pie (misma
     * sesión tras un 401; mismo uso y misma cuota tras llenarse). Si algo ha
     * cambiado —se ha vuelto a entrar, el admin subió la cuota, se liberó
     * espacio— lo levanta y devuelve false, y la pasada reintenta los
     * rechazados. Sin respuesta del servidor sobre la cuota, sigue en pie.
     */
    suspend fun passBlockHolds(): Boolean {
        val block = stateStore.passBlock() ?: return false
        val holds = when (block.reason) {
            UploadFailureReason.Unauthorized -> currentSessionKey() == block.sessionKey
            UploadFailureReason.QuotaExceeded -> {
                val storage = storageOrNull()
                storage == null ||
                    (storage.usedBytes == block.usedBytes && storage.quotaBytes == block.quotaBytes)
            }
            else -> false
        }
        if (!holds) stateStore.clearPassBlock()
        return holds
    }

    private suspend fun storageOrNull(): com.photonne.app.data.models.StorageInfoDto? = try {
        api.getStorageInfo()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    /** Huella de la sesión actual (no el token): cambia al volver a entrar. */
    private fun currentSessionKey(): String? =
        tokenStorage.getRefreshToken()?.hashCode()?.toString(16)

    /**
     * Streams [media] to the server without ever holding the payload in
     * memory — large videos OOM the Android heap if read into a ByteArray.
     * The server dedupes by SHA-256 itself, so if the hash check above
     * raced the upload still ends with the right asset id. Retries follow
     * [withUploadRetries]. A Live Photo's motion clip follows its still,
     * and [onProgress] then splits in halves: the still fills the first, the
     * clip the second, so the bar doesn't sit at 100 % while the clip uploads.
     */
    suspend fun upload(
        media: DeviceMedia,
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        onProgress: ((fraction: Float) -> Unit)? = null
    ): com.photonne.app.data.api.UploadAssetResponse {
        val stillShare = if (media.isLivePhoto) LIVE_PHOTO_STILL_SHARE else 1f
        val response = withUploadRetries(maxAttempts) {
            gallery.withUploadSource(media) { source, sizeBytes ->
                uploads.uploadStream(
                    fileName = media.displayName,
                    mimeType = media.mimeType,
                    source = source,
                    sizeBytes = sizeBytes,
                    destination = MOBILE_BACKUP_DESTINATION,
                    deviceName = currentDeviceName(),
                    fileModifiedAtMillis = media.dateModifiedMillis.takeIf { it > 0 },
                    fileCreatedAtMillis = media.dateCreatedMillis,
                    onProgress = onProgress?.let { report ->
                        { sent, total -> report(stillShare * (sent.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                )
            }
        }
        // The still is what the backup promises, so a clip that doesn't make
        // it never fails the file: [repairMotionClips] sends it on a later pass.
        val assetId = response.assetId
        if (media.isLivePhoto && !assetId.isNullOrEmpty()) {
            attachMotionClip(media, assetId, onProgress?.let { report ->
                { fraction -> report(stillShare + (1f - stillShare) * fraction) }
            })
        }
        return response
    }

    /**
     * Sends the motion clip of every Live Photo in [synced] (device file →
     * server asset id) whose still the server holds without one: stills backed
     * up before the clip travelled with them, or whose clip upload failed.
     * One lookup per [MOTION_CLIP_BATCH] stills, all of them up front so
     * [onProgress] can count against the real total; only the missing clips
     * are uploaded. [onProgress] reports clips finished (landed or not) of
     * those missing, and stays silent when none is: the lookup alone isn't
     * worth a phase on screen. Never throws but for cancellation — the stills
     * themselves are safe, so a failure here just waits for the next pass.
     * Returns how many clips landed.
     */
    suspend fun repairMotionClips(
        synced: List<Pair<DeviceMedia, String>>,
        shouldContinue: () -> Boolean = { true },
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Int {
        val mediaByAssetId = synced
            .filter { (media, assetId) -> media.isLivePhoto && assetId.isNotEmpty() }
            .associate { (media, assetId) -> assetId to media }
        if (mediaByAssetId.isEmpty()) return 0
        var repaired = 0
        try {
            val missing = mutableListOf<String>()
            for (batch in mediaByAssetId.keys.chunked(MOTION_CLIP_BATCH)) {
                if (!shouldContinue()) return 0
                missing += api.motionClipsMissing(batch).filter { it in mediaByAssetId }
            }
            if (missing.isEmpty()) return 0
            onProgress?.invoke(0, missing.size)
            var done = 0
            val permits = Semaphore(uploadConcurrency().video)
            val counter = Mutex()
            coroutineScope {
                missing.map { assetId ->
                    async {
                        permits.withPermit {
                            if (!shouldContinue()) return@withPermit
                            val landed = attachMotionClip(mediaByAssetId.getValue(assetId), assetId)
                            counter.withLock {
                                if (landed) repaired++
                                done++
                                onProgress?.invoke(done, missing.size)
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (_: Throwable) {
            // Server without the endpoint (404) or unreachable: next pass.
        }
        return repaired
    }

    /** Uploads the paired clip of [media] to the still [assetId]. False when it
     *  has none or the upload failed after its retries. */
    private suspend fun attachMotionClip(
        media: DeviceMedia,
        assetId: String,
        onProgress: ((fraction: Float) -> Unit)? = null
    ): Boolean = try {
        withUploadRetries(DEFAULT_MAX_ATTEMPTS) {
            gallery.withMotionClipSource(media) { source, sizeBytes, fileName, mimeType ->
                api.attachMotionClip(
                    assetId, fileName, mimeType, source, sizeBytes,
                    onProgress = onProgress?.let { report ->
                        { sent, total -> report((sent.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                )
            }
        } != null
    } catch (ex: CancellationException) {
        throw ex
    } catch (_: Throwable) {
        false
    }

    /**
     * Runs one streamed upload [attempt] with the backup's retry policy.
     * Transient failures (network, 5xx, 429) are retried with exponential
     * backoff up to [maxAttempts] times, re-opening the source each try.
     * Permanent failures (quota, oversize, forbidden, unauthorized) bail
     * immediately so we don't keep retrying something the server will
     * never accept. The one exception is the first 401: the streamed body
     * can't be replayed by the auth plugin, which only refreshes the token,
     * so the upload is resent once, right away, with a reopened source.
     */
    private suspend fun <T> withUploadRetries(maxAttempts: Int, attempt: suspend () -> T): T {
        var lastError: Throwable? = null
        var retriedAfterRefresh = false
        var tries = 0
        while (tries < maxAttempts) {
            try {
                return attempt()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Throwable) {
                lastError = ex
                if (!retriedAfterRefresh && (ex as? PhotonneApiException)?.status == 401) {
                    // Expired access token: the plugin has refreshed it (or
                    // logged out, and this retry fails with 401 again).
                    retriedAfterRefresh = true
                    continue
                }
                if (!ex.toUploadFailureReason().isRetryable) {
                    throw ex // permanent — no point retrying
                }
                if (tries < maxAttempts - 1) {
                    delay(retryDelayFor(tries))
                }
                tries++
            }
        }
        throw lastError ?: RuntimeException("Upload failed after $maxAttempts attempts")
    }

    fun thumbnailModel(media: DeviceMedia): String = gallery.thumbnailModel(media)

    /** Deletes [media] from the device storage. */
    suspend fun deleteLocal(media: DeviceMedia): Boolean = gallery.deleteFile(media)

    companion object {
        const val MOBILE_BACKUP_DESTINATION = "mobile-backup"

        // Single slot: only one foreground pass runs at a time (the worker is
        // enqueued as unique work), and the worker consumes the row on read.
        const val SELECTION_META_KEY = "foregroundSelection"

        // When the last full server-wide reconcile ran, and how often it should.
        const val RECONCILE_META_KEY = "lastFullReconcileMillis"
        const val FULL_RECONCILE_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val DEFAULT_MAX_ATTEMPTS = 3

        // Server caps /check-checksums at 1000 hashes per request; stay under.
        const val CHECKSUM_BATCH = 500

        // Same cap and margin for /motion-clips/missing.
        const val MOTION_CLIP_BATCH = 500

        // Share of a Live Photo's bar that belongs to the still; the clip
        // fills the rest. Their sizes aren't known before each is opened (iOS
        // reports 0), and a 2–3 MB clip next to a 1–3 MB HEIC is about even.
        const val LIVE_PHOTO_STILL_SHARE = 0.5f

        // Parallel SHA-256 workers during verification. Bounded so a phone
        // doesn't read too many large videos into the hash pipeline at once.
        const val HASH_CONCURRENCY = 6

        // Parallel upload workers, split by media kind. Photos are small so
        // they fan out wide; videos stay tight because each holds a large
        // streaming source open (OOM risk) and competes for the radio. Turbo
        // raises both for users on fast Wi-Fi who want to drain a big backlog.
        const val PHOTO_CONCURRENCY = 6
        const val VIDEO_CONCURRENCY = 2
        const val PHOTO_CONCURRENCY_TURBO = 10
        const val VIDEO_CONCURRENCY_TURBO = 3

        // Backoff between client-side upload retries. Index = attempt number
        // (0-based) of the FAILED attempt — delay BEFORE the next try.
        // Kept short on purpose: a stuck phone backup shouldn't waste 6h like
        // server-side enrichment does.
        private val retryDelays = listOf(1.seconds, 5.seconds, 30.seconds)

        private fun retryDelayFor(attempt: Int): Duration =
            retryDelays.getOrElse(attempt) { retryDelays.last() }
    }
}
