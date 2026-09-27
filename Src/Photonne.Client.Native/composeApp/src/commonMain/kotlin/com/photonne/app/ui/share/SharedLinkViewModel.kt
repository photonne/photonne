package com.photonne.app.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.PublicShareContent
import com.photonne.app.ui.main.SharedLinkTarget
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Por qué un enlace no se puede ver (sin reintento posible). */
enum class SharedLinkUnavailable { Expired, NotFound }

data class SharedLinkUiState(
    val target: SharedLinkTarget? = null,
    /** Origen contra el que se resuelven las URL relativas de miniaturas y contenido. */
    val serverBaseUrl: String = "",
    val isLoading: Boolean = false,
    val content: PublicShareContent? = null,
    val requiresPassword: Boolean = false,
    val wrongPassword: Boolean = false,
    val unavailable: SharedLinkUnavailable? = null,
    val error: UiError? = null,
) {
    /** El enlace es de otro servidor Photonne, no del de la sesión. */
    val isForeignServer: Boolean get() = target?.serverUrl != null
}

/**
 * Vista en la app de un enlace `photonne://share/{token}` (la web pública lo
 * ofrece con "Abrir en la app"). Lee el mismo `GET /api/share/{token}` que la
 * página web, con su puerta de contraseña, sin sesión y contra el servidor que
 * emitió el enlace.
 */
class SharedLinkViewModel(
    private val api: PhotonneApi,
    private val urlStore: ServerUrlStore,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(SharedLinkUiState())
    val state: StateFlow<SharedLinkUiState> = _state.asStateFlow()

    private var password: String? = null
    private var loadJob: Job? = null

    fun open(target: SharedLinkTarget) {
        password = null
        _state.value = SharedLinkUiState(
            target = target,
            serverBaseUrl = target.serverUrl ?: urlStore.effectiveBaseUrl.value.orEmpty()
        )
        load()
    }

    fun submitPassword(value: String) {
        if (value.isEmpty()) return
        password = value
        load()
    }

    fun retry() = load()

    fun close() {
        loadJob?.cancel()
        password = null
        _state.value = SharedLinkUiState()
    }

    private fun load() {
        val target = _state.value.target ?: return
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            runCatching { api.getPublicShare(target.token, password, target.serverUrl) }
                .onSuccess { content ->
                    _state.update {
                        if (content.requiresPassword) it.copy(
                            isLoading = false,
                            content = null,
                            requiresPassword = true,
                            wrongPassword = content.wrongPassword
                        ) else it.copy(
                            isLoading = false,
                            content = content,
                            requiresPassword = false,
                            wrongPassword = false
                        )
                    }
                }
                .onFailure { error ->
                    // 410 = caducado o sin visitas; 404 = revocado o inexistente.
                    val unavailable = when ((error as? PhotonneApiException)?.status) {
                        410 -> SharedLinkUnavailable.Expired
                        404 -> SharedLinkUnavailable.NotFound
                        else -> null
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            unavailable = unavailable,
                            error = if (unavailable == null)
                                errorFactory.from(error, "No se pudo abrir el enlace")
                            else null
                        )
                    }
                }
        }
    }
}
