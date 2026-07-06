package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.UserDto
import com.photonne.app.ui.util.sortedByNatural
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val users: List<UserDto> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
    val statusMessage: String? = null,
    val isCurrentUserPrimaryAdmin: Boolean = false,
)

class AdminUsersViewModel(
    private val repository: AdminRepository,
    private val accountRepository: AccountRepository,
    private val authStateHolder: AuthStateHolder,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUsersUiState())
    val state: StateFlow<AdminUsersUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.users.isNotEmpty() || _state.value.isLoading) return
        refresh()
    }

    fun refresh() {
        if (_state.value.isLoading) return
        // Seed from the cached user first so the UI never flashes the wrong
        // state, then refresh /api/users/me below to repair any stale
        // `isPrimaryAdmin` flag (sessions that started before the server fix
        // had this field missing from the cached UserDto).
        val cachedPrimary =
            (authStateHolder.state.value as? AuthState.Authenticated)?.user?.isPrimaryAdmin == true
        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                isCurrentUserPrimaryAdmin = cachedPrimary
            )
        }
        viewModelScope.launch {
            // Fire both calls and apply each result independently so a transient
            // failure in one doesn't abort the other.
            launch {
                runCatching { accountRepository.refreshCurrentUser() }
                    .onSuccess { freshUser ->
                        _state.update {
                            it.copy(isCurrentUserPrimaryAdmin = freshUser.isPrimaryAdmin)
                        }
                    }
            }
            runCatching { repository.listUsers() }
                .onSuccess { users ->
                    _state.update {
                        it.copy(users = users.sortedByNatural { u -> u.username }, isLoading = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Could not load users")
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, statusMessage = null) }
    }

    fun create(
        username: String,
        email: String,
        password: String,
        firstName: String?,
        lastName: String?,
        role: String,
        isActive: Boolean,
        storageQuotaBytes: Long?,
        onDone: () -> Unit
    ) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.createUser(
                    username = username.trim(),
                    email = email.trim(),
                    password = password,
                    firstName = firstName?.trim(),
                    lastName = lastName?.trim(),
                    role = role,
                    isActive = isActive,
                    storageQuotaBytes = storageQuotaBytes
                )
            }
                .onSuccess { created ->
                    _state.update { current ->
                        current.copy(
                            users = (current.users + created).sortedByNatural { it.username },
                            isMutating = false,
                            statusMessage = "User \"${created.username}\" created"
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error, "Could not create user") }
        }
    }

    fun update(
        userId: String,
        username: String,
        email: String,
        firstName: String?,
        lastName: String?,
        role: String,
        isActive: Boolean,
        storageQuotaBytes: Long?,
        onDone: () -> Unit
    ) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.updateUser(
                    id = userId,
                    username = username.trim(),
                    email = email.trim(),
                    firstName = firstName?.trim(),
                    lastName = lastName?.trim(),
                    role = role,
                    isActive = isActive,
                    storageQuotaBytes = storageQuotaBytes
                )
            }
                .onSuccess { updated ->
                    _state.update { current ->
                        val replaced = current.users
                            .map { if (it.id == updated.id) updated else it }
                            .sortedByNatural { it.username }
                        current.copy(
                            users = replaced,
                            isMutating = false,
                            statusMessage = "User \"${updated.username}\" updated"
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error, "Could not update user") }
        }
    }

    fun delete(userId: String, onDone: () -> Unit) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching { repository.deleteUser(userId) }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            users = current.users.filterNot { it.id == userId },
                            isMutating = false,
                            statusMessage = "User deleted"
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error, "Could not delete user") }
        }
    }

    fun resetPassword(userId: String, newPassword: String, onDone: () -> Unit) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching { repository.resetUserPassword(userId, newPassword) }
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            statusMessage = msg.ifBlank { "Password reset" }
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error, "Could not reset password") }
        }
    }

    /**
     * Transfers the primary-admin flag from the current logged-in user to
     * [targetUserId]. On success, flips the flags in the cached user list and
     * updates [authStateHolder] so the rest of the app immediately reflects
     * that the current user is no longer primary (loses delete/demote
     * protection in the UI).
     */
    fun promoteToPrimary(targetUserId: String, successMessage: String, onDone: () -> Unit) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching { repository.promoteToPrimary(targetUserId) }
                .onSuccess {
                    val currentUser =
                        (authStateHolder.state.value as? AuthState.Authenticated)?.user
                    if (currentUser != null) {
                        authStateHolder.update(
                            AuthState.Authenticated(currentUser.copy(isPrimaryAdmin = false))
                        )
                    }
                    _state.update { current ->
                        val updatedUsers = current.users.map { u ->
                            when (u.id) {
                                currentUser?.id -> u.copy(isPrimaryAdmin = false)
                                targetUserId -> u.copy(isPrimaryAdmin = true)
                                else -> u
                            }
                        }
                        current.copy(
                            users = updatedUsers,
                            isMutating = false,
                            statusMessage = successMessage,
                            isCurrentUserPrimaryAdmin = false
                        )
                    }
                    onDone()
                }
                .onFailure { error ->
                    failMutation(error, "Could not transfer primary admin")
                }
        }
    }

    private fun failMutation(throwable: Throwable, fallback: String) {
        _state.update {
            it.copy(isMutating = false, error = errorFactory.from(throwable, fallback))
        }
    }
}
