package com.photonne.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.api.LocalReachabilityProbe
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.skipAuthRefresh
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.data.auth.RememberedCredentialsStore
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
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
    val localUrl: String = "",
    val username: String = "",
    val password: String = "",
    val rememberMe: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: UiError? = null,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val tokenStorage: TokenStorage,
    private val serverUrlStore: ServerUrlStore,
    private val rememberedCredentialsStore: RememberedCredentialsStore,
    private val httpClient: HttpClient,
    private val reachabilityProbe: LocalReachabilityProbe,
    config: PhotonneAppConfig,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState(config))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private fun initialState(config: PhotonneAppConfig): LoginUiState {
        val savedPublic = serverUrlStore.getPublic()
        val savedLocal = serverUrlStore.getLocal().orEmpty()
        val remembered = rememberedCredentialsStore.get()
        return if (savedPublic != null) {
            LoginUiState(
                step = LoginStep.Credentials,
                serverUrl = savedPublic,
                localUrl = savedLocal,
                username = remembered?.username.orEmpty(),
                password = remembered?.password.orEmpty(),
                rememberMe = remembered != null
            )
        } else {
            LoginUiState(
                step = LoginStep.ServerUrl,
                serverUrl = config.apiBaseUrl.orEmpty(),
                localUrl = savedLocal,
                rememberMe = rememberedCredentialsStore.isEnabled()
            )
        }
    }

    fun onServerUrlChange(value: String) {
        _state.value = _state.value.copy(serverUrl = value, error = null)
    }

    fun onLocalUrlChange(value: String) {
        _state.value = _state.value.copy(localUrl = value, error = null)
    }

    fun onUsernameChange(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun onRememberMeChange(value: Boolean) {
        _state.value = _state.value.copy(rememberMe = value)
    }

    fun submitServerUrl() {
        val current = _state.value
        if (current.isSubmitting) return
        val normalizedPublic = ServerUrlStore.normalize(current.serverUrl)
        if (!isValidUrl(normalizedPublic)) {
            _state.value = current.copy(
                error = UiError(userMessage = "Introduce una URL válida (ej. https://photos.example.com)")
            )
            return
        }
        val normalizedLocal = current.localUrl
            .takeIf { it.isNotBlank() }
            ?.let(ServerUrlStore::normalize)
        if (normalizedLocal != null && !isValidUrl(normalizedLocal)) {
            _state.value = current.copy(
                error = UiError(userMessage = "La URL local no es válida")
            )
            return
        }
        _state.value = current.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val reachable = probe(normalizedPublic)
            if (reachable.isFailure) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    error = reachable.exceptionOrNull()?.let {
                        errorFactory.from(it, "No se pudo contactar con el servidor")
                    }
                )
                return@launch
            }
            serverUrlStore.setPublic(normalizedPublic)
            serverUrlStore.setLocal(normalizedLocal)
            // Probe the LAN URL right away so the first request after login
            // already goes through the local address when applicable.
            reachabilityProbe.runProbe()
            val remembered = rememberedCredentialsStore.get()
            _state.value = LoginUiState(
                step = LoginStep.Credentials,
                serverUrl = normalizedPublic,
                localUrl = normalizedLocal.orEmpty(),
                username = remembered?.username.orEmpty(),
                password = remembered?.password.orEmpty(),
                rememberMe = remembered != null
            )
        }
    }

    fun changeServer() {
        if (_state.value.isSubmitting) return
        tokenStorage.clear()
        val previousPublic = serverUrlStore.getPublic().orEmpty()
        val previousLocal = serverUrlStore.getLocal().orEmpty()
        _state.value = LoginUiState(
            step = LoginStep.ServerUrl,
            serverUrl = previousPublic,
            localUrl = previousLocal,
            rememberMe = rememberedCredentialsStore.isEnabled()
        )
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(
                error = UiError(userMessage = "Introduce usuario y contraseña")
            )
            return
        }
        if (serverUrlStore.getPublic().isNullOrEmpty()) {
            _state.value = current.copy(
                step = LoginStep.ServerUrl,
                error = UiError(userMessage = "Configura primero la URL del servidor")
            )
            return
        }
        _state.value = current.copy(isSubmitting = true, error = null)
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
                    serverUrl = serverUrlStore.getPublic().orEmpty(),
                    localUrl = serverUrlStore.getLocal().orEmpty(),
                    username = if (current.rememberMe) username else "",
                    password = if (current.rememberMe) current.password else "",
                    rememberMe = current.rememberMe
                )
            } else {
                _state.value.copy(
                    isSubmitting = false,
                    error = result.exceptionOrNull()?.let {
                        errorFactory.from(it, "Error desconocido")
                    }
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
