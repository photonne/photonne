package com.photonne.app.ui.devicebackup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.devicebackup.DeviceFolderRef
import com.photonne.app.data.devicebackup.DeviceMedia
import com.photonne.app.data.devicebackup.BackgroundSyncPreferences
import com.photonne.app.data.devicebackup.BackgroundSyncScheduler
import com.photonne.app.data.devicebackup.BackupActivity
import com.photonne.app.data.devicebackup.BackupOrigin
import com.photonne.app.data.devicebackup.BackupPhase
import com.photonne.app.data.devicebackup.BackupProgressBus
import com.photonne.app.data.devicebackup.DeviceMediaSyncState
import com.photonne.app.data.devicebackup.UploadFailureReason
import com.photonne.app.data.devicebackup.UploadOutcome
import com.photonne.app.data.devicebackup.uploadInParallel
import com.photonne.app.data.devicebackup.withBackgroundExecution
import com.photonne.app.data.devicebackup.DeviceMediaType
import com.photonne.app.data.devicebackup.DeviceBackupRepository
import com.photonne.app.data.devicebackup.LastBackupRun
import kotlinx.datetime.Clock
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.photonne.app.resources.Res
import com.photonne.app.resources.backup_error_list_folder
import com.photonne.app.resources.backup_error_refresh_folder
import com.photonne.app.resources.backup_error_verify
import com.photonne.app.resources.device_backup_free_space_done
import com.photonne.app.resources.device_backup_free_space_partial
import com.photonne.app.ui.format.humanBytes
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.getString

/**
 * One entry in the gallery grid. `media` carries the raw metadata
 * coming back from the platform layer; `syncState` is the verdict
 * after we've hashed the file and asked the server, lazily, when
 * the user scrolls past it or explicitly requests a refresh.
 */
