package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUsersUiState(
    val users: List<UserDto> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class AdminUsersViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUsersUiState())
    val state: StateFlow<AdminUsersUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.users.isNotEmpty() || _state.value.isLoading) return
        refresh()
    }

    fun refresh() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.listUsers() }
                .onSuccess { users ->
                    _state.update {
                        it.copy(users = users.sortedBy { u -> u.username }, isLoading = false)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load users"
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, statusMessage = null) }
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
        _state.update { it.copy(isMutating = true, errorMessage = null, statusMessage = null) }
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
                            users = (current.users + created).sortedBy { it.username },
                            isMutating = false,
                            statusMessage = "User \"${created.username}\" created"
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error.message ?: "Could not create user") }
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
        _state.update { it.copy(isMutating = true, errorMessage = null, statusMessage = null) }
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
                            .sortedBy { it.username }
                        current.copy(
                            users = replaced,
                            isMutating = false,
                            statusMessage = "User \"${updated.username}\" updated"
                        )
                    }
                    onDone()
                }
                .onFailure { error -> failMutation(error.message ?: "Could not update user") }
        }
    }

    fun delete(userId: String, onDone: () -> Unit) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null, statusMessage = null) }
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
                .onFailure { error -> failMutation(error.message ?: "Could not delete user") }
        }
    }

    fun resetPassword(userId: String, newPassword: String, onDone: () -> Unit) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null, statusMessage = null) }
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
                .onFailure { error -> failMutation(error.message ?: "Could not reset password") }
        }
    }

    private fun failMutation(message: String) {
        _state.update { it.copy(isMutating = false, errorMessage = message) }
    }
}
