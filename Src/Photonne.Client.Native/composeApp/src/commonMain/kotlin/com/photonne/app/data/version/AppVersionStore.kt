package com.photonne.app.data.version

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.LatestReleaseResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cachea en memoria la versión del servidor con el que está hablando el
 * cliente. Se rellena llamando a [refresh] cuando cambia el `baseUrl` (al
 * iniciar sesión / al cambiar de servidor). No se persiste — es info de
 * runtime que sólo importa para incluirla en los reportes de error.
 */
class AppVersionStore(private val api: PhotonneApi) {
    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    suspend fun refresh() {
        runCatching { api.getServerVersion() }
            .onSuccess { _serverVersion.value = it.takeIf { v -> v.isNotBlank() } }
        // Failure: dejamos el valor previo o null. El reporte de error
        // saldrá sin la versión del servidor; no es un dato bloqueante.
    }

    private val _latestRelease = MutableStateFlow<LatestReleaseResponse?>(null)

    /**
     * Última release publicada, para avisar de instaladores nuevos. No se
     * deduce de [serverVersion]: el servidor se despliega en cada push y va
     * por delante de las releases, que son las que traen instaladores.
     */
    val latestRelease: StateFlow<LatestReleaseResponse?> = _latestRelease.asStateFlow()

    suspend fun refreshLatestRelease() {
        runCatching { api.getLatestRelease() }
            .onSuccess { _latestRelease.value = it }
    }

    fun clear() {
        _serverVersion.value = null
        _latestRelease.value = null
    }
}
