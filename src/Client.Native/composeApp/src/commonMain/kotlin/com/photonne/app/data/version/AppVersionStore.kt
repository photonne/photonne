package com.photonne.app.data.version

import com.photonne.app.PhotonneMinServerVersion
import com.photonne.app.PhotonneVersion
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.LatestReleaseResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cachea en memoria la versión del servidor con el que está hablando el
 * cliente y si ambos son compatibles. Se rellena llamando a [refresh] cuando
 * cambia el `baseUrl` (al iniciar sesión / al cambiar de servidor). No se
 * persiste — es info de runtime para los reportes de error y el aviso de
 * incompatibilidad.
 */
class AppVersionStore(private val api: PhotonneApi) {
    private val _serverVersion = MutableStateFlow<String?>(null)
    val serverVersion: StateFlow<String?> = _serverVersion.asStateFlow()

    private val _compatibility = MutableStateFlow<ServerCompatibility>(ServerCompatibility.Compatible)

    /** Si esta app y el servidor actual pueden entenderse; se recalcula en cada [refresh]. */
    val compatibility: StateFlow<ServerCompatibility> = _compatibility.asStateFlow()

    suspend fun refresh() {
        runCatching { api.getServerVersion() }
            .onSuccess { response ->
                val version = response.version.takeIf { it.isNotBlank() }
                _serverVersion.value = version
                _compatibility.value = serverCompatibility(
                    serverVersion = version,
                    minClientVersion = response.minClientVersion,
                    clientVersion = PhotonneVersion,
                    minServerVersion = PhotonneMinServerVersion
                )
            }
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
        _compatibility.value = ServerCompatibility.Compatible
        _latestRelease.value = null
    }
}
