package com.photonne.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.account.AccountRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountSecurityUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isSubmitting: Boolean = false,
    val error: UiError? = null,
    val successMessage: String? = null,
) {
    val mismatch: Boolean
        get() = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() &&
            newPassword != confirmPassword

    /** The server-side rule is "at least 8 chars"; mirror it client-side
     *  so the user gets immediate feedback before submitting. */
    val newPasswordTooShort: Boolean
        get() = newPassword.isNotEmpty() && newPassword.length < MIN_LENGTH

    val canSave: Boolean
        get() = !isSubmitting && currentPassword.isNotEmpty() &&
            newPassword.length >= MIN_LENGTH && newPassword == confirmPassword

    companion object {
        const val MIN_LENGTH = 8
    }
}

class AccountSecurityViewModel(
    private val repository: AccountRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountSecurityUiState())
    val state: StateFlow<AccountSecurityUiState> = _state.asStateFlow()

    fun onCurrentChange(value: String) {
        _state.update { it.copy(currentPassword = value, successMessage = null) }
    }

    fun onNewChange(value: String) {
        _state.update { it.copy(newPassword = value, successMessage = null) }
    }

    fun onConfirmChange(value: String) {
        _state.update { it.copy(confirmPassword = value, successMessage = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSubmitting = true, error = null, successMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.changePassword(current.currentPassword, current.newPassword)
            }
                .onSuccess {
                    _state.value = AccountSecurityUiState(successMessage = "Password changed")
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = errorFactory.from(error, "Could not change password")
                        )
                    }
                }
        }
    }
}
