package com.photonne.app.ui.admin

import com.photonne.app.resources.admin_libraries_error_load
import com.photonne.app.resources.admin_libraries_created_format
import com.photonne.app.resources.admin_libraries_error_create
import com.photonne.app.resources.admin_libraries_updated_format
import com.photonne.app.resources.admin_libraries_error_update
import com.photonne.app.resources.admin_libraries_deleted
import com.photonne.app.resources.admin_libraries_error_delete
import com.photonne.app.resources.admin_libraries_error_scan
import com.photonne.app.resources.admin_libraries_error_scan_cancel
import com.photonne.app.resources.admin_libraries_error_permissions_load
import com.photonne.app.resources.admin_libraries_error_grant
import com.photonne.app.resources.admin_libraries_error_revoke
import com.photonne.app.ui.util.getString
import com.photonne.app.resources.Res
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.ui.util.sortedByNatural
import com.photonne.app.data.models.LibraryPermissionDto
import com.photonne.app.data.models.LibraryScanProgress
import com.photonne.app.data.models.UserDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminLibrariesUiState(
    val libraries: List<ExternalLibraryDto> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
    val statusMessage: String? = null,
    val scanProgress: LibraryScanProgress? = null,
    val scanningLibraryId: String? = null,
    /** A cancel is on its way to the server: the card's X waits for it. */
    val isCancellingScan: Boolean = false,
    val permissions: List<LibraryPermissionDto> = emptyList(),
    val permissionsLibraryId: String? = null,
    /** The sheet's own load and error: it covers the list, so anything put in
     *  [error] while it is open is drawn behind it and never read. */
    val permissionsLoading: Boolean = false,
    val permissionsError: UiError? = null,
    /** Users with a grant or revoke in flight, so a second tap can't fire it twice. */
    val permissionsBusy: Set<String> = emptySet(),
    val candidateUsers: List<UserDto> = emptyList()
)

