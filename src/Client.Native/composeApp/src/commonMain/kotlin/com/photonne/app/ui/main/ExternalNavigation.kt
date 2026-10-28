package com.photonne.app.ui.main

import com.photonne.app.ui.upload.PickedFile
import io.ktor.http.decodeURLQueryComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pantallas a las que se puede llegar desde fuera de la app (lote M4): una
 * notificación del sistema lleva el destino como extra del intent y la
 * plataforma lo publica aquí. También "Compartir con Photonne" desde otra app
 * y los enlaces `photonne://share/{token}` (funciones nuevas 3/3), y el widget
 * de Recuerdos (`photonne://memory/{id}`).
 */
enum class ExternalDestination {
    /** Pantalla de Backup (notificación de progreso). */
    Backup,

    /** Backup → Pendientes (notificación de fallos). */
    BackupPending,

    /** Subida con lo compartido desde otra app ya en la cola. */
    Upload,

    /** Vista de un enlace compartido (`photonne://share/{token}`). */
    SharedLink,

    /** Notificaciones (aviso local de actividad nueva del servidor). */
    Notifications,

    /** Un recuerdo concreto (`photonne://memory/{id}`): lo abre el widget de Recuerdos. */
    Memory;

    companion object {
        /** Extra del intent (Android) / userInfo (iOS) que lleva el destino. */
        const val EXTRA_KEY = "com.photonne.app.destination"

        fun fromKey(key: String?): ExternalDestination? =
            key?.let { k -> entries.firstOrNull { it.name == k } }
    }
}

/**
 * Enlace compartido abierto desde fuera. [serverUrl] es el servidor que lo
 * emitió (la web lo añade como `?server=`): cada instalación tiene su dominio
 * y el enlace puede ser de otro Photonne que no es el de la sesión. Null usa
 * el servidor configurado.
 */
data class SharedLinkTarget(val token: String, val serverUrl: String?)

/**
 * Buzón de un solo destino: la plataforma deja el último pedido y
 * `AuthenticatedApp` lo consume una vez. Si llega sin sesión espera al login;
 * un segundo toque antes de consumir el primero lo sustituye.
 */
object ExternalNavigation {
    /** Esquema propio de los enlaces que abren la app (`photonne://share/{token}`). */
    const val SCHEME = "photonne"

    private val _pending = MutableStateFlow<ExternalDestination?>(null)
    val pending: StateFlow<ExternalDestination?> = _pending.asStateFlow()

    // Carga de los destinos que la necesitan. Se escriben antes de publicar el
    // destino y se vacían al consumirlo.
    private var sharedFiles: List<PickedFile> = emptyList()
    private var sharedLink: SharedLinkTarget? = null
    private var memoryId: String? = null

    private val _connectServer = MutableStateFlow<String?>(null)

    /**
     * Servidor de un enlace `photonne://connect?server=…` (el QR de Ajustes →
     * App móvil en la web) aún sin atender. Lo atiende el login, que lo
     * comprueba y lo guarda; con la sesión abierta se descarta.
     */
    val connectServer: StateFlow<String?> = _connectServer.asStateFlow()

    private val _oidcReturn = MutableStateFlow<OidcReturn?>(null)

    /**
     * The browser came back from the OpenID provider
     * (`photonne://oidc?ticket=…` or `?error=…`), not yet handled. The login
     * screen takes it.
     */
    val oidcReturn: StateFlow<OidcReturn?> = _oidcReturn.asStateFlow()

    fun consumeOidcReturn(): OidcReturn? {
        val value = _oidcReturn.value
        _oidcReturn.value = null
        return value
    }

    fun consumeConnectServer(): String? {
        val server = _connectServer.value
        _connectServer.value = null
        return server
    }

    fun request(destination: ExternalDestination) {
        _pending.value = destination
    }

    /** Devuelve el destino pendiente y vacía el buzón. */
    fun consume(): ExternalDestination? {
        val destination = _pending.value
        _pending.value = null
        return destination
    }

    /** Archivos compartidos desde otra app, ya leídos: abren la Subida con ellos en cola. */
    fun requestUpload(files: List<PickedFile>) {
        if (files.isEmpty()) return
        sharedFiles = files
        request(ExternalDestination.Upload)
    }

