package com.photonne.app.ui.devicebackup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.devicebackup.DeviceFolderRef
import com.photonne.app.data.devicebackup.DeviceMedia
import com.photonne.app.data.devicebackup.BackgroundSyncPreferences
import com.photonne.app.data.devicebackup.BackgroundSyncScheduler
import com.photonne.app.data.devicebackup.DeviceMediaSyncState
import com.photonne.app.data.devicebackup.UploadFailureReason
import com.photonne.app.data.devicebackup.UploadOutcome
import com.photonne.app.data.devicebackup.uploadInParallel
import com.photonne.app.data.devicebackup.DeviceMediaType
import com.photonne.app.data.devicebackup.DeviceBackupRepository
import com.photonne.app.data.devicebackup.LastBackupRun
import kotlinx.datetime.Clock
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.LocalSyncBadge
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

/**
 * One entry in the gallery grid. `media` carries the raw metadata
 * coming back from the platform layer; `syncState` is the verdict
 * after we've hashed the file and asked the server, lazily, when
 * the user scrolls past it or explicitly requests a refresh.
 */
data class DeviceBackupEntry(
    val media: DeviceMedia,
    val syncState: DeviceMediaSyncState = DeviceMediaSyncState.Unknown,
    val isSelected: Boolean = false,
    /**
     * Live upload progress (0..1) while this entry is `Uploading`, for the
     * per-file progress bar in the pending list. Null when not uploading, or
     * before the first byte-progress tick arrives (renders indeterminate).
     */
    val uploadProgress: Float? = null
)

/**
 * Outcome of the last completed sync batch. Carried separately from the
 * free-form [DeviceBackupUiState.statusMessage] so the UI can render it
 * with proper i18n + per-reason breakdown instead of a hardcoded string.
 */
data class SyncSummary(
    val completed: Int,
    val skipped: Int,
    val failed: Int,
    val failuresByReason: Map<UploadFailureReason, Int> = emptyMap()
)

data class DeviceBackupUiState(
    val isSupported: Boolean = true,
    val isBackupEnabled: Boolean = false,
    val folder: DeviceFolderRef? = null,
    val entries: List<DeviceBackupEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isCheckingHashes: Boolean = false,
    val hashProgress: DeviceBackupRepository.VerificationProgress? = null,
    val isSyncing: Boolean = false,
    val isFreeingSpace: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val error: UiError? = null,
    val statusMessage: String? = null,
    val lastSyncSummary: SyncSummary? = null,
    val lastRun: LastBackupRun? = null,
    val backgroundSync: BackgroundSyncPreferences = BackgroundSyncPreferences(
        enabled = false,
        requireWifi = true,
        requireCharging = true
    )
) {
    val selectedCount: Int get() = entries.count { it.isSelected }
    val syncableSelectedCount: Int get() = entries.count {
        it.isSelected &&
            it.syncState !is DeviceMediaSyncState.Synced &&
            it.syncState !is DeviceMediaSyncState.Ignored
    }
    val syncedCount: Int get() = entries.count {
        it.syncState is DeviceMediaSyncState.Synced
    }
    val failedCount: Int get() = entries.count {
        it.syncState is DeviceMediaSyncState.Failed
    }
    val ignoredCount: Int get() = entries.count {
        it.syncState is DeviceMediaSyncState.Ignored
    }
    val uploadingCount: Int get() = entries.count {
        it.syncState is DeviceMediaSyncState.Uploading
    }

    /**
     * Entries known not to be on the server yet (NotSynced, Uploading
     * or Failed). Used by the Timeline merge so device-only items
     * surface alongside server assets with a sync badge. We
     * deliberately leave Unknown out — until we've hashed and asked
     * the server, we can't tell whether they're already backed up,
     * and assuming "pending" would double-show every photo.
     */
    val pendingEntries: List<DeviceBackupEntry> get() = entries.filter {
        it.syncState is DeviceMediaSyncState.NotSynced ||
            it.syncState is DeviceMediaSyncState.Uploading ||
            it.syncState is DeviceMediaSyncState.Failed
    }
}

