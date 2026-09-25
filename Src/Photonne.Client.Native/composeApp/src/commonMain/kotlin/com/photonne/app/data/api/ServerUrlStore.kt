package com.photonne.app.data.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.koinInject

/**
 * Persists the base URL of the Photonne server. Two URLs are tracked:
 *
 *  * `publicUrl` — reachable from anywhere (e.g. https://photos.example.com).
 *  * `localUrl`  — only reachable from the home LAN (e.g. http://192.168.1.10:5000).
 *
 * A [LocalReachabilityProbe] flips [localReachable] on/off as the device moves
 * between networks. [requireBaseUrl] resolves to the local URL when it is
 * configured and currently reachable, otherwise it falls back to the public
 * URL. The shared HttpClient and image loader read this at request time so a
 * network change takes effect without rebuilding the Koin graph.
 */
class ServerUrlStore(private val settings: Settings) {

    private val _publicUrl = MutableStateFlow(loadInitial(KEY_PUBLIC) ?: loadInitial(KEY_LEGACY))
    val publicUrl: StateFlow<String?> = _publicUrl.asStateFlow()

    private val _localUrl = MutableStateFlow(loadInitial(KEY_LOCAL))
    val localUrl: StateFlow<String?> = _localUrl.asStateFlow()

    private val _localReachable = MutableStateFlow(false)
    val localReachable: StateFlow<Boolean> = _localReachable.asStateFlow()

    private val _effectiveBaseUrl = MutableStateFlow(computeEffective())

    /**
     * Effective URL used by every HTTP request. Recomputed whenever any of
     * [publicUrl], [localUrl] or [localReachable] changes.
     */
    val effectiveBaseUrl: StateFlow<String?> = _effectiveBaseUrl.asStateFlow()

    fun getPublic(): String? = _publicUrl.value
    fun getLocal(): String? = _localUrl.value
    fun isLocalReachable(): Boolean = _localReachable.value

    fun requireBaseUrl(): String =
        _effectiveBaseUrl.value ?: throw ServerUrlNotConfiguredException()

    fun setPublic(url: String) {
        val normalized = normalize(url)
        settings.putString(KEY_PUBLIC, normalized)
        if (settings.hasKey(KEY_LEGACY)) settings.remove(KEY_LEGACY)
        _publicUrl.value = normalized
        refreshEffective()
    }

    fun setLocal(url: String?) {
        val normalized = url?.let(::normalize)?.takeIf { it.isNotEmpty() }
        if (normalized.isNullOrEmpty()) {
            settings.remove(KEY_LOCAL)
            _localUrl.value = null
            _localReachable.value = false
        } else {
            settings.putString(KEY_LOCAL, normalized)
            _localUrl.value = normalized
        }
        refreshEffective()
    }

    fun setLocalReachable(reachable: Boolean) {
        _localReachable.value = reachable
        refreshEffective()
    }

    fun clear() {
        settings.remove(KEY_PUBLIC)
        settings.remove(KEY_LOCAL)
        settings.remove(KEY_LEGACY)
        _publicUrl.value = null
        _localUrl.value = null
        _localReachable.value = false
        refreshEffective()
    }

    private fun refreshEffective() {
        _effectiveBaseUrl.value = computeEffective()
    }

    private fun computeEffective(): String? {
        val local = _localUrl.value
        return if (_localReachable.value && !local.isNullOrEmpty()) local else _publicUrl.value
    }

    private fun loadInitial(key: String): String? =
        if (settings.hasKey(key)) settings.getString(key, "").takeIf { it.isNotEmpty() } else null

    companion object {
        private const val KEY_PUBLIC = "photonne.server.url.public"
        private const val KEY_LOCAL = "photonne.server.url.local"
        private const val KEY_LEGACY = "photonne.server.url"

        fun normalize(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return trimmed
            return if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) trimmed
            else "https://$trimmed"
        }

        /**
         * True when [raw] would send credentials in cleartext across the
         * internet: plain `http://` to a host that is not on a private network.
         * LAN and VPN addresses (RFC 1918, CGNAT/Tailscale, link-local,
         * `.local`, single-label names) are fine over HTTP and not flagged.
         */
        fun isCleartextToPublicHost(raw: String): Boolean {
            val url = normalize(raw)
            if (!url.startsWith("http://", ignoreCase = true)) return false
            val host = runCatching { io.ktor.http.Url(url).host }.getOrNull()
                ?.lowercase()?.removePrefix("[")?.removeSuffix("]")
                ?: return false
            return !isPrivateHost(host)
        }

        private fun isPrivateHost(host: String): Boolean {
            if (host.isEmpty() || host == "localhost") return true
            if (host.contains(':')) {
                // IPv6: loopback, link-local (fe80::/10), unique local (fc00::/7).
                return host == "::1" || host.startsWith("fe8") || host.startsWith("fe9") ||
                    host.startsWith("fea") || host.startsWith("feb") ||
                    host.startsWith("fc") || host.startsWith("fd")
            }
            val octets = host.split('.').map { it.toIntOrNull() }
            if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
                val (a, b) = octets.map { it!! }
                return a == 10 || a == 127 ||
                    (a == 172 && b in 16..31) ||
                    (a == 192 && b == 168) ||
                    (a == 169 && b == 254) ||
                    (a == 100 && b in 64..127)
            }
            return '.' !in host ||
                host.endsWith(".local") || host.endsWith(".lan") ||
                host.endsWith(".home.arpa") || host.endsWith(".internal")
        }
    }
}

class ServerUrlNotConfiguredException :
    IllegalStateException("Server URL has not been configured yet")

/**
 * Compose helper that returns the current effective Photonne server base URL
 * (local when reachable, public otherwise). Returns an empty string when no
 * URL is configured (login wizard not completed).
 */
@Composable
fun rememberApiBaseUrl(): String {
    val store: ServerUrlStore = koinInject()
    val url by store.effectiveBaseUrl.collectAsState()
    return url.orEmpty()
}
