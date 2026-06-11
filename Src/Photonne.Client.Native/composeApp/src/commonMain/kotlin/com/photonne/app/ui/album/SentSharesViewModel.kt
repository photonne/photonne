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
import kotlinx.datetime.Instant

data class SentSharesUiState(
    val links: List<SentShareLink> = emptyList(),
    val isLoading: Boolean = false,
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

    /** Loads on first open; later opens of the tab keep the cached list (use [refresh] to reload). */
    fun load() {
        if (_state.value.hasLoaded || _state.value.isLoading) return
        reload()
    }

    fun refresh() {
        if (_state.value.isLoading) return
        reload()
    }

    private fun reload() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.sentShares() }
                .onSuccess { links ->
                    _state.update {
                        it.copy(links = links, isLoading = false, hasLoaded = true, error = null)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasLoaded = true,
                            error = errorFactory.from(error, "Failed to load share links")
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
        maxViews: Int?
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
                    maxViews = maxViews
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
                                    maxViews = result.maxViews
                                ) else link
                            },
                            isMutating = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to update share link")
                        )
                    }
                }
        }
    }

    fun revoke(token: String) {
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
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to revoke share link")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
