package com.photonne.app.data.devicelibrary

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.devicebackup.BackupLedger
import kotlinx.coroutines.CancellationException
import com.photonne.app.data.devicebackup.DeviceGallery
import com.photonne.app.data.devicebackup.DeviceMedia
import com.photonne.app.data.devicebackup.DeviceMediaType
import com.photonne.app.data.devicebackup.LedgerEntry
import com.photonne.app.data.devicebackup.LedgerState
import com.photonne.app.data.models.LocalSyncBadge
import com.photonne.app.data.models.TimelineItem
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

data class DeviceLibraryUiState(
    val access: DeviceLibraryAccess = DeviceLibraryAccess.NotDetermined,
    /**
     * The whole device library as synthetic [TimelineItem]s (ids
     * `device:<uri>`), newest first, ready to merge into the bucket
     * timeline. Empty until access is granted.
     */
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    /** True once the permission prompt has been shown (persisted), so a
     *  denied user is never re-nagged on every timeline entry. */
    val hasPrompted: Boolean = false
)

/**
 * The timeline's LOCAL source of truth: holds the device library as
 * render-ready items, loads it off-main via [DeviceLibrary]'s bulk
 * enumeration, and reloads (coalesced) whenever the platform media
 * index reports a change — take a photo, switch to the app, it's there.
 *
 * Sync knowledge is an overlay, never a prerequisite — display never
 * waits for hashing, verification, or the network. Two layered sources:
 *
 * 1. **The identity map** ([DeviceIdentityMap]): persisted, keyed by
 *    library uri, fingerprint-invalidated. `assetId` is ground truth
 *    for the bucket merge's dedup; `sha256` backs the checksum dedup.
 * 2. **The backup ledger**: knowledge the backup flow already paid for.
 *    Rows are keyed by the backup's SAF uris on Android, so the join
 *    also tries a (size, mtime-seconds) fingerprint to bridge them to
 *    MediaStore uris; on iOS both flows share the `photokit:` scheme
 *    and the uri join is exact. Bridged matches are PERSISTED into the
 *    identity map, so the bridge is paid once and identities survive a
 *    ledger folder-clear. Confirmed pending/failed verdicts become the
 *    cell badges.
 *
 * Gaps are filled lazily by [requestIdentityResolution]: the timeline
 * hands over the settled viewport window, and items with no identity
 * yet are hashed locally (never via iCloud download) and checked
 * against the server in bulk — identity resolves exactly where the
 * user looks, once, forever.
 */
