package com.photonne.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.skipAuthRefresh
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.data.auth.RememberedCredentialsStore
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.di.PhotonneAppConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LoginStep { ServerUrl, Credentials }

data class LoginUiState(
    val step: LoginStep = LoginStep.ServerUrl,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val tokenStorage: TokenStorage,
    private val serverUrlStore: ServerUrlStore,
    private val rememberedCredentialsStore: RememberedCredentialsStore,
    private val httpClient: HttpClient,
    config: PhotonneAppConfig
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(config))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private fun initialState(config: PhotonneAppConfig): LoginUiState {
        val saved = serverUrlStore.get()
        val remembered = rememberedCredentialsStore.get()
        return if (saved != null) {
            LoginUiState(
                step = LoginStep.Credentials,
                serverUrl = saved,
                username = remembered?.username.orEmpty(),
                password = remembered?.password.orEmpty(),
                rememberMe = remembered != null
            )
        } else {
            LoginUiState(
                step = LoginStep.ServerUrl,
                serverUrl = config.apiBaseUrl.orEmpty(),
                rememberMe = rememberedCredentialsStore.isEnabled()
            )
        }
    }

    fun onServerUrlChange(value: String) {
        _state.value = _state.value.copy(serverUrl = value, errorMessage = null)
    }

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value, errorMessage = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, errorMessage = null)
    }

    fun onRememberMeChange(value: Boolean) {
        _state.value = _state.value.copy(rememberMe = value)
    }

    fun submitServerUrl() {
        val current = _state.value
        if (current.isSubmitting) return
        val normalized = ServerUrlStore.normalize(current.serverUrl)
        if (!isValidUrl(normalized)) {
            _state.value = current.copy(errorMessage = "Introduce una URL válida (ej. https://photos.example.com)")
            return
        }
        _state.value = current.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val reachable = probe(normalized)
            if (reachable.isFailure) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    errorMessage = reachable.exceptionOrNull()?.message
                        ?: "No se pudo contactar con el servidor"
                )
                return@launch
            }
            serverUrlStore.set(normalized)
            val remembered = rememberedCredentialsStore.get()
            _state.value = LoginUiState(
                step = LoginStep.Credentials,
                serverUrl = normalized,
                username = remembered?.username.orEmpty(),
                password = remembered?.password.orEmpty(),
                rememberMe = remembered != null
            )
        }
    }

    fun changeServer() {
        if (_state.value.isSubmitting) return
        tokenStorage.clear()
        val previous = serverUrlStore.get().orEmpty()
        _state.value = LoginUiState(
            step = LoginStep.ServerUrl,
            serverUrl = previous,
            rememberMe = rememberedCredentialsStore.isEnabled()
        )
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(errorMessage = "Introduce usuario y contraseña")
            return
        }
        if (serverUrlStore.get().isNullOrEmpty()) {
            _state.value = current.copy(
                step = LoginStep.ServerUrl,
                errorMessage = "Configura primero la URL del servidor"
            )
            return
        }
        _state.value = current.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val username = current.username.trim()
            val result = authRepository.login(username, current.password)
            _state.value = if (result.isSuccess) {
                if (current.rememberMe) {
                    rememberedCredentialsStore.save(username, current.password)
                } else {
                    rememberedCredentialsStore.clear()
                }
                LoginUiState(
                    step = LoginStep.Credentials,
                    serverUrl = serverUrlStore.get().orEmpty(),
                    username = if (current.rememberMe) username else "",
                    password = if (current.rememberMe) current.password else "",
                    rememberMe = current.rememberMe
                )
            } else {
                _state.value.copy(
                    isSubmitting = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
                )
            }
        }
    }

    private fun isValidUrl(url: String): Boolean = try {
        if (url.isBlank()) false
        else {
            val parsed = Url(url)
            parsed.host.isNotEmpty() && (parsed.protocol.name == "http" || parsed.protocol.name == "https")
        }
    } catch (_: Throwable) {
        false
    }

    private suspend fun probe(baseUrl: String): Result<Unit> = try {
        val response: HttpResponse = httpClient.head("$baseUrl/api/auth/login") {
            skipAuthRefresh()
        }
        if (response.status.value in 500..599) {
            Result.failure(
                RuntimeException("El servidor respondió con ${response.status.value}")
            )
        } else {
            Result.success(Unit)
        }
    } catch (t: Throwable) {
        Result.failure(
            RuntimeException(
                "No se pudo contactar con el servidor: ${t.message ?: t::class.simpleName}"
            )
        )
    }
}