    fun consumeSharedFiles(): List<PickedFile> {
        val files = sharedFiles
        sharedFiles = emptyList()
        return files
    }

    fun consumeSharedLink(): SharedLinkTarget? {
        val target = sharedLink
        sharedLink = null
        return target
    }

    fun consumeMemoryId(): String? {
        val id = memoryId
        memoryId = null
        return id
    }

    /** El enlace que abre el recuerdo [id] (lo usa el widget de Recuerdos). */
    fun memoryUrl(id: String): String = "$SCHEME://memory/$id"

    /**
     * Abre un enlace `photonne://share/{token}[?server=<origen>]`. Devuelve
     * false si la URL no es de este esquema o no trae un token válido.
     */
    fun handleUrl(url: String): Boolean {
        parseConnectUrl(url)?.let { server ->
            _connectServer.value = server
            return true
        }
        parseOidcUrl(url)?.let { result ->
            _oidcReturn.value = result
            return true
        }
        parseMemoryUrl(url)?.let { id ->
            memoryId = id
            request(ExternalDestination.Memory)
            return true
        }
        val target = parseShareUrl(url) ?: return false
        sharedLink = target
        request(ExternalDestination.SharedLink)
        return true
    }

    /** `photonne://oidc?ticket=…` or `?error=…`, or null when it isn't that. */
    internal fun parseOidcUrl(url: String): OidcReturn? {
        val prefix = "$SCHEME://oidc"
        if (!url.startsWith(prefix, ignoreCase = true)) return null
        val params = url.substring(prefix.length).substringAfter('?', "").substringBefore('#')
            .split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=', "")
                if (key.isEmpty()) null
                else key to (runCatching { pair.substringAfter('=').decodeURLQueryComponent() }.getOrNull() ?: "")
            }
            .toMap()
        val ticket = params["ticket"]?.takeIf { it.isNotBlank() }
        val error = params["error"]?.takeIf { it.isNotBlank() }
        if (ticket == null && error == null) return null
        return OidcReturn(ticket = ticket, error = error)
    }

    /** El servidor de `photonne://connect?server=<origen>`, o null si no es eso. */
    internal fun parseConnectUrl(url: String): String? {
        val prefix = "$SCHEME://connect"
        if (!url.startsWith(prefix, ignoreCase = true)) return null
        return serverParam(url.substring(prefix.length).substringAfter('?', "").substringBefore('#'))
    }

    /** `server=` de una query, decodificado y solo si es http(s). */
    private fun serverParam(query: String): String? =
        query.split('&')
            .firstOrNull { it.startsWith("server=") }
            ?.removePrefix("server=")
            ?.let { runCatching { it.decodeURLQueryComponent() }.getOrNull() }
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf {
                it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("http://", ignoreCase = true)
            }

    /** El id de `photonne://memory/{id}`, o null si no es eso. */
    internal fun parseMemoryUrl(url: String): String? {
        val prefix = "$SCHEME://memory/"
        if (!url.startsWith(prefix, ignoreCase = true)) return null
        val id = url.substring(prefix.length).takeWhile { it != '?' && it != '#' && it != '/' }
        // Los ids del servidor son GUID: nada más entra en la ruta.
        if (id.isEmpty() || !id.all { it.isLetterOrDigit() || it == '-' }) return null
        return id
    }

    internal fun parseShareUrl(url: String): SharedLinkTarget? {
        val prefix = "$SCHEME://share/"
        if (!url.startsWith(prefix, ignoreCase = true)) return null
        val rest = url.substring(prefix.length)
        val token = rest.takeWhile { it != '?' && it != '#' && it != '/' }
        // Los tokens del servidor son un GUID en hex: nada más entra en la ruta.
        if (token.isEmpty() || !token.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            return null
        }
        val server = serverParam(rest.substringAfter('?', "").substringBefore('#'))
        return SharedLinkTarget(token = token, serverUrl = server)
    }
}

/** How the OpenID sign-in came back: a ticket to exchange, or the reason it didn't work. */
data class OidcReturn(val ticket: String?, val error: String?)
