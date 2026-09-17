package com.photonne.app.ui.admin

import com.photonne.app.resources.admin_libraries_error_load
import com.photonne.app.resources.admin_libraries_created_format
import com.photonne.app.resources.admin_libraries_error_create
import com.photonne.app.resources.admin_libraries_updated_format
import com.photonne.app.resources.admin_libraries_error_update
import com.photonne.app.resources.admin_libraries_deleted
import com.photonne.app.resources.admin_libraries_error_delete
import com.photonne.app.resources.admin_libraries_error_scan
import com.photonne.app.resources.admin_libraries_error_permissions_load
import com.photonne.app.resources.admin_libraries_error_grant
import com.photonne.app.resources.admin_libraries_error_revoke
import org.jetbrains.compose.resources.getString
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
import kotlinx.coroutines.Job
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

    fun startScan(id: String) {
        if (_state.value.scanningLibraryId != null) return
        _state.update {
            it.copy(
                scanningLibraryId = id,
                // Blank until the server says something: the card fills in "Iniciando…".
                scanProgress = LibraryScanProgress(message = ""),
                error = null,
                statusMessage = null
            )
        }
        scanJob = viewModelScope.launch {
            repository.scanLibrary(id)
                .catch { throwable ->
                    _state.update {
                        it.copy(
                            scanningLibraryId = null,
                            scanProgress = null,
                            error = errorFactory.from(throwable, getString(Res.string.admin_libraries_error_scan))
                        )
                    }
                }
                .collect { progress ->
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
                    statusMessage = it.scanProgress?.message?.takeIf { m -> m.isNotBlank() }
                )
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanningLibraryId = null, scanProgress = null) }
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
                        val list = current.permissions.filterNot { it.userId == perm.userId } + perm
                        current.copy(
                            permissions = list.sortedByNatural { it.username },
                            permissionsBusy = current.permissionsBusy - userId
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            permissionsBusy = it.permissionsBusy - userId,
                            permissionsError = errorFactory.from(error, getString(Res.string.admin_libraries_error_grant))
                        )
                    }
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
                        current.copy(
                            permissions = current.permissions.filterNot { it.userId == userId },
                            permissionsBusy = current.permissionsBusy - userId
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            permissionsBusy = it.permissionsBusy - userId,
                            permissionsError = errorFactory.from(error, getString(Res.string.admin_libraries_error_revoke))
                        )
                    }
                }
        }
    }

    /** False when [userId] already has a request in flight. */
    private fun markPermissionBusy(userId: String): Boolean {
        if (userId in _state.value.permissionsBusy) return false
        _state.update { it.copy(permissionsBusy = it.permissionsBusy + userId, permissionsError = null) }
        return true
    }

    private fun failMutation(throwable: Throwable, fallback: String) {
        _state.update {
            it.copy(isMutating = false, error = errorFactory.from(throwable, fallback))
        }
    }
}
