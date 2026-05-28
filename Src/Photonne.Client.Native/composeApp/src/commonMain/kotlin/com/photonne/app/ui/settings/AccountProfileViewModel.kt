package com.photonne.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val username: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: UiError? = null,
    val successMessage: String? = null,
    val baseline: UserDto? = null,
) {
    val canSave: Boolean
        get() {
            val current = baseline ?: return false
            if (isSubmitting) return false
            if (email.isBlank() || username.isBlank()) return false
            return firstName.trim() != current.firstName.orEmpty().trim() ||
                lastName.trim() != current.lastName.orEmpty().trim() ||
                email.trim() != current.email.trim() ||
                username.trim() != current.username.trim()
        }
}

class AccountProfileViewModel(
    private val repository: AccountRepository,
    private val authStateHolder: AuthStateHolder,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountProfileUiState())
    val state: StateFlow<AccountProfileUiState> = _state.asStateFlow()

    /**
     * Seeds the form from the cached user, then refreshes against
     * `/api/users/me` in the background so values stay in sync if the
     * user was renamed from another device.
     */
    fun load() {
        val cached = (authStateHolder.state.value as? AuthState.Authenticated)?.user
        if (cached != null) seed(cached)
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.refreshCurrentUser() }
                .onSuccess { user ->
                    seed(user)
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = if (cached == null) {
                                errorFactory.from(error, "Failed to load profile")
                            } else null
                        )
                    }
                }
        }
    }

    fun onFirstNameChange(value: String) {
        _state.update { it.copy(firstName = value, successMessage = null) }
    }

    fun onLastNameChange(value: String) {
        _state.update { it.copy(lastName = value, successMessage = null) }
    }

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, successMessage = null) }
    }

    fun onUsernameChange(value: String) {
        _state.update { it.copy(username = value, successMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSubmitting = true, error = null, successMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.updateProfile(
                    username = current.username.trim(),
                    email = current.email.trim(),
                    firstName = current.firstName.trim(),
                    lastName = current.lastName.trim()
                )
            }
                .onSuccess { user ->
                    seed(user)
                    _state.update {
                        it.copy(isSubmitting = false, successMessage = "Profile updated")
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = errorFactory.from(error, "Could not update profile")
                        )
                    }
                }
        }
    }

    private fun seed(user: UserDto) {
        _state.update {
            it.copy(
                firstName = user.firstName.orEmpty(),
                lastName = user.lastName.orEmpty(),
                email = user.email,
                username = user.username,
                baseline = user
            )
        }
    }
}