class AdminLibrariesViewModel(
    private val repository: AdminRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminLibrariesUiState())
    val state: StateFlow<AdminLibrariesUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var scanTaskId: CompletableDeferred<String>? = null

    /** For the editor, which only needs the list to exist. The list screen
     *  calls [refresh] on every entry. */
    fun ensureLoaded() {
        if (_state.value.libraries.isNotEmpty() || _state.value.isLoading) return
        refresh()
    }

    /** The result was shown (snackbar): don't replay it on the next screen. */
    fun consumeStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.listLibraries() }
                .onSuccess { libs ->
                    _state.update { it.copy(libraries = libs, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, getString(Res.string.admin_libraries_error_load))
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, statusMessage = null) }
    }

    fun create(
        name: String,
        path: String,
        importSubfolders: Boolean,
        cronSchedule: String?,
        onDone: () -> Unit
    ) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.createLibrary(name.trim(), path.trim(), importSubfolders, cronSchedule?.trim())
            }
                .onSuccess { created ->
                    _state.update { current ->
                        current.copy(
                            libraries = current.libraries + created,
                            isMutating = false,
                            statusMessage = getString(Res.string.admin_libraries_created_format, created.name)
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error, getString(Res.string.admin_libraries_error_create)) }
        }
    }

    fun update(
        id: String,
        name: String,
        path: String,
        importSubfolders: Boolean,
        cronSchedule: String?,
        onDone: () -> Unit
    ) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.updateLibrary(id, name.trim(), path.trim(), importSubfolders, cronSchedule?.trim())
            }
                .onSuccess { updated ->
                    _state.update { current ->
                        current.copy(
                            libraries = current.libraries.map {
                                if (it.id == updated.id) updated else it
                            },
                            isMutating = false,
                            statusMessage = getString(Res.string.admin_libraries_updated_format, updated.name)
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error, getString(Res.string.admin_libraries_error_update)) }
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching { repository.deleteLibrary(id) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            libraries = current.libraries.filterNot { it.id == id },
                            isMutating = false,
                            statusMessage = getString(Res.string.admin_libraries_deleted)
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error, getString(Res.string.admin_libraries_error_delete)) }
        }
    }

    /**
     * Picks up a scan that is already running on the server — started from the
     * web, by the nightly scheduler, or by this app before it was closed — so
     * its card (and its cancel button) comes back instead of the list looking
     * idle while the server works. Called on every entry to the screen.
     */
    fun attachToRunningScan() {
        if (_state.value.scanningLibraryId != null) return
        viewModelScope.launch {
            val running = repository.findRunningTaskOfType(LIBRARY_SCAN_TASK) ?: return@launch
            val libraryId = running.parameters[LIBRARY_ID_PARAM] ?: return@launch
            if (_state.value.scanningLibraryId != null) return@launch
            beginScan(libraryId, knownTaskId = running.id, message = running.lastMessage) {
                repository.resumeLibraryScanTaskStream(running.id)
            }
        }
    }

    fun startScan(id: String) {
        if (_state.value.scanningLibraryId != null) return
        beginScan(id, knownTaskId = null, message = "") {
            // The server doesn't refuse a second scan of the same library: it
            // would run both, side by side, over the same files. When one is
            // already going (this screen hadn't caught up with it yet), follow
            // that one instead of starting another.
            val running = repository.findRunningTaskOfType(LIBRARY_SCAN_TASK)
                ?.takeIf { it.parameters[LIBRARY_ID_PARAM] == id }
            if (running != null) repository.resumeLibraryScanTaskStream(running.id)
            else repository.scanLibrary(id)
        }
    }

    private fun beginScan(
        libraryId: String,
        knownTaskId: String?,
        message: String,
        source: suspend () -> Flow<LibraryScanProgress>
    ) {
        val taskId = CompletableDeferred<String>()
        knownTaskId?.let(taskId::complete)
        scanTaskId = taskId
        _state.update {
            it.copy(
                scanningLibraryId = libraryId,
                // Blank until the server says something: the card fills in "Iniciando…".
                scanProgress = LibraryScanProgress(message = message),
                isCancellingScan = false,
                error = null,
                statusMessage = null
            )
        }
        scanJob = viewModelScope.launch {
            source()
                .catch { throwable ->
                    _state.update {
                        it.copy(
                            scanningLibraryId = null,
                            scanProgress = null,
                            isCancellingScan = false,
                            error = errorFactory.from(throwable, getString(Res.string.admin_libraries_error_scan))
                        )
                    }
                }
                .collect { progress ->
                    // The server's task id: what the cancel button needs.
                    progress.taskId?.let(taskId::complete)
                    _state.update { it.copy(scanProgress = progress) }
                    if (progress.isCompleted) {
                        refresh()
                    }
                }
            // A finished scan takes its card with it. It used to stay at 100 %
            // until someone pressed its X; the last message goes to the
            // snackbar instead, so the outcome is still said once.
            _state.update {
                if (it.scanningLibraryId == null) it
                else it.copy(
                    scanningLibraryId = null,
                    scanProgress = null,
                    isCancellingScan = false,
                    statusMessage = it.scanProgress?.message?.takeIf { m -> m.isNotBlank() }
                )
            }
        }
    }

    /**
     * Stops the scan on the server. Dropping the stream alone only stopped
     * listening: the scan runs on its own token server-side, so it went on
     * indexing while the card said it was gone. The task id comes with the
     * first progress line; a tap before that waits for it.
     */
    fun cancelScan() {
        val job = scanJob ?: return
        if (_state.value.isCancellingScan) return
        val pendingTaskId = scanTaskId
        _state.update { it.copy(isCancellingScan = true) }
        viewModelScope.launch {
            // Either the id arrives or the stream ends first (failed, finished):
            // in the second case there is nothing left to cancel.
            val taskId = select<String?> {
                if (pendingTaskId != null) pendingTaskId.onAwait { it }
                job.onJoin { null }
            }
            if (taskId != null) {
                val failure = runCatching { repository.cancelBackgroundTask(taskId) }.exceptionOrNull()
                if (failure != null) {
                    // Still running: keep following it so the card stays honest.
                    _state.update {
                        it.copy(
                            isCancellingScan = false,
                            error = errorFactory.from(failure, getString(Res.string.admin_libraries_error_scan_cancel))
                        )
                    }
                    return@launch
                }
            }
            job.cancel()
            if (scanJob === job) {
                scanJob = null
                scanTaskId = null
            }
            _state.update { it.copy(scanningLibraryId = null, scanProgress = null, isCancellingScan = false) }
            // The library's status and counts changed under the cancelled scan.
            refresh()
        }
    }

    fun openPermissions(libraryId: String, allUsers: List<UserDto>) {
        _state.update {
            it.copy(
                permissionsLibraryId = libraryId,
                permissions = emptyList(),
                candidateUsers = allUsers,
                permissionsLoading = true,
                permissionsError = null,
                permissionsBusy = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching { repository.listLibraryPermissions(libraryId) }
                .onSuccess { perms ->
                    _state.update { it.copy(permissions = perms, permissionsLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            permissionsLoading = false,
                            permissionsError = errorFactory.from(error, getString(Res.string.admin_libraries_error_permissions_load))
                        )
                    }
                }
        }
    }

    fun closePermissions() {
        _state.update {
            it.copy(
                permissionsLibraryId = null,
                permissions = emptyList(),
                candidateUsers = emptyList(),
                permissionsLoading = false,
                permissionsError = null,
                permissionsBusy = emptySet()
            )
        }
    }

    fun grantPermission(userId: String) {
        val libraryId = _state.value.permissionsLibraryId ?: return
        if (!markPermissionBusy(userId)) return
        viewModelScope.launch {
            runCatching {
                repository.setLibraryPermission(libraryId, userId, canRead = true)
            }
                .onSuccess { perm ->
                    _state.update { current ->
                        if (current.permissionsLibraryId != libraryId) return@update current
                        val list = current.permissions.filterNot { it.userId == perm.userId } + perm
                        current.copy(
                            permissions = list.sortedByNatural { it.username },
                            permissionsBusy = current.permissionsBusy - userId
                        )
                    }
                }
                .onFailure { error ->
                    failPermission(libraryId, userId, errorFactory.from(error, getString(Res.string.admin_libraries_error_grant)))
                }
        }
    }

    fun revokePermission(userId: String) {
        val libraryId = _state.value.permissionsLibraryId ?: return
        if (!markPermissionBusy(userId)) return
        viewModelScope.launch {
            runCatching { repository.removeLibraryPermission(libraryId, userId) }
                .onSuccess {
                    _state.update { current ->
                        if (current.permissionsLibraryId != libraryId) return@update current
                        current.copy(
                            permissions = current.permissions.filterNot { it.userId == userId },
                            permissionsBusy = current.permissionsBusy - userId
                        )
                    }
                }
                .onFailure { error ->
                    failPermission(libraryId, userId, errorFactory.from(error, getString(Res.string.admin_libraries_error_revoke)))
                }
        }
    }

    /**
     * A grant or revoke answers after the sheet may have moved on. Its result
     * belongs to [libraryId] only: applied to whatever the sheet shows now it
     * listed a user under the wrong library. A failure is still news, though —
     * in the sheet when it is still that library's, otherwise in the snackbar,
     * where it used to be dropped with the closed sheet.
     */
    private fun failPermission(libraryId: String, userId: String, error: UiError) {
        _state.update {
            if (it.permissionsLibraryId == libraryId) {
                it.copy(permissionsBusy = it.permissionsBusy - userId, permissionsError = error)
            } else {
                it.copy(statusMessage = error.userMessage)
            }
        }
    }

    /** False when [userId] already has a request in flight. */
    private fun markPermissionBusy(userId: String): Boolean {
        if (userId in _state.value.permissionsBusy) return false
        _state.update { it.copy(permissionsBusy = it.permissionsBusy + userId, permissionsError = null) }
        return true
    }

    private companion object {
        /** The server's BackgroundTaskType name and the parameter it files the library under. */
        const val LIBRARY_SCAN_TASK = "LibraryScan"
        const val LIBRARY_ID_PARAM = "libraryId"
    }

    private fun failMutation(throwable: Throwable, fallback: String) {
        _state.update {
            it.copy(isMutating = false, error = errorFactory.from(throwable, fallback))
        }
    }
}
