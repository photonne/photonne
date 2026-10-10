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
 * y los enlaces `photonne://share/{token}` (funciones nuevas 3/3).
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
    Notifications;

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

    /**
     * Abre un enlace `photonne://share/{token}[?server=<origen>]`. Devuelve
     * false si la URL no es de este esquema o no trae un token válido.
     */
    fun handleUrl(url: String): Boolean {
        val target = parseShareUrl(url) ?: return false
        sharedLink = target
        request(ExternalDestination.SharedLink)
        return true
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
        val query = rest.substringAfter('?', "").substringBefore('#')
        val server = query.split('&')
            .firstOrNull { it.startsWith("server=") }
            ?.removePrefix("server=")
            ?.let { runCatching { it.decodeURLQueryComponent() }.getOrNull() }
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf {
                it.startsWith("https://", ignoreCase = true) ||
                    it.startsWith("http://", ignoreCase = true)
            }
        return SharedLinkTarget(token = token, serverUrl = server)
    }
}