class DeviceLibraryStore(
    private val library: DeviceLibrary,
    private val ledger: BackupLedger,
    private val identityMap: DeviceIdentityMap,
    private val gallery: DeviceGallery,
    private val api: PhotonneApi,
    private val progressBus: com.photonne.app.data.devicebackup.BackupProgressBus,
    private val settings: Settings,
    private val scopeStore: DeviceLibraryScopeStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()
    private val resolveMutex = Mutex()
    private var started = false

    /** Last enumeration, uri-keyed in display order — the resolver needs
     *  the [DeviceMedia] back to hash it. */
    private var mediaByUri: Map<String, DeviceMedia> = emptyMap()

    /** URIs whose local-only hash failed this session (iCloud-offloaded
     *  original, unreadable file). Skipped until next launch instead of
     *  retrying on every viewport visit. Guarded by [resolveMutex]. */
    private val unresolvable = mutableSetOf<String>()

    private val _state = MutableStateFlow(
        DeviceLibraryUiState(hasPrompted = settings.getBoolean(KEY_PROMPTED, false))
    )
    val state: StateFlow<DeviceLibraryUiState> = _state.asStateFlow()

    /** Whether the scope UI makes sense on this platform (see
     *  [DeviceLibrary.supportsBuckets]). */
    val supportsBuckets: Boolean get() = library.supportsBuckets

    /** The library's folders for the scope sheet and the "Mi dispositivo"
     *  listing — the same enumeration the backup picker uses. */
    suspend fun listBuckets(): List<DeviceBucket> = library.listBuckets()

    /**
     * One bucket's assets as render-ready items under the same identity +
     * ledger overlay the timeline gets. Deliberately IGNORES the timeline's
     * [DeviceLibraryScope]: the folder browser is where a hidden WhatsApp
     * folder must remain findable, or narrowing the timeline would orphan
     * everything outside it. Off-main; a fresh enumeration per call.
     */
    suspend fun loadBucketItems(bucketId: String): List<TimelineItem> {
        val media = runCatching {
            library.loadAll(DeviceLibraryScope.Buckets(setOf(bucketId)))
        }.getOrDefault(emptyList())
        return buildItems(media)
    }

    /**
     * Idempotent bootstrap, called from the timeline's composition (main
     * thread): first load plus the change-observer loop. Kept lazy so a
     * session that never reaches the timeline pays nothing.
     */
    fun ensureStarted() {
        if (started || !library.isSupported) return
        started = true
        scope.launch {
            refresh()
            // conflate + delay coalesce observer bursts (a burst of saves,
            // a bulk delete) into one reload ~1 s after the first signal.
            library.changes().conflate().collect {
                delay(CHANGE_COALESCE_MILLIS)
                refresh()
            }
        }
        scope.launch {
            // Scope changes re-enumerate: the narrowing lives in the platform
            // query itself (see DeviceLibrary.loadAll), not in a post-filter.
            scopeStore.value.drop(1).collect { refresh() }
        }
        scope.launch {
            // The badge overlay reads the LEDGER, whose verdicts land while a
            // verify/upload pass runs — typically right AFTER the initial
            // rebuild. Re-apply the overlay whenever the pass reports ledger
            // writes; the enumeration itself is reused, so this is cheap.
            progressBus.ledgerRevision.drop(1).conflate().collect {
                delay(CHANGE_COALESCE_MILLIS)
                refreshOverlay()
            }
        }
    }

    /** Re-applies the identity + ledger overlay over the cached enumeration
     *  (no MediaStore/PhotoKit re-scan). */
    private suspend fun refreshOverlay() {
        refreshMutex.withLock {
            val media = mediaByUri.values.toList()
            if (media.isNotEmpty()) rebuildItems(media)
        }
    }

    /** Re-reads access + library; safe to call from anywhere, off-main. */
    suspend fun refresh() {
        refreshMutex.withLock {
            val access = library.accessState()
            if (!access.canRead) {
                _state.update { it.copy(access = access, items = emptyList(), isLoading = false) }
                return
            }
            _state.update { it.copy(access = access, isLoading = true) }
            // SyncedOnly never touches the platform: no local side at all, on
            // every platform alike (iOS ignores scopes it can't express, so
            // deciding here is what keeps the semantics uniform).
            val libraryScope = scopeStore.value.value
            val media = if (libraryScope == DeviceLibraryScope.SyncedOnly) {
                emptyList()
            } else {
                runCatching { library.loadAll(libraryScope) }.getOrDefault(emptyList())
            }
            mediaByUri = media.associateBy { it.uri }
            rebuildItems(media)
            _state.update { it.copy(isLoading = false) }
        }
    }

    /** Fire-and-forget [refresh] — for callers that just changed the
     *  library (e.g. a device-trash) and want the UI ahead of the
     *  change observer's coalesced reload. */
    fun requestRefresh() {
        scope.launch { refresh() }
    }

    /** Marks the permission prompt as shown BEFORE launching it, so a
     *  dismissed dialog can't retrigger the auto-prompt in a loop. */
    fun markPrompted() {
        settings.putBoolean(KEY_PROMPTED, true)
        _state.update { it.copy(hasPrompted = true) }
    }

    /** Wire to [rememberDeviceLibraryAccessRequester]'s result. */
    fun onAccessResult(access: DeviceLibraryAccess) {
        _state.update { it.copy(access = access) }
        scope.launch { refresh() }
    }

    /**
     * Establishes server identities for [uris] where missing: hash
     * locally (bounded, never downloading iCloud originals), bulk-check
     * against the server, persist, and refresh the published items so
     * the merge dedups immediately. Fire-and-forget — the timeline
     * calls this with each settled viewport window.
     */
    fun requestIdentityResolution(uris: List<String>) {
        if (uris.isEmpty()) return
        scope.launch { resolveIdentities(uris) }
    }

    private suspend fun resolveIdentities(uris: List<String>) {
        resolveMutex.withLock {
            val media = mediaByUri
            val identities = runCatching { identityMap.all() }.getOrDefault(emptyMap())
            val now = Clock.System.now().toEpochMilliseconds()

            val toHash = ArrayList<DeviceMedia>()
            val toRecheck = ArrayList<DeviceIdentity>()
            for (uri in uris.distinct()) {
                val item = media[uri] ?: continue
                if (uri in unresolvable) continue
                val identity = identities[uri]?.takeIf { it.matchesFingerprint(item) }
                when {
                    identity?.assetId != null -> Unit // established — nothing to do
                    identity?.sha256 != null -> {
                        // Hashed but unmatched last time. Re-ask lazily so an
                        // upload from another device is eventually noticed
                        // (checkedAtMillis null = the check itself failed —
                        // retry on next visit).
                        val checkedAt = identity.checkedAtMillis
                        if (checkedAt == null || now - checkedAt >= RECHECK_MILLIS) {
                            toRecheck += identity
                        }
                    }
                    else -> toHash += item
                }
            }
            if (toHash.isEmpty() && toRecheck.isEmpty()) return

            // Hash phase: local-only, bounded. Failures (typically an
            // iCloud-offloaded original) are parked for the session.
            val semaphore = Semaphore(HASH_CONCURRENCY)
            val hashResults = coroutineScope {
                toHash.map { item ->
                    async {
                        semaphore.withPermit {
                            item to runCatching {
                                gallery.computeSha256(item, allowNetwork = false)
                            }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
            val hashed = ArrayList<DeviceIdentity>(hashResults.size)
            hashResults.forEach { (item, sha) ->
                if (sha == null) {
                    unresolvable += item.uri
                } else {
                    hashed += DeviceIdentity(
                        uri = item.uri,
                        sizeBytes = item.sizeBytes,
                        dateModifiedMillis = item.dateModifiedMillis,
                        sha256 = sha,
                        assetId = null,
                        checkedAtMillis = null,
                        displayName = item.displayName
                    )
                }
            }

            // Check phase: one bulk round-trip per 500 distinct hashes. A
            // failed batch keeps checkedAtMillis null so only the (cheap)
            // check is retried later — the hash work is already banked.
            // Servers older than /check-checksums answer 404: degrade to the
            // per-hash legacy lookup for the rest of the pass, mirroring
            // DeviceBackupRepository — otherwise identity resolution (and
            // with it device/server dedup) never works on those deployments.
            val candidates = hashed + toRecheck
            if (candidates.isEmpty()) return
            val assetBySha = HashMap<String, String>()
            var checkFailed = false
            var bulkSupported = true
            candidates.mapTo(LinkedHashSet()) { it.sha256!! }
                .chunked(CHECKSUM_BATCH)
                .forEach { batch ->
                    if (bulkSupported) {
                        try {
                            assetBySha.putAll(api.checkChecksums(batch))
                            return@forEach
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: PhotonneApiException) {
                            if (ex.status != 404) {
                                checkFailed = true
                                return@forEach
                            }
                            bulkSupported = false
                        } catch (ex: Exception) {
                            checkFailed = true
                            return@forEach
                        }
                    }
                    batch.forEach { sha ->
                        runCatching { api.assetExistsByChecksum(sha) }
                            .onSuccess { assetId -> if (assetId != null) assetBySha[sha] = assetId }
                            .onFailure { checkFailed = true }
                    }
                }
            val resolved = candidates.map { candidate ->
                val assetId = assetBySha[candidate.sha256]
                candidate.copy(
                    assetId = assetId,
                    checkedAtMillis = if (assetId == null && checkFailed) {
                        candidate.checkedAtMillis
                    } else now
                )
            }
            runCatching { identityMap.upsertAll(resolved) }

            // Republish so freshly-established identities dedup right away.
            // Through refreshOverlay, NOT the snapshot captured before the
            // hashing/network work: seconds have passed and the library may
            // have changed underneath (a deletion mid-resolution would come
            // back as a dead cell if we republished the stale list).
            if (hashed.isNotEmpty() || resolved.any { it.assetId != null }) {
                refreshOverlay()
            }
        }
    }

    /**
     * Maps the enumeration to published [TimelineItem]s under the
     * identity + ledger overlay, persisting any ledger knowledge the
     * identity map doesn't have yet.
     */
    private fun rebuildItems(media: List<DeviceMedia>) {
        _state.update { it.copy(items = buildItems(media)) }
    }

    /** The overlay mapping itself, shared by the timeline's published list
     *  and the per-bucket loads of the device folder browser. */
    private fun buildItems(media: List<DeviceMedia>): List<TimelineItem> {
        val ledgerRows = runCatching { ledger.allEntries() }.getOrDefault(emptyList())
        val identities = runCatching { identityMap.all() }.getOrDefault(emptyMap())

        val ledgerByUri = HashMap<String, LedgerEntry>(ledgerRows.size)
        val ledgerByFingerprint = HashMap<String, LedgerEntry>(ledgerRows.size)
        for (entry in ledgerRows) {
            ledgerByUri[entry.uri] = entry
            // Seconds precision: SAF lastModified carries millis, MediaStore
            // DATE_MODIFIED only seconds — rounding makes them comparable.
            if (entry.sizeBytes > 0L) {
                ledgerByFingerprint["${entry.sizeBytes}|${entry.dateModifiedMillis / 1000}"] =
                    entry
            }
        }

        val bridged = ArrayList<DeviceIdentity>()
        val items = media.map { item ->
            val identity = identities[item.uri]?.takeIf { it.matchesFingerprint(item) }
            val ledgerRow = ledgerByUri[item.uri]
                ?: ledgerByFingerprint["${item.sizeBytes}|${item.dateModifiedMillis / 1000}"]
            val ledgerAssetId = ledgerRow?.assetId
                ?.takeIf { it.isNotEmpty() && ledgerRow.state == LedgerState.Synced }

            // Bank what the backup flow already knows and the map doesn't.
            if (ledgerRow?.sha256 != null &&
                (identity?.sha256 == null || (identity.assetId == null && ledgerAssetId != null))
            ) {
                bridged += DeviceIdentity(
                    uri = item.uri,
                    sizeBytes = item.sizeBytes,
                    dateModifiedMillis = item.dateModifiedMillis,
                    sha256 = ledgerRow.sha256,
                    assetId = ledgerAssetId ?: identity?.assetId,
                    checkedAtMillis = ledgerRow.lastVerifiedAtMillis,
                    displayName = item.displayName
                )
            }

            val instant = naiveLocalInstant(item.dateCreatedMillis ?: item.dateModifiedMillis)
            TimelineItem(
                id = "device:${item.uri}",
                fileName = item.displayName,
                fullPath = if (item.relativePath.isBlank()) item.displayName
                else "${item.relativePath}/${item.displayName}",
                fileSize = item.sizeBytes,
                fileCreatedAt = instant,
                fileModifiedAt = naiveLocalInstant(item.dateModifiedMillis),
                extension = item.displayName.substringAfterLast('.', missingDelimiterValue = ""),
                scannedAt = instant,
                type = if (item.type == DeviceMediaType.Video) "VIDEO" else "IMAGE",
                checksum = identity?.sha256 ?: ledgerRow?.sha256,
                width = item.width,
                height = item.height,
                hasThumbnails = false,
                localThumbnailModel = item.uri,
                localUri = item.uri,
                localServerAssetId = identity?.assetId ?: ledgerAssetId,
                // A ledger row only exists for files under a BACKUP folder,
                // so Unknown there already means "awaiting verification →
                // pending" (the pre-library timeline badged it too). Files
                // outside the backup scope have no row and stay badge-less —
                // the full library never drowns in badges.
                localSyncBadge = when (ledgerRow?.state) {
                    LedgerState.Unknown -> LocalSyncBadge.Pending
                    LedgerState.NotSynced -> LocalSyncBadge.Pending
                    LedgerState.Failed -> LocalSyncBadge.Failed
                    else -> null
                }
            )
        }
        runCatching { identityMap.upsertAll(bridged) }
        return items
    }

    private companion object {
        /**
         * Re-frames a real epoch instant into the timeline's NAIVE-LOCAL
         * convention: the server stores capture dates as the photo's own
         * wall-clock labelled UTC (see CaptureLocalDate.kt), so every
         * server `fileCreatedAt` compares as wall-clock-as-UTC. Device
         * items must speak the same frame or the two sources sort ±(tz
         * offset) apart — the same photo would JUMP position the moment
         * its server copy hydrates, and midnight-adjacent photos would
         * bucket into a different month than the server put them in
         * (where the intra-month dedup can no longer see them). The
         * device's current zone approximates the wall clock at capture;
         * only photos taken in another timezone keep a residual offset,
         * and those converge once identity dedup replaces them.
         */
        fun naiveLocalInstant(epochMillis: Long): Instant =
            Instant.fromEpochMilliseconds(epochMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .toInstant(TimeZone.UTC)

        const val KEY_PROMPTED = "device_library.prompted"
        const val CHANGE_COALESCE_MILLIS = 1_000L

        /** Parallel local hashes during identity resolution — low on
         *  purpose: this runs while the user is browsing. */
        const val HASH_CONCURRENCY = 2

        /** Mirrors the server's cap on /check-checksums (1000) with the
         *  same headroom the backup verifier uses. */
        const val CHECKSUM_BATCH = 500

        /** How long an "unmatched" verdict is trusted before re-asking. */
        const val RECHECK_MILLIS = 24 * 60 * 60 * 1000L
    }
}
