package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.SentShareLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class SentSharesUiState(
    val links: List<SentShareLink> = emptyList(),
    val isLoading: Boolean = false,
    /** Recarga pedida con el gesto; la de cada apertura va en silencio. */
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: UiError? = null,
)

/**
 * Backs the "My links" tab, which lists every public link the current user has created across
 * all albums (via `GET /api/share/sent`). Editing/revoking reuse the same `/api/share` endpoints
 * as the per-album share manager.
 */
class SentSharesViewModel(
    private val repository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(SentSharesUiState())
    val state: StateFlow<SentSharesUiState> = _state.asStateFlow()

    /**
     * Reloads on every open, keeping the cached list on screen meanwhile. Links
     * are also created, edited and revoked from an album's share sheet and from
     * the asset share flow, which this list never hears about: loading only once
     * left it showing revoked links and missing new ones.
     */
    fun load() {
        if (_state.value.isLoading) return
        reload(showIndicator = false)
    }

    fun refresh() {
        if (_state.value.isLoading) return
        reload(showIndicator = true)
    }

    private fun reload(showIndicator: Boolean) {
        _state.update {
            it.copy(isLoading = true, isRefreshing = showIndicator && it.links.isNotEmpty(), error = null)
        }
        viewModelScope.launch {
            runCatching { repository.sentShares() }
                .onSuccess { links ->
                    _state.update {
                        it.copy(
                            links = links,
                            isLoading = false,
                            isRefreshing = false,
                            hasLoaded = true,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            hasLoaded = true,
                            error = errorFactory.from(error, "No se pudieron cargar los enlaces")
                        )
                    }
                }
        }
    }

    fun editLink(
        token: String,
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?,
        allowUpload: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.updateShare(
                    token = token,
                    expiresAt = expiresAt,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews,
                    allowUpload = allowUpload
                )
            }
                .onSuccess { result ->
                    _state.update { current ->
                        current.copy(
                            links = current.links.map { link ->
                                if (link.token == result.token) link.copy(
                                    expiresAt = result.expiresAt,
                                    hasPassword = result.hasPassword,
                                    allowDownload = result.allowDownload,
                                    maxViews = result.maxViews,
                                    allowUpload = result.allowUpload
                                ) else link
                            },
                            isMutating = false
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo actualizar el enlace")
                        )
                    }
                }
        }
    }

    fun revoke(token: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.revokeShare(token) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            links = it.links.filterNot { link -> link.token == token },
                            isMutating = false
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo revocar el enlace")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