/** Progress snapshot while the manual sync is uploading files. */
data class SyncProgress(
    val total: Int,
    val completed: Int,
    val skipped: Int,
    val failed: Int,
    val currentName: String? = null,
    /** How many uploads are in flight right now (parallel sync). */
    val inFlight: Int = 0
)

class DeviceBackupViewModel(
    private val repository: DeviceBackupRepository,
    private val backgroundScheduler: BackgroundSyncScheduler,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DeviceBackupUiState(
            isSupported = repository.isSupported,
            isBackupEnabled = repository.isBackupEnabled(),
            lastRun = repository.lastRun(),
            backgroundSync = repository.backgroundSyncPreferences()
        )
    )
    val state: StateFlow<DeviceBackupUiState> = _state.asStateFlow()

    init {
        // Reconcile platform scheduler with whatever the user had configured
        // previously — covers the case where the app was uninstalled/reinstalled
        // and WorkManager forgot about us. Idempotent on every app start.
        backgroundScheduler.apply(repository.backgroundSyncPreferences())

        // Eager-load the saved folder so the timeline merge can show
        // device-only photos as soon as the user opens the app —
        // otherwise we'd have to wait for them to visit the Backup
        // screen before pending items become visible.
        if (repository.isSupported && repository.savedFolder() != null) {
            // Show the last scan instantly (cache), then re-scan to reconcile.
            seedFromCache()
            ensureLoaded()
        }
    }

    /**
     * Populate [entries] from the last persisted scan so the timeline can
     * surface device-only photos immediately on launch, instead of waiting
     * for the full folder re-enumeration. Runs off the main thread and bails
     * if a fresh scan has already filled entries; the subsequent
     * [refreshFolderContents] reconciles by URI.
     */
    private fun seedFromCache() {
        if (_state.value.entries.isNotEmpty()) return
        viewModelScope.launch {
            val cached = withContext(Dispatchers.Default) { repository.cachedMedia() }
            if (cached.isEmpty()) return@launch
            _state.update { current ->
                if (current.entries.isNotEmpty()) current
                else current.copy(entries = cached.map { DeviceBackupEntry(media = it) })
            }
        }
    }

    fun setBackupEnabled(enabled: Boolean) {
        repository.setBackupEnabled(enabled)
        _state.update { it.copy(isBackupEnabled = enabled) }
        if (enabled) ensureLoaded()
        // Toggling the master switch off also disables auto-backup at the
        // OS scheduler so the worker doesn't keep waking up for nothing.
        if (!enabled) {
            repository.setAutoBackupEnabled(false)
            val prefs = repository.backgroundSyncPreferences()
            _state.update { it.copy(backgroundSync = prefs) }
            backgroundScheduler.apply(prefs)
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        updateBackgroundPrefs { repository.setAutoBackupEnabled(enabled) }
        // Flipping the switch should visibly DO something: the periodic job
        // won't fire until the end of its first 15-min window (and only
        // under its charging/Wi-Fi constraints), so kick one pass now.
        if (enabled) {
            backgroundScheduler.requestImmediateSync(repository.backgroundSyncPreferences())
        }
    }

    fun setRequireWifi(value: Boolean) =
        updateBackgroundPrefs { repository.setRequireWifi(value) }

    fun setRequireCharging(value: Boolean) =
        updateBackgroundPrefs { repository.setRequireCharging(value) }

    /** Turbo only tunes the in-app/worker upload fan-out; it doesn't change the
     *  OS schedule, so it skips [backgroundScheduler.apply] (unlike the other
     *  prefs, which reconcile WorkManager constraints). */
    fun setTurbo(value: Boolean) {
        repository.setTurboEnabled(value)
        _state.update { it.copy(backgroundSync = repository.backgroundSyncPreferences()) }
    }

    private fun updateBackgroundPrefs(mutate: () -> Unit) {
        mutate()
        val prefs = repository.backgroundSyncPreferences()
        _state.update { it.copy(backgroundSync = prefs) }
        backgroundScheduler.apply(prefs)
    }

    /**
     * Called every time the screen is composed. If the folder hasn't
     * been resolved yet we restore the saved bookmark; if it has, we
     * re-list its contents so new photos taken since the last visit
     * show up. Sync states already computed are preserved.
     */
    fun ensureLoaded() {
        if (_state.value.isLoading) return
        if (!repository.isSupported) return
        // A background pass may have run since we last looked.
        _state.update { it.copy(lastRun = repository.lastRun()) }
        val folder = _state.value.folder
        if (folder != null) {
            refreshFolderContents(folder)
            return
        }
        val saved = repository.savedFolder() ?: return
        viewModelScope.launch {
            val resumed = runCatching { repository.restoreFolder(saved.uri) }.getOrNull()
            if (resumed == null) {
                // Stale bookmark — drop it so we don't keep retrying.
                repository.forgetFolder()
                return@launch
            }
            applyFolder(resumed)
        }
    }

    /**
     * Re-list a folder we've already loaded once, merging new entries
     * in and keeping the [DeviceBackupEntry.syncState] of anything we'd
     * already checked. Runs without toggling `isLoading` so the
     * existing grid stays visible during the re-scan — only the
     * initial load (empty entries) shows a spinner.
     */
    private fun refreshFolderContents(folder: DeviceFolderRef) {
        val showSpinner = _state.value.entries.isEmpty()
        if (showSpinner) {
            _state.update { it.copy(isLoading = true, error = null) }
        } else {
            _state.update { it.copy(error = null) }
        }
        viewModelScope.launch {
            val result = runCatching { repository.listMedia(folder) }
            result
                .onSuccess { items ->
                    _state.update { current ->
                        val previous = current.entries.associateBy { it.media.uri }
                        current.copy(
                            isLoading = false,
                            entries = items.map { media ->
                                val existing = previous[media.uri]
                                if (existing != null) {
                                    existing.copy(
                                        media = media.copy(sha256 = existing.media.sha256)
                                    )
                                } else {
                                    DeviceBackupEntry(media = media)
                                }
                            }
                        )
                    }
                    // Verdicts already proven in a previous session appear
                    // instantly — no hashing, no network.
                    seedSyncStatesFromLedger(folder)
                    // …then reconcile against the server. Cheap now: only
                    // new/changed files hash, the rest is 1-2 bulk calls.
                    maybeAutoVerify()
                    // Persist the fresh scan so the next launch can seed the
                    // timeline instantly from cache.
                    withContext(Dispatchers.Default) {
                        repository.saveCachedMedia(folder.uri, items)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Could not refresh folder")
                        )
                    }
                }
        }
    }

    fun onFolderPicked(folder: DeviceFolderRef?) {
        if (folder == null) return
        repository.rememberFolder(folder)
        applyFolder(folder)
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, statusMessage = null) }
    }

    fun toggleSelection(uri: String) {
        _state.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.media.uri == uri) entry.copy(isSelected = !entry.isSelected)
                    else entry
                }
            )
        }
    }

    fun selectAllNotSynced() {
        _state.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    // Already-synced and user-skipped items are never auto-queued.
                    if (entry.syncState is DeviceMediaSyncState.Synced ||
                        entry.syncState is DeviceMediaSyncState.Ignored
                    ) entry
                    else entry.copy(isSelected = true)
                }
            )
        }
    }

    fun clearSelection() {
        _state.update { current ->
            current.copy(entries = current.entries.map { it.copy(isSelected = false) })
        }
    }

    /**
     * Incremental verification pass backed by the persistent ledger:
     * only new/changed files are hashed (everything already verified is
     * skipped), and the server is asked in bulk — a couple of HTTP calls
     * for the whole folder instead of one per file. Cancelling mid-pass
     * loses nothing: hashes and verdicts persist as they are computed.
     */
    fun refreshSyncStates() {
        val folder = _state.value.folder ?: return
        if (_state.value.isCheckingHashes) return
        _state.update { it.copy(isCheckingHashes = true, error = null, hashProgress = null) }
        viewModelScope.launch {
            val scanned = _state.value.entries.map { it.media }
            val result = runCatching {
                repository.verifyAgainstServer(
                    folder = folder,
                    scanned = scanned,
                    onProgress = { progress ->
                        _state.update { it.copy(hashProgress = progress) }
                    },
                    shouldContinue = { _state.value.isCheckingHashes }
                )
            }
            result
                .onSuccess { states -> applySyncStates(states) }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "Could not verify pending files"))
                    }
                }
            _state.update { it.copy(isCheckingHashes = false, hashProgress = null) }
        }
    }

    /** Merge ledger/server verdicts into the grid, leaving in-flight
     *  `Uploading` entries alone so an active sync isn't visually reset. */
    private fun applySyncStates(states: Map<String, DeviceMediaSyncState>) {
        if (states.isEmpty()) return
        _state.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    val verdict = states[entry.media.uri] ?: return@map entry
                    if (entry.syncState is DeviceMediaSyncState.Uploading) entry
                    else entry.copy(syncState = verdict)
                }
            )
        }
    }

    /** Seed sync badges from the persisted ledger — instant, no hashing or
     *  network. Only fills entries still in `Unknown` so fresher in-session
     *  states are never downgraded. */
    private fun seedSyncStatesFromLedger(folder: DeviceFolderRef) {
        viewModelScope.launch {
            val states = withContext(Dispatchers.Default) { repository.syncStatesFor(folder) }
            if (states.isEmpty()) return@launch
            _state.update { current ->
                current.copy(
                    entries = current.entries.map { entry ->
                        if (entry.syncState !is DeviceMediaSyncState.Unknown) entry
                        else states[entry.media.uri]?.let { entry.copy(syncState = it) } ?: entry
                    }
                )
            }
        }
    }

    fun stopHashCheck() {
        _state.update { it.copy(isCheckingHashes = false) }
    }

    /**
     * Kicks the incremental verification automatically after a folder scan.
     * The ledger makes this cheap enough to run on every screen entry, so
     * the pending count is always real instead of "Unknown until you ask".
     */
    private fun maybeAutoVerify() {
        val current = _state.value
        if (!current.isBackupEnabled) return
        if (current.isCheckingHashes || current.isSyncing) return
        refreshSyncStates()
    }

    /** Selects everything not yet on the server and uploads it — the status
     *  card's one-tap "upload now" action.
     *
     *  On Android this hands the whole pass to a prioritized foreground worker
     *  so a big backlog keeps uploading at full speed even with the app
     *  backgrounded (progress shows in a notification). On iOS/Desktop, where
     *  there's no such primitive, [BackgroundSyncScheduler.requestForegroundBackup]
     *  returns false and we run it in-process with live per-file progress. */
    fun syncAllPending() {
        if (_state.value.isSyncing) return
        if (backgroundScheduler.requestForegroundBackup()) {
            // The foreground worker owns this pass now; reflect that a run was
            // kicked so the next screen refresh picks up its results.
            _state.update { it.copy(statusMessage = null, error = null) }
            return
        }
        selectAllNotSynced()
        syncSelected()
    }

    /** Re-uploads exactly one entry — the failure dialog's retry action. */
    fun retrySingle(uri: String) {
        if (_state.value.isSyncing) return
        _state.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    entry.copy(isSelected = entry.media.uri == uri)
                }
            )
        }
        syncSelected()
    }

    /** Skips one file: it stops counting as pending and is never re-queued
     *  until [unignore]. The failure dialog's "Omitir" action. */
    fun ignoreSingle(uri: String) {
        val folder = _state.value.folder ?: return
        repository.markIgnored(folder, uri)
        _state.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.media.uri == uri) {
                        entry.copy(
                            syncState = DeviceMediaSyncState.Ignored,
                            isSelected = false,
                            uploadProgress = null
                        )
                    } else entry
                }
            )
        }
    }

    /** Bulk skip: every currently-failed file becomes ignored in one shot.
     *  The pending list's "Omitir todos los fallidos" action. */
    fun ignoreFailed() {
        val folder = _state.value.folder ?: return
        repository.ignoreFailed(folder)
        _state.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.syncState is DeviceMediaSyncState.Failed) {
                        entry.copy(
                            syncState = DeviceMediaSyncState.Ignored,
                            isSelected = false,
                            uploadProgress = null
                        )
                    } else entry
                }
            )
        }
    }

    /** Reverses a skip: the file returns to Unknown and the next verify pass
     *  re-hashes and re-checks it. */
    fun unignore(uri: String) {
        val folder = _state.value.folder ?: return
        repository.unignore(folder, uri)
        _state.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.media.uri == uri) {
                        entry.copy(syncState = DeviceMediaSyncState.Unknown)
                    } else entry
                }
            )
        }
        // Re-verify so the un-skipped file gets a fresh verdict right away.
        maybeAutoVerify()
    }

    /** Uploads every selected entry that isn't already synced. */
    fun syncSelected() {
        if (_state.value.isSyncing) return
        val selected = _state.value.entries.filter {
            it.isSelected &&
                it.syncState !is DeviceMediaSyncState.Synced &&
                it.syncState !is DeviceMediaSyncState.Ignored
        }
        if (selected.isEmpty()) return

        _state.update {
            it.copy(
                isSyncing = true,
                error = null,
                statusMessage = null,
                syncProgress = SyncProgress(
                    total = selected.size,
                    completed = 0,
                    skipped = 0,
                    failed = 0
                )
            )
        }

        viewModelScope.launch {
            var completed = 0
            var skipped = 0
            val failureReasonCounts = mutableMapOf<UploadFailureReason, Int>()
            var failed = 0
            var inFlight = 0

            // Marks an entry Synced and refreshes the progress snapshot. Shared
            // by the Uploaded and Skipped (server-side dedup) outcomes, which
            // differ only in which counter they bump.
            fun markSynced(media: DeviceMedia, assetId: String) {
                _state.value.folder?.let { folder ->
                    repository.markUploaded(folder, media.uri, assetId)
                }
                _state.update { current ->
                    current.copy(
                        entries = current.entries.map { e ->
                            if (e.media.uri == media.uri) {
                                e.copy(
                                    syncState = DeviceMediaSyncState.Synced(assetId),
                                    isSelected = false,
                                    uploadProgress = null
                                )
                            } else e
                        },
                        syncProgress = current.syncProgress?.copy(
                            completed = completed,
                            skipped = skipped,
                            inFlight = inFlight
                        )
                    )
                }
            }

            // Upload with bounded concurrency. onItemStart/onItemDone run under
            // the helper's mutex, so the counters and inFlight stay consistent
            // and the StateFlow updates never race. Cancellation: cancelSync()
            // flips isSyncing=false; in-flight uploads finish, no new ones start.
            uploadInParallel(
                pending = selected.map { it.media },
                concurrency = repository.uploadConcurrency(),
                upload = { media, report -> repository.upload(media, onProgress = report) },
                shouldContinue = { _state.value.isSyncing },
                onItemStart = { media ->
                    inFlight++
                    _state.update { current ->
                        current.copy(
                            entries = current.entries.map { e ->
                                if (e.media.uri == media.uri) {
                                    e.copy(
                                        syncState = DeviceMediaSyncState.Uploading,
                                        uploadProgress = null
                                    )
                                } else e
                            },
                            syncProgress = current.syncProgress?.copy(
                                currentName = media.displayName,
                                inFlight = inFlight
                            )
                        )
                    }
                },
                onItemProgress = { media, fraction ->
                    updateUploadProgress(media.uri, fraction)
                },
                onItemDone = { media, outcome, _ ->
                    inFlight--
                    when (outcome) {
                        is UploadOutcome.Uploaded -> {
                            completed++
                            markSynced(media, outcome.assetId)
                        }
                        is UploadOutcome.Skipped -> {
                            skipped++
                            markSynced(media, outcome.assetId)
                        }
                        is UploadOutcome.Failed -> {
                            failed++
                            failureReasonCounts[outcome.reason] =
                                (failureReasonCounts[outcome.reason] ?: 0) + 1
                            _state.value.folder?.let { folder ->
                                repository.markUploadFailed(
                                    folder, media.uri, outcome.reason, outcome.detail
                                )
                            }
                            _state.update { current ->
                                current.copy(
                                    entries = current.entries.map { e ->
                                        if (e.media.uri == media.uri) {
                                            e.copy(
                                                syncState = DeviceMediaSyncState.Failed(
                                                    reason = outcome.reason,
                                                    detail = outcome.detail
                                                ),
                                                uploadProgress = null
                                            )
                                        } else e
                                    },
                                    syncProgress = current.syncProgress?.copy(
                                        failed = failed,
                                        inFlight = inFlight
                                    )
                                )
                            }
                        }
                    }
                }
            )
            val run = LastBackupRun(
                finishedAtMillis = Clock.System.now().toEpochMilliseconds(),
                uploaded = completed,
                skipped = skipped,
                failed = failed,
                background = false
            )
            repository.recordLastRun(run)
            _state.update {
                it.copy(
                    isSyncing = false,
                    lastRun = run,
                    lastSyncSummary = SyncSummary(
                        completed = completed,
                        skipped = skipped,
                        failed = failed,
                        failuresByReason = failureReasonCounts.toMap()
                    )
                )
            }
        }
    }

    /**
     * Updates the live upload fraction of one entry. Throttled to
     * whole-percent steps so a fast upload doesn't trigger a recomposition on
     * every byte-progress tick. Called concurrently from the in-flight uploads;
     * each only touches its own entry and `_state.update` is atomic, so no lock
     * is needed.
     */
    private fun updateUploadProgress(uri: String, fraction: Float) {
        val pct = (fraction * 100).toInt()
        val currentPct = _state.value.entries
            .firstOrNull { it.media.uri == uri }
            ?.uploadProgress
            ?.let { (it * 100).toInt() } ?: -1
        if (pct == currentPct) return
        _state.update { current ->
            current.copy(
                entries = current.entries.map { e ->
                    if (e.media.uri == uri && e.syncState is DeviceMediaSyncState.Uploading) {
                        e.copy(uploadProgress = fraction)
                    } else e
                }
            )
        }
    }

    fun cancelSync() {
        _state.update { it.copy(isSyncing = false) }
    }

    /**
     * Deletes from local storage every entry that we've confirmed is
     * already on the server. The "free up space" entry point assumes
     * the caller has already shown a confirmation dialog — by the
     * time this fires, the user has agreed to the deletion.
     */
    fun freeUpSyncedSpace() {
        if (_state.value.isFreeingSpace || _state.value.isSyncing) return
        val targets = _state.value.entries.filter {
            it.syncState is DeviceMediaSyncState.Synced
        }
        if (targets.isEmpty()) return

        _state.update {
            it.copy(
                isFreeingSpace = true,
                error = null,
                statusMessage = null
            )
        }

        viewModelScope.launch {
            var deleted = 0
            var failed = 0
            for (entry in targets) {
                if (!_state.value.isFreeingSpace) break
                val ok = runCatching {
                    repository.deleteLocal(entry.media)
                }.getOrDefault(false)
                if (ok) deleted++ else failed++
                if (ok) {
                    _state.update { current ->
                        current.copy(
                            entries = current.entries.filterNot {
                                it.media.uri == entry.media.uri
                            }
                        )
                    }
                }
            }
            _state.update {
                val msg = if (failed == 0) {
                    "Liberados $deleted archivos del dispositivo"
                } else {
                    "Liberados $deleted · No se pudo borrar $failed"
                }
                it.copy(isFreeingSpace = false, statusMessage = msg)
            }
        }
    }

    fun pickAnotherFolder() {
        _state.update {
            it.copy(folder = null, entries = emptyList(), syncProgress = null)
        }
    }

    private fun applyFolder(folder: DeviceFolderRef) {
        _state.update { it.copy(folder = folder, isLoading = true, error = null) }
        viewModelScope.launch {
            val media = runCatching { repository.listMedia(folder) }
            media
                .onSuccess { items ->
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            entries = items.map { DeviceBackupEntry(media = it) }
                        )
                    }
                    seedSyncStatesFromLedger(folder)
                    maybeAutoVerify()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Could not list folder")
                        )
                    }
                }
        }
    }

    fun thumbnailModel(media: DeviceMedia): String = repository.thumbnailModel(media)

    /**
     * Renders every device entry as a synthetic [TimelineItem] so the
     * timeline can show the full device gallery — confirmed-pending
     * items get a cloud badge, items whose state is still unknown get
     * no badge (the merge step dedups them against server-known
     * items, so most unknowns disappear before reaching the screen).
     *
     * Confirmed `Synced` entries are excluded outright: the server
     * timeline already contains them, and adding a device copy would
     * just duplicate the cell.
     */
    fun deviceTimelineItems(): List<TimelineItem> {
        val source = _state.value.entries
        if (source.isEmpty()) return emptyList()
        return source.mapNotNull { entry ->
            val state = entry.syncState
            if (state is DeviceMediaSyncState.Synced) return@mapNotNull null
            val media = entry.media
            val instant = Instant.fromEpochMilliseconds(media.dateModifiedMillis)
            val ext = media.displayName.substringAfterLast('.', missingDelimiterValue = "")
            val type = if (media.type == DeviceMediaType.Video) "VIDEO" else "IMAGE"
            val badge = when (state) {
                DeviceMediaSyncState.Uploading -> LocalSyncBadge.Uploading
                is DeviceMediaSyncState.Failed -> LocalSyncBadge.Failed
                DeviceMediaSyncState.NotSynced -> LocalSyncBadge.Pending
                // Unknown: treat as pending until a hash check proves
                // otherwise. The merge step still dedups against
                // server entries by checksum / (fileName, fileSize),
                // so most photos already on the server won't show up
                // here at all.
                DeviceMediaSyncState.Unknown -> LocalSyncBadge.Pending
                is DeviceMediaSyncState.Synced -> return@mapNotNull null
                // Skipped by the user — don't clutter the timeline with it.
                DeviceMediaSyncState.Ignored -> return@mapNotNull null
            }
            TimelineItem(
                id = "device:${media.uri}",
                fileName = media.displayName,
                fullPath = if (media.relativePath.isBlank()) media.displayName
                else "${media.relativePath}/${media.displayName}",
                fileSize = media.sizeBytes,
                fileCreatedAt = instant,
                fileModifiedAt = instant,
                extension = ext,
                scannedAt = instant,
                type = type,
                checksum = media.sha256,
                hasThumbnails = false,
                localThumbnailModel = repository.thumbnailModel(media),
                localUri = media.uri,
                localSyncBadge = badge
            )
        }
    }

    /** Look up the [DeviceBackupEntry] backing a synthetic local timeline item. */
    fun pendingEntryByUri(uri: String): DeviceBackupEntry? =
        _state.value.entries.firstOrNull { it.media.uri == uri }

    /**
     * Builds the minimal [TimelineItem] that lets AssetDetailScreen
     * boot before its own ViewModel fetches the full server-side
     * `AssetDetail` for [entry]. Only valid for entries we've already
     * matched against the server (i.e. with a `Synced` state).
     */
    fun timelineItemFor(entry: DeviceBackupEntry): TimelineItem? {
        val synced = entry.syncState as? DeviceMediaSyncState.Synced ?: return null
        val media = entry.media
        val instant = Instant.fromEpochMilliseconds(media.dateModifiedMillis)
        val ext = media.displayName.substringAfterLast('.', missingDelimiterValue = "")
        val type = if (media.type == DeviceMediaType.Video) "VIDEO" else "IMAGE"
        return TimelineItem(
            id = synced.assetId,
            fileName = media.displayName,
            fullPath = if (media.relativePath.isBlank()) media.displayName
            else "${media.relativePath}/${media.displayName}",
            fileSize = media.sizeBytes,
            fileCreatedAt = instant,
            fileModifiedAt = instant,
            extension = ext,
            scannedAt = instant,
            type = type,
            checksum = media.sha256
        )
    }
}
