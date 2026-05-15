package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.api.LocalReachabilityProbe
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.api.skipAuthRefresh
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceConnectionUiState(
    val publicUrl: String = "",
    val localUrl: String = "",
    val localReachable: Boolean = false,
    val isProbing: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

/**
 * Per-device editor for the public/local server URLs shown in
 * Administración → Ajustes → Configuración del servidor. Writes to
 * [ServerUrlStore] (local-only) and re-probes [LocalReachabilityProbe] so the
 * effective URL switches immediately, without reopening the login wizard.
 */
class DeviceConnectionViewModel(
    private val store: ServerUrlStore,
    private val probe: LocalReachabilityProbe,
    private val httpClient: HttpClient
) : ViewModel() {

    private val _state = MutableStateFlow(loadInitial())
    val state: StateFlow<DeviceConnectionUiState> = _state.asStateFlow()

    private fun loadInitial(): DeviceConnectionUiState = DeviceConnectionUiState(
        publicUrl = store.getPublic().orEmpty(),
        localUrl = store.getLocal().orEmpty(),
        localReachable = store.isLocalReachable()
    )

    fun reload() {
        _state.value = loadInitial()
    }

    fun onPublicUrlChange(value: String) {
        _state.value = _state.value.copy(
            publicUrl = value,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun onLocalUrlChange(value: String) {
        _state.value = _state.value.copy(
            localUrl = value,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun testLocalConnection() {
        val current = _state.value
        if (current.isProbing || current.isSaving) return
        val local = current.localUrl.trim()
        if (local.isEmpty()) {
            _state.value = current.copy(errorMessage = "Introduce una URL local primero")
            return
        }
        val normalized = ServerUrlStore.normalize(local)
        if (!isValidUrl(normalized)) {
            _state.value = current.copy(errorMessage = "La URL local no es válida")
            return
        }
        _state.value = current.copy(isProbing = true, errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            // Persist first so the probe reads it from the store; we restore
            // the previous value if the probe fails and the user hasn't saved.
            val previousLocal = store.getLocal()
            store.setLocal(normalized)
            val reachable = probe.runProbe()
            // Roll the persisted value back if it wasn't saved before — the
            // probe should not silently overwrite the stored URL just from a
            // test.
            if (previousLocal != normalized) store.setLocal(previousLocal)
            _state.value = _state.value.copy(
                isProbing = false,
                localReachable = reachable && store.getLocal() == normalized,
                infoMessage = if (reachable) PROBE_REACHABLE else PROBE_UNREACHABLE
            )
        }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isProbing) return
        val normalizedPublic = ServerUrlStore.normalize(current.publicUrl)
        if (!isValidUrl(normalizedPublic)) {
            _state.value = current.copy(
                errorMessage = "La URL pública no es válida"
            )
            return
        }
        val rawLocal = current.localUrl.trim()
        val normalizedLocal = if (rawLocal.isEmpty()) null else ServerUrlStore.normalize(rawLocal)
        if (normalizedLocal != null && !isValidUrl(normalizedLocal)) {
            _state.value = current.copy(errorMessage = "La URL local no es válida")
            return
        }
        _state.value = current.copy(isSaving = true, errorMessage = null, infoMessage = null)
        viewModelScope.launch {
            // Validate the public URL is reachable before persisting — otherwise
            // a typo could lock the user out of their own server.
            val publicOk = probeOnce(normalizedPublic)
            if (!publicOk) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorMessage = "No se pudo contactar con la URL pública"
                )
                return@launch
            }
            store.setPublic(normalizedPublic)
            store.setLocal(normalizedLocal)
            val reachable = probe.runProbe()
            _state.value = _state.value.copy(
                isSaving = false,
                publicUrl = normalizedPublic,
                localUrl = normalizedLocal.orEmpty(),
                localReachable = reachable,
                infoMessage = SAVED
            )
        }
    }

    private suspend fun probeOnce(url: String): Boolean = try {
        val response: HttpResponse = httpClient.head("$url/api/auth/login") {
            skipAuthRefresh()
        }
        response.status.value < 500
    } catch (_: Throwable) {
        false
    }

    private fun isValidUrl(url: String): Boolean = try {
        if (url.isBlank()) false
        else {
            val parsed = Url(url)
            parsed.host.isNotEmpty() &&
                (parsed.protocol.name == "http" || parsed.protocol.name == "https")
        }
    } catch (_: Throwable) {
        false
    }

    companion object {
        // String keys would be cleaner, but the VM lives outside Compose so it
        // can't read stringResource. Screens map these sentinels to localized
        // strings.
        const val PROBE_REACHABLE = "probe_reachable"
        const val PROBE_UNREACHABLE = "probe_unreachable"
        const val SAVED = "saved"
    }
}
