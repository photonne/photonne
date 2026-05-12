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
 * Persists the base URL of the Photonne server that the user enters on the
 * login wizard. The URL is read at request time by [PhotonneApiClient] and
 * the refresh handler, so it can change at runtime without re-creating the
 * Koin graph.
 */
class ServerUrlStore(private val settings: Settings) {

    private val _baseUrl = MutableStateFlow(loadInitial())
    val baseUrl: StateFlow<String?> = _baseUrl.asStateFlow()

    fun get(): String? = _baseUrl.value

    fun requireBaseUrl(): String =
        _baseUrl.value ?: throw ServerUrlNotConfiguredException()

    fun set(url: String) {
        val normalized = normalize(url)
        settings.putString(KEY, normalized)
        _baseUrl.value = normalized
    }

    fun clear() {
        settings.remove(KEY)
        _baseUrl.value = null
    }

    private fun loadInitial(): String? =
        if (settings.hasKey(KEY)) settings.getString(KEY, "").takeIf { it.isNotEmpty() } else null

    companion object {
        private const val KEY = "photonne.server.url"

        fun normalize(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return trimmed
            return if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) trimmed
            else "https://$trimmed"
        }
    }
}

class ServerUrlNotConfiguredException :
    IllegalStateException("Server URL has not been configured yet")

/**
 * Compose helper that returns the current Photonne server base URL. Returns
 * an empty string when no URL is configured (login wizard not completed).
 * Screens that render after authentication can rely on a non-empty value.
 */
@Composable
fun rememberApiBaseUrl(): String {
    val store: ServerUrlStore = koinInject()
    val url by store.baseUrl.collectAsState()
    return url.orEmpty()
}
