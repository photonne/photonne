package com.photonne.app.data.auth

import com.photonne.app.data.models.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    data object Unknown : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: UserDto) : AuthState
}

class AuthStateHolder {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun update(newState: AuthState) {
        _state.value = newState
    }
}
