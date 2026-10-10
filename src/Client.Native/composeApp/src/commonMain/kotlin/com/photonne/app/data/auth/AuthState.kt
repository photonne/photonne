package com.photonne.app.data.auth

import com.photonne.app.data.models.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    data object Unknown : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: UserDto) : AuthState

    /**
     * El servidor rechazó el refresh token: la sesión caducó sin que el
     * usuario la cerrase. Se comporta como [Unauthenticated] (pantalla de
     * login, sesión limpia) pero la pantalla avisa del motivo y prerrellena
     * [username]. Un logout voluntario va a [Unauthenticated], sin aviso.
     */
    data class SessionExpired(val username: String?) : AuthState
}

/** Sin sesión, por el motivo que sea: login voluntario o caducidad. */
val AuthState.isSignedOut: Boolean
    get() = this is AuthState.Unauthenticated || this is AuthState.SessionExpired

class AuthStateHolder {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun update(newState: AuthState) {
        _state.value = newState
    }

    /**
     * Cierre forzado por caducidad. Si ya no había sesión (un logout voluntario
     * con peticiones aún en vuelo que vuelven con 401), no se convierte en
     * "caducada": el usuario salió él mismo y no debe ver el aviso.
     */
    fun expireSession(username: String?): Boolean {
        val current = _state.value
        if (current.isSignedOut) return false
        val name = username ?: (current as? AuthState.Authenticated)?.user?.username
        _state.value = AuthState.SessionExpired(name)
        return true
    }
}
