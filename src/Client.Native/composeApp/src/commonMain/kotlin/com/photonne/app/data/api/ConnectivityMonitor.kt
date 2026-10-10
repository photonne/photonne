package com.photonne.app.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Estado de conexión global que enseña la píldora del cromo superior. */
sealed interface ConnectivityStatus {
    data object Online : ConnectivityStatus

    /** El dispositivo no tiene red. */
    data object NoNetwork : ConnectivityStatus

    /** Hay red, pero el servidor no responde en la URL efectiva. */
    data class ServerUnreachable(val host: String) : ConnectivityStatus
}

// Deja asentarse la red (y que la sonda LAN/pública decida URL) antes de
// preguntar, y junta en una sola comprobación las ráfagas de fallos.
private val SETTLE_DELAY = 600.milliseconds
private val CHECK_TIMEOUT = 6.seconds
// Mientras no hay conexión se vuelve a mirar sola para que la píldora
// desaparezca al recuperarla, sin esperar a que el usuario toque nada.
private val OFFLINE_RECHECK = 15.seconds

/**
 * Une [NetworkMonitor] (¿hay red?) y un HEAD al servidor por la URL efectiva
 * (¿responde?) en un único [status]. Lo alimentan los cambios de red, los
 * cambios de URL efectiva (la sonda LAN/pública), los fallos de conexión de
 * cualquier petición a la API y, mientras dura el corte, una comprobación
 * periódica en primer plano.
 */
class ConnectivityMonitor(
    private val httpClient: HttpClient,
    private val store: ServerUrlStore,
    private val networkMonitor: NetworkMonitor,
) {
    private val _status = MutableStateFlow<ConnectivityStatus>(ConnectivityStatus.Online)
    val status: StateFlow<ConnectivityStatus> = _status.asStateFlow()

    private val foreground = MutableStateFlow(true)

    private val checkRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    fun start(scope: CoroutineScope) {
        scope.launch { networkMonitor.changes.collect { checkRequests.tryEmit(Unit) } }
        scope.launch { store.effectiveBaseUrl.drop(1).collect { checkRequests.tryEmit(Unit) } }
        scope.launch {
            checkRequests.collectLatest {
                delay(SETTLE_DELAY)
                check()
            }
        }
        scope.launch {
            combine(_status, foreground) { status, fg -> status != ConnectivityStatus.Online && fg }
                .collectLatest { polling ->
                    while (polling) {
                        delay(OFFLINE_RECHECK)
                        checkRequests.tryEmit(Unit)
                    }
                }
        }
    }

    /** Una petición a la API no llegó a conectar: comprobar (con rebote). */
    fun reportConnectionError() {
        checkRequests.tryEmit(Unit)
    }

    /** "Reintentar" de la píldora. */
    fun retry() {
        checkRequests.tryEmit(Unit)
    }

    /** La app entra o sale de primer plano: fuera no se sondea en bucle. */
    fun setForeground(value: Boolean) {
        foreground.value = value
        if (value && _status.value != ConnectivityStatus.Online) checkRequests.tryEmit(Unit)
    }

    private suspend fun check() {
        if (!networkMonitor.isNetworkAvailable()) {
            _status.value = ConnectivityStatus.NoNetwork
            return
        }
        // Sin servidor configurado (antes del login) no hay nada que avisar.
        val base = store.effectiveBaseUrl.value
        if (base.isNullOrEmpty()) {
            _status.value = ConnectivityStatus.Online
            return
        }
        val reachable = try {
            withTimeoutOrNull(CHECK_TIMEOUT) {
                // Igual que la sonda LAN: cualquier respuesta por debajo de 500
                // es el servidor contestando (un 502/503 es el proxy sin API).
                httpClient.head("$base/api/auth/login") { skipAuthRefresh() }.status.value < 500
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
        _status.value = if (reachable) {
            ConnectivityStatus.Online
        } else {
            ConnectivityStatus.ServerUnreachable(
                host = runCatching { Url(base).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: base
            )
        }
    }
}
