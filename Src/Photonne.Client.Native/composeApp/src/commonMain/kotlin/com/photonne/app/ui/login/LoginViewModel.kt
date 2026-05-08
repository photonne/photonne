package com.photonne.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, errorMessage = null)
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(errorMessage = "Introduce usuario y contraseña")
            return
        }
        _state.value = current.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = authRepository.login(current.username.trim(), current.password)
            _state.value = if (result.isSuccess) {
                LoginUiState()
            } else {
                _state.value.copy(
                    isSubmitting = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
                )
            }
        }
    }
}
