package com.photonne.app.ui.devicesync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.devicesync.DeviceFolderRef
import com.photonne.app.data.devicesync.DeviceMedia
import com.photonne.app.data.devicesync.DeviceMediaSyncState
import com.photonne.app.data.devicesync.DeviceSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One entry in the gallery grid. `media` carries the raw metadata
 * coming back from the platform layer; `syncState` is the verdict
 * after we've hashed the file and asked the server, lazily, when
 * the user scrolls past it or explicitly requests a refresh.
 */
data class DeviceSyncEntry(
    val media: DeviceMedia,
    val syncState: DeviceMediaSyncState = DeviceMediaSyncState.Unknown,
    val isSelected: Boolean = false
)

data class DeviceSyncUiState(
    val isSupported: Boolean = true,
    val folder: DeviceFolderRef? = null,
    val entries: List<DeviceSyncEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isCheckingHashes: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null
) {
    val selectedCount: Int get() = entries.count { it.isSelected }
    val syncableSelectedCount: Int get() = entries.count {
        it.isSelected && it.syncState !is DeviceMediaSyncState.Synced
    }
}

/** Progress snapshot while the manual sync is uploading files. */
data class SyncProgress(
    val total: Int,
    val completed: Int,
    val skipped: Int,
    val failed: Int,
    val currentName: String? = null
)

class DeviceSyncViewModel(
    private val repository: DeviceSyncRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        DeviceSyncUiState(isSupported = repository.isSupported)
    )
    val state: StateFlow<DeviceSyncUiState> = _state.asStateFlow()

    /**
     * Called when the screen is first composed. Tries to resume the
     * previously-picked folder; if there isn't one (or the platform
     * dropped the grant) the screen shows the empty CTA.
     */
    fun ensureLoaded() {
        if (_state.value.folder != null || _state.value.isLoading) return
        if (!repository.isSupported) return
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

    fun onFolderPicked(folder: DeviceFolderRef?) {
        if (folder == null) return
        repository.rememberFolder(folder)
        applyFolder(folder)
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, statusMessage = null) }
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
                    if (entry.syncState is DeviceMediaSyncState.Synced) entry
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
     * Streams through every entry, hashing locally and asking the
     * server if a matching asset already exists. Runs serially to
     * keep memory pressure low; users with very large folders can
     * just sync without waiting for the full pass.
     */
    fun refreshSyncStates() {
        val folder = _state.value.folder ?: return
        if (_state.value.isCheckingHashes) return
        _state.update { it.copy(isCheckingHashes = true, errorMessage = null) }
        viewModelScope.launch {
            val snapshot = _state.value.entries
            for (entry in snapshot) {
                if (!_state.value.isCheckingHashes) break // user cancelled / left screen
                val (hash, state) = runCatching {
                    repository.checkSyncStatus(entry.media)
                }.getOrElse {
                    "" to DeviceMediaSyncState.Failed(it.message ?: "Hash check failed")
                }
                _state.update { current ->
                    current.copy(
                        entries = current.entries.map { e ->
                            if (e.media.uri == entry.media.uri) {
                                e.copy(
                                    media = e.media.copy(
                                        sha256 = hash.takeIf { it.isNotBlank() }
                                    ),
                                    syncState = state
                                )
                            } else e
                        }
                    )
                }
            }
            _state.update { it.copy(isCheckingHashes = false) }
        }
        _state.value.folder
    }

    fun stopHashCheck() {
        _state.update { it.copy(isCheckingHashes = false) }
    }

    /** Uploads every selected entry that isn't already synced. */
    fun syncSelected() {
        if (_state.value.isSyncing) return
        val selected = _state.value.entries.filter {
            it.isSelected && it.syncState !is DeviceMediaSyncState.Synced
        }
        if (selected.isEmpty()) return

        _state.update {
            it.copy(
                isSyncing = true,
                errorMessage = null,
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
            var failed = 0
            for (entry in selected) {
                if (!_state.value.isSyncing) break
                _state.update { current ->
                    current.copy(
                        entries = current.entries.map { e ->
                            if (e.media.uri == entry.media.uri) {
                                e.copy(syncState = DeviceMediaSyncState.Uploading)
                            } else e
                        },
                        syncProgress = current.syncProgress?.copy(
                            currentName = entry.media.displayName
                        )
                    )
                }
                val result = runCatching { repository.upload(entry.media) }
                result
                    .onSuccess { response ->
                        // Server message contains "already exists" when the
                        // upload short-circuited via the SHA-256 dedup
                        // path. Track those as skipped rather than counted
                        // as full uploads.
                        val msg = response.message
                        val isDup = msg.contains("already exists", ignoreCase = true)
                        if (isDup) skipped++ else completed++
                        val nextState: DeviceMediaSyncState =
                            DeviceMediaSyncState.Synced(response.assetId.orEmpty())
                        _state.update { current ->
                            current.copy(
                                entries = current.entries.map { e ->
                                    if (e.media.uri == entry.media.uri) {
                                        e.copy(
                                            syncState = nextState,
                                            isSelected = false
                                        )
                                    } else e
                                },
                                syncProgress = current.syncProgress?.copy(
                                    completed = completed,
                                    skipped = skipped
                                )
                            )
                        }
                    }
                    .onFailure { error ->
                        failed++
                        _state.update { current ->
                            current.copy(
                                entries = current.entries.map { e ->
                                    if (e.media.uri == entry.media.uri) {
                                        e.copy(
                                            syncState = DeviceMediaSyncState.Failed(
                                                error.message ?: "Upload failed"
                                            )
                                        )
                                    } else e
                                },
                                syncProgress = current.syncProgress?.copy(failed = failed)
                            )
                        }
                    }
            }
            _state.update {
                it.copy(
                    isSyncing = false,
                    statusMessage = "Subidos $completed · Omitidos $skipped · Fallidos $failed"
                )
            }
        }
    }

    fun cancelSync() {
        _state.update { it.copy(isSyncing = false) }
    }

    fun pickAnotherFolder() {
        _state.update {
            it.copy(folder = null, entries = emptyList(), syncProgress = null)
        }
    }

    private fun applyFolder(folder: DeviceFolderRef) {
        _state.update { it.copy(folder = folder, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val media = runCatching { repository.listMedia(folder) }
            media
                .onSuccess { items ->
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            entries = items.map { DeviceSyncEntry(media = it) }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not list folder"
                        )
                    }
                }
        }
    }

    fun thumbnailModel(media: DeviceMedia): String = repository.thumbnailModel(media)
}