data class DeviceBackupEntry(
    val media: DeviceMedia,
    /** Which backed-up folder this file was listed from. Ledger rows are keyed
     *  by folder, so every verdict write needs it. */
    val folderUri: String,
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
    val folders: List<DeviceFolderRef> = emptyList(),
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
    /**
     * Live upload fraction per device URI for a pass driven by the worker.
     * Kept out of [entries] on purpose: patching a 20k-item list on every
     * byte-progress tick would be far more expensive than a map lookup at
     * render time.
     */
    val externalItemProgress: Map<String, Float> = emptyMap(),
    /**
     * Set while a pass started OUTSIDE this viewmodel is running (the Android
     * foreground worker or the scheduled background one). Null when idle or
     * when the pass is our own, so the UI knows which cancel path applies.
     */
    val activeOrigin: BackupOrigin? = null,
    val backgroundSync: BackgroundSyncPreferences = BackgroundSyncPreferences(
        enabled = false,
        requireWifi = true,
        requireCharging = false
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

    /** How much is still to upload. A file count says nothing about whether
     *  this is a two-second job or a 40 GB overnight one. */
    val pendingBytes: Long get() = pendingEntries.sumOf { it.media.sizeBytes }

    /**
     * Whether a "stop" makes sense right now. The OS-scheduled pass is excluded:
     * WorkManager can't cancel a single run of periodic work without dropping
     * the schedule itself, and offering a button that quietly does the wrong
     * thing is worse than not offering one.
     */
    val canStopCurrentPass: Boolean
        get() = (isSyncing || isCheckingHashes) && activeOrigin != BackupOrigin.Background

    /** How much local storage "free up space" would actually reclaim. */
    val syncedBytes: Long get() = entries
        .filter { it.syncState is DeviceMediaSyncState.Synced }
        .sumOf { it.media.sizeBytes }
}

/** Progress snapshot while the manual sync is uploading files. */
data class SyncProgress(
    val total: Int,
    val completed: Int,
    val skipped: Int,
    val failed: Int,
    val currentName: String? = null,
    /** How many uploads are in flight right now (parallel sync). */
    val inFlight: Int = 0,
    /**
     * Bytes finished / total for this batch. The bar tracks these instead of
     * the file counts: by file count a 2 GB video and a 200 KB screenshot are
     * the same step, so the bar stalls and then leaps.
     */
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L
) {
    /** 0..1, weighted by size when we know it, by file count otherwise. */
    val fraction: Float
        get() = when {
            bytesTotal > 0L -> (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
            total > 0 -> ((completed + skipped + failed).toFloat() / total).coerceIn(0f, 1f)
            else -> 0f
        }
}

class DeviceBackupViewModel(
    private val repository: DeviceBackupRepository,
    private val backgroundScheduler: BackgroundSyncScheduler,
    private val progressBus: BackupProgressBus,
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

    /**
     * True while THIS viewmodel drives a pass. Bus updates coming from a worker
     * pass are ignored meanwhile, so a background run finishing can't flip our
     * own `isCheckingHashes` gate and abort the verification we're running.
     */
    private var ownPassActive = false

    /** Throttle for the ledger re-reads triggered by a worker pass. */
    private var lastLedgerRefreshMillis = 0L

    init {
        observeExternalPasses()

        // Reconcile platform scheduler with whatever the user had configured
        // previously — covers the case where the app was uninstalled/reinstalled
        // and WorkManager forgot about us. Idempotent on every app start.
        backgroundScheduler.apply(repository.backgroundSyncPreferences())

        // Eager-load the saved folder so the timeline merge can show
        // device-only photos as soon as the user opens the app —
        // otherwise we'd have to wait for them to visit the Backup
        // screen before pending items become visible.
        if (repository.isSupported && repository.savedFolders().isNotEmpty()) {
            // Show the last scan instantly (cache), then re-scan to reconcile.
            seedFromCache()
            ensureLoaded()
        }
    }

    /**
     * Mirrors passes driven by the OS worker (Android "upload now" and the
     * scheduled background run) into this state. Without it, handing a pass to
     * the worker left the screen frozen on the pre-tap numbers: no progress, no
     * shrinking pending list, nothing until the user navigated away and back.
     */
    private fun observeExternalPasses() {
        viewModelScope.launch {
            progressBus.activity.collect { applyExternalActivity(it) }
        }
        viewModelScope.launch {
            // The runner bumps this after every item it writes, so the pending
            // list drains live instead of in one jump at the end.
            progressBus.ledgerRevision.collect { refreshFromLedger() }
        }
    }

    private fun applyExternalActivity(activity: BackupActivity?) {
        if (ownPassActive) return
        if (activity == null) {
            if (_state.value.activeOrigin == null) return
            // The worker owns the counters now, so lift its outcome into the
            // same summary the in-process path produces — otherwise a pass on
            // Android would end with no report at all.
            val finished = repository.lastRun()
            _state.update {
                it.copy(
                    lastSyncSummary = finished?.let { run ->
                        SyncSummary(
                            completed = run.uploaded,
                            skipped = run.skipped,
                            failed = run.failed
                        )
                    } ?: it.lastSyncSummary,
                    activeOrigin = null,
                    isCheckingHashes = false,
                    isSyncing = false,
                    hashProgress = null,
                    syncProgress = null,
                    externalItemProgress = emptyMap(),
                    lastRun = finished
                )
            }
            refreshFromLedger(force = true)
            return
        }
        _state.update { current ->
            when (activity.phase) {
                BackupPhase.Verifying -> current.copy(
                    activeOrigin = activity.origin,
                    isCheckingHashes = true,
                    isSyncing = false,
                    hashProgress = DeviceBackupRepository.VerificationProgress(
                        hashedCount = activity.hashedCount,
                        hashTotal = activity.hashTotal
                    ),
                    syncProgress = null,
                    externalItemProgress = emptyMap()
                )
                BackupPhase.Uploading -> current.copy(
                    activeOrigin = activity.origin,
                    isCheckingHashes = false,
                    isSyncing = true,
                    hashProgress = null,
                    syncProgress = SyncProgress(
                        total = activity.total,
                        completed = activity.completed,
                        skipped = activity.skipped,
                        failed = activity.failed,
                        currentName = activity.currentName,
                        inFlight = activity.inFlight,
                        bytesDone = activity.bytesDone,
                        bytesTotal = activity.bytesTotal
                    ),
                    externalItemProgress = activity.inFlightItems
                )
            }
        }
    }

    /**
     * Re-applies the ledger's verdicts over the grid. Unlike
     * [seedSyncStatesFromLedger] this also upgrades entries that already had a
     * verdict — which is the point when a worker pass is uploading underneath
     * us — so it's throttled to keep a 6-wide fan-out from querying per file.
     */
    private fun refreshFromLedger(force: Boolean = false) {
        val folders = _state.value.folders
        if (folders.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastLedgerRefreshMillis < LEDGER_REFRESH_INTERVAL_MILLIS) return
        lastLedgerRefreshMillis = now
        viewModelScope.launch {
            val states = withContext(Dispatchers.Default) { repository.syncStatesFor(folders) }
            applySyncStates(states)
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
            val entries = cached.flatMap { (folder, media) ->
                media.map { DeviceBackupEntry(media = it, folderUri = folder.uri) }
            }
            _state.update { current ->
                if (current.entries.isNotEmpty()) current
                else current.copy(entries = entries)
            }
        }
    }

    fun setBackupEnabled(enabled: Boolean) {
        repository.setBackupEnabled(enabled)
        _state.update { it.copy(isBackupEnabled = enabled) }
        if (enabled) ensureLoaded()
        // The master switch drives the OS schedule too: turning backup on arms
        // the background sync (auto-backup defaults to on) and kicks a first
        // pass; turning it off cancels the scheduled work WITHOUT erasing the
        // user's explicit auto-backup choice — prefs.enabled already folds the
        // master switch in.
        val prefs = repository.backgroundSyncPreferences()
        _state.update { it.copy(backgroundSync = prefs) }
        backgroundScheduler.apply(prefs)
        if (enabled && prefs.enabled) {
            backgroundScheduler.requestImmediateSync(prefs)
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
        val loaded = _state.value.folders
        if (loaded.isNotEmpty()) {
            refreshFolderContents(loaded)
            return
        }
        val saved = repository.savedFolders()
        if (saved.isEmpty()) return
        viewModelScope.launch {
            // Each saved folder is re-resolved from its persisted bookmark;
            // one gone stale shouldn't take the others down with it.
            val resumed = saved.mapNotNull { folder ->
                val restored = runCatching { repository.restoreFolder(folder.uri) }.getOrNull()
                if (restored == null) repository.forgetFolder(folder.uri)
                restored
            }
            if (resumed.isEmpty()) return@launch
            applyFolders(resumed)
        }
    }

    /**
     * Re-list a folder we've already loaded once, merging new entries
     * in and keeping the [DeviceBackupEntry.syncState] of anything we'd
     * already checked. Runs without toggling `isLoading` so the
     * existing grid stays visible during the re-scan — only the
     * initial load (empty entries) shows a spinner.
     */
    private fun refreshFolderContents(folders: List<DeviceFolderRef>) {
        val showSpinner = _state.value.entries.isEmpty()
        if (showSpinner) {
            _state.update { it.copy(isLoading = true, error = null) }
        } else {
            _state.update { it.copy(error = null) }
        }
        viewModelScope.launch {
            val result = runCatching {
                folders.associateWith { repository.listMedia(it) }
            }
            result
                .onSuccess { scans ->
                    _state.update { current ->
                        val previous = current.entries.associateBy { it.media.uri }
                        current.copy(
                            isLoading = false,
                            entries = scans.flatMap { (folder, items) ->
                                items.map { media ->
                                    val existing = previous[media.uri]
                                    if (existing != null) {
                                        existing.copy(
                                            media = media.copy(sha256 = existing.media.sha256),
                                            folderUri = folder.uri
                                        )
                                    } else {
                                        DeviceBackupEntry(media = media, folderUri = folder.uri)
                                    }
                                }
                            }
                        )
                    }
                    // Verdicts already proven in a previous session appear
                    // instantly — no hashing, no network.
                    seedSyncStatesFromLedger(folders)
                    // …then reconcile against the server. Cheap now: only
                    // new/changed files hash, the rest is 1-2 bulk calls.
                    maybeAutoVerify()
                    // Persist the fresh scan so the next launch can seed the
                    // timeline instantly from cache.
                    withContext(Dispatchers.Default) {
                        scans.forEach { (folder, items) ->
                            repository.saveCachedMedia(folder.uri, items)
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, getString(Res.string.backup_error_refresh_folder))
                        )
                    }
                }
        }
    }

    fun onFolderPicked(folder: DeviceFolderRef?) {
        if (folder == null) return
        repository.rememberFolder(folder)
        applyFolders(repository.savedFolders())
    }

    /** Stops backing up one folder and drops its files from the grid. */
    fun removeFolder(folderUri: String) {
        repository.forgetFolder(folderUri)
        _state.update { current ->
            current.copy(
                folders = current.folders.filterNot { it.uri == folderUri },
                entries = current.entries.filterNot { it.folderUri == folderUri }
            )
        }
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
        val folders = _state.value.folders
        if (folders.isEmpty()) return
        if (_state.value.isCheckingHashes) return
        ownPassActive = true
        _state.update { it.copy(isCheckingHashes = true, error = null, hashProgress = null) }
        viewModelScope.launch {
            val byFolder = _state.value.entries.groupBy({ it.folderUri }, { it.media })
            val result = runCatching {
                // Folders verify in sequence; the progress bar accumulates so it
                // reads as one job rather than restarting per folder.
                var hashedSoFar = 0
                val merged = mutableMapOf<String, DeviceMediaSyncState>()
                for (folder in folders) {
                    if (!_state.value.isCheckingHashes) break
                    val alreadyHashed = hashedSoFar
                    merged += repository.verifyAgainstServer(
                        folder = folder,
                        scanned = byFolder[folder.uri].orEmpty(),
                        onProgress = { progress ->
                            _state.update {
                                it.copy(
                                    hashProgress = DeviceBackupRepository.VerificationProgress(
                                        hashedCount = alreadyHashed + progress.hashedCount,
                                        hashTotal = alreadyHashed + progress.hashTotal
                                    )
                                )
                            }
                        },
                        shouldContinue = { _state.value.isCheckingHashes }
                    )
                    hashedSoFar = _state.value.hashProgress?.hashedCount ?: alreadyHashed
                }
                merged.toMap()
            }
            result
                .onSuccess { states -> applySyncStates(states) }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, getString(Res.string.backup_error_verify)))
                    }
                }
            _state.update { it.copy(isCheckingHashes = false, hashProgress = null) }
            ownPassActive = false
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
    private fun seedSyncStatesFromLedger(folders: List<DeviceFolderRef>) {
        viewModelScope.launch {
            val states = withContext(Dispatchers.Default) { repository.syncStatesFor(folders) }
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
        if (_state.value.isSyncing || _state.value.isCheckingHashes) return
        if (startWorkerPass(uris = null)) return
        selectAllNotSynced()
        syncSelected()
    }

    /** Re-uploads exactly one entry — the failure dialog's retry action. */
    fun retrySingle(uri: String) {
        if (_state.value.isSyncing) return
        if (startWorkerPass(uris = listOf(uri))) return
        _state.update { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    entry.copy(isSelected = entry.media.uri == uri)
                }
            )
        }
        syncSelected()
    }

    /**
     * Hands a pass to the OS foreground worker, which keeps uploading with the
     * app closed. [uris] null means the whole folder; otherwise exactly those
     * files (the worker then skips the folder-wide verification).
     *
     * Returns false where no such primitive exists (iOS, desktop), so the
     * caller falls back to the in-process path. Every explicit upload action
     * goes through here, so "sync selected" and "upload now" now carry the same
     * guarantee instead of one dying the moment the user leaves the app.
     */
    private fun startWorkerPass(uris: List<String>?): Boolean {
        if (!backgroundScheduler.supportsForegroundBackup) return false
        val selectionKey = uris?.let { repository.stashSelection(it) }
        if (!backgroundScheduler.requestForegroundBackup(selectionKey)) return false

        // Show it as started right away: the worker takes a moment to spin up,
        // and a tap that changes nothing on screen reads as a dead button.
        _state.update { current ->
            current.copy(
                statusMessage = null,
                error = null,
                activeOrigin = BackupOrigin.Foreground,
                // A targeted pass goes straight to uploading; a full one starts
                // by verifying what's actually missing.
                isCheckingHashes = uris == null,
                isSyncing = uris != null,
                hashProgress = null,
                syncProgress = uris?.let { selection ->
                    val bytes = current.entries
                        .filter { it.media.uri in selection }
                        .sumOf { it.media.sizeBytes }
                    SyncProgress(
                        total = selection.size,
                        completed = 0,
                        skipped = 0,
                        failed = 0,
                        bytesTotal = bytes
                    )
                },
                entries = current.entries.map { if (it.isSelected) it.copy(isSelected = false) else it }
            )
        }
        // …but don't leave the UI hanging on a worker that never starts (gate
        // closed, WorkManager throttling): drop the optimistic state if nothing
        // has reported in by then.
        viewModelScope.launch {
            delay(WORKER_STARTUP_GRACE_MILLIS)
            if (progressBus.activity.value == null) applyExternalActivity(null)
        }
        return true
    }

    /**
     * The single "stop" the UI calls, whichever pass is running: an OS worker
     * gets cancelled through the scheduler, an in-process one through the
     * cooperative flags. Both are cooperative — files already in flight finish.
     */
    fun stopCurrentPass() {
        if (!_state.value.canStopCurrentPass) return
        if (_state.value.activeOrigin != null) {
            backgroundScheduler.cancelForegroundBackup()
            return
        }
        cancelSync()
        stopHashCheck()
    }

    /** Skips one file: it stops counting as pending and is never re-queued
     *  until [unignore]. The failure dialog's "Omitir" action. */
    fun ignoreSingle(uri: String) {
        val entry = _state.value.entries.firstOrNull { it.media.uri == uri } ?: return
        repository.markIgnored(entry.folderUri, uri)
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
        _state.value.folders.forEach { repository.ignoreFailed(it.uri) }
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
        val entry = _state.value.entries.firstOrNull { it.media.uri == uri } ?: return
        repository.unignore(entry.folderUri, uri)
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
        if (startWorkerPass(selected.map { it.media.uri })) return
        ownPassActive = true

        _state.update {
            it.copy(
                isSyncing = true,
                error = null,
                statusMessage = null,
                // Selection was only the "what to upload" queue — already
                // captured into `selected` above. Clear it now so the pending
                // screen leaves selection mode the instant the sync starts;
                // otherwise every in-flight item stays selected and taps just
                // toggle selection instead of opening preview/failure actions.
                entries = it.entries.map { e ->
                    if (e.isSelected) e.copy(isSelected = false) else e
                },
                syncProgress = SyncProgress(
                    total = selected.size,
                    completed = 0,
                    skipped = 0,
                    failed = 0,
                    bytesTotal = selected.sumOf { e -> e.media.sizeBytes }
                )
            )
        }

        // iOS/desktop only (Android delegated above): ask the OS for a grace
        // period so switching apps mid-batch doesn't freeze the uploads.
        viewModelScope.launch { withBackgroundExecution { runSelectedUploads(selected) } }
    }

    private suspend fun runSelectedUploads(selected: List<DeviceBackupEntry>) {
        var completed = 0
        var skipped = 0
        val failureReasonCounts = mutableMapOf<UploadFailureReason, Int>()
        var failed = 0
        var inFlight = 0
        var bytesDone = 0L

        // Marks an entry Synced and refreshes the progress snapshot. Shared
        // by the Uploaded and Skipped (server-side dedup) outcomes, which
        // differ only in which counter they bump.
        fun markSynced(media: DeviceMedia, assetId: String) {
            folderUriOf(media.uri)?.let { folderUri ->
                repository.markUploaded(folderUri, media.uri, assetId)
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
                        inFlight = inFlight,
                        bytesDone = bytesDone
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
                bytesDone += media.sizeBytes
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
                        folderUriOf(media.uri)?.let { folderUri ->
                            repository.markUploadFailed(
                                folderUri, media.uri, outcome.reason, outcome.detail
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
                                    inFlight = inFlight,
                                    bytesDone = bytesDone
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
        ownPassActive = false
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
            var freedBytes = 0L
            for (entry in targets) {
                if (!_state.value.isFreeingSpace) break
                val ok = runCatching {
                    repository.deleteLocal(entry.media)
                }.getOrDefault(false)
                if (ok) deleted++ else failed++
                if (ok) {
                    freedBytes += entry.media.sizeBytes
                    _state.update { current ->
                        current.copy(
                            entries = current.entries.filterNot {
                                it.media.uri == entry.media.uri
                            }
                        )
                    }
                }
            }
            val freed = humanBytes(freedBytes)
            val message = if (failed == 0) {
                getString(Res.string.device_backup_free_space_done, freed, deleted)
            } else {
                getString(Res.string.device_backup_free_space_partial, freed, failed)
            }
            _state.update { it.copy(isFreeingSpace = false, statusMessage = message) }
        }
    }

    /** Which backed-up folder a file belongs to, for the ledger writes. */
    private fun folderUriOf(uri: String): String? =
        _state.value.entries.firstOrNull { it.media.uri == uri }?.folderUri

    private fun applyFolders(folders: List<DeviceFolderRef>) {
        _state.update { it.copy(folders = folders, isLoading = true, error = null) }
        viewModelScope.launch {
            val media = runCatching { folders.associateWith { repository.listMedia(it) } }
            media
                .onSuccess { scans ->
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            entries = scans.flatMap { (folder, items) ->
                                items.map { DeviceBackupEntry(media = it, folderUri = folder.uri) }
                            }
                        )
                    }
                    seedSyncStatesFromLedger(folders)
                    maybeAutoVerify()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, getString(Res.string.backup_error_list_folder))
                        )
                    }
                }
        }
    }

    fun thumbnailModel(media: DeviceMedia): String = repository.thumbnailModel(media)

    private companion object {
        /** Minimum gap between ledger re-reads while a worker pass uploads. */
        const val LEDGER_REFRESH_INTERVAL_MILLIS = 500L

        /** How long we keep the optimistic "starting…" state before assuming
         *  the worker never got off the ground. */
        const val WORKER_STARTUP_GRACE_MILLIS = 5_000L
    }

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
