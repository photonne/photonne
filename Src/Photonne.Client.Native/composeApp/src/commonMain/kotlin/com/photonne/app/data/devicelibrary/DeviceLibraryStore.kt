package com.photonne.app.data.devicelibrary

import com.photonne.app.data.devicebackup.BackupLedger
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

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
 * Sync knowledge is an overlay, never a prerequisite: rows the backup
 * ledger already knows contribute their SHA-256 (so the bucket merge
 * dedups the local copy against the server item) and a badge for
 * confirmed pending/failed files. Unmatched files simply render with
 * no badge — display never waits for hashing, verification, or the
 * network. Ledger rows are keyed by the backup flow's SAF uris on
 * Android, so the join also tries a (size, mtime-seconds) fingerprint
 * to bridge them to MediaStore uris; on iOS both flows share the
 * `photokit:` scheme and the uri join is exact.
 */
class DeviceLibraryStore(
    private val library: DeviceLibrary,
    private val ledger: BackupLedger,
    private val settings: Settings
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()
    private var started = false

    private val _state = MutableStateFlow(
        DeviceLibraryUiState(hasPrompted = settings.getBoolean(KEY_PROMPTED, false))
    )
    val state: StateFlow<DeviceLibraryUiState> = _state.asStateFlow()

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
            val media = runCatching { library.loadAll() }.getOrDefault(emptyList())
            val overlay = runCatching { ledger.allEntries() }.getOrDefault(emptyList())
            val items = buildItems(media, overlay)
            _state.update { it.copy(items = items, isLoading = false) }
        }
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

    private fun buildItems(
        media: List<DeviceMedia>,
        overlay: List<LedgerEntry>
    ): List<TimelineItem> {
        val byUri = HashMap<String, LedgerEntry>(overlay.size)
        val byFingerprint = HashMap<String, LedgerEntry>(overlay.size)
        for (entry in overlay) {
            byUri[entry.uri] = entry
            // Seconds precision: SAF lastModified carries millis, MediaStore
            // DATE_MODIFIED only seconds — rounding makes them comparable.
            if (entry.sizeBytes > 0L) {
                byFingerprint["${entry.sizeBytes}|${entry.dateModifiedMillis / 1000}"] = entry
            }
        }
        return media.map { item ->
            val ledgerRow = byUri[item.uri]
                ?: byFingerprint["${item.sizeBytes}|${item.dateModifiedMillis / 1000}"]
            val instant = Instant.fromEpochMilliseconds(
                item.dateCreatedMillis ?: item.dateModifiedMillis
            )
            TimelineItem(
                id = "device:${item.uri}",
                fileName = item.displayName,
                fullPath = if (item.relativePath.isBlank()) item.displayName
                else "${item.relativePath}/${item.displayName}",
                fileSize = item.sizeBytes,
                fileCreatedAt = instant,
                fileModifiedAt = Instant.fromEpochMilliseconds(item.dateModifiedMillis),
                extension = item.displayName.substringAfterLast('.', missingDelimiterValue = ""),
                scannedAt = instant,
                type = if (item.type == DeviceMediaType.Video) "VIDEO" else "IMAGE",
                checksum = ledgerRow?.sha256,
                hasThumbnails = false,
                localThumbnailModel = item.uri,
                localUri = item.uri,
                // Only CONFIRMED verdicts badge. Unknown rows are just
                // "photos on this phone" — claiming Pending for a whole
                // unverified library would drown the grid in badges.
                localSyncBadge = when (ledgerRow?.state) {
                    LedgerState.NotSynced -> LocalSyncBadge.Pending
                    LedgerState.Failed -> LocalSyncBadge.Failed
                    else -> null
                }
            )
        }
    }

    private companion object {
        const val KEY_PROMPTED = "device_library.prompted"
        const val CHANGE_COALESCE_MILLIS = 1_000L
    }
}
