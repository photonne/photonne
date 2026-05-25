package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.models.AlbumShareLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class AlbumSharesUiState(
    val albumId: String? = null,
    val links: List<AlbumShareLink> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val createdLink: AlbumShareLink? = null
)

class AlbumSharesViewModel(
    private val repository: AlbumsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumSharesUiState())
    val state: StateFlow<AlbumSharesUiState> = _state.asStateFlow()

    fun open(albumId: String) {
        if (_state.value.albumId == albumId && _state.value.links.isNotEmpty()) {
            return
        }
        _state.value = AlbumSharesUiState(albumId = albumId, isLoading = true)
        viewModelScope.launch { reloadInternal(albumId) }
    }

    fun refresh() {
        val albumId = _state.value.albumId ?: return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch { reloadInternal(albumId) }
    }

    fun createLink(
        expiresAt: Instant?,
        password: String?,
        allowDownload: Boolean,
        maxViews: Int?
    ) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.createShare(
                    albumId = albumId,
                    expiresAt = expiresAt,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews
                )
            }
                .onSuccess { link ->
                    _state.update {
                        it.copy(
                            links = listOf(link) + it.links,
                            isMutating = false,
                            createdLink = link
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to create share link"
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
        _state.update { it.copy(isMutating = true, errorMessage = null) }
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
                            errorMessage = error.message ?: "Failed to update share link"
                        )
                    }
                }
        }
    }

    fun revoke(token: String) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
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
                            errorMessage = error.message ?: "Failed to revoke share link"
                        )
                    }
                }
        }
    }

    fun consumeCreatedLink() {
        _state.update { it.copy(createdLink = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun close() {
        _state.value = AlbumSharesUiState()
    }

    private suspend fun reloadInternal(albumId: String) {
        runCatching { repository.listShares(albumId) }
            .onSuccess { links ->
                _state.update {
                    it.copy(
                        albumId = albumId,
                        links = links,
                        isLoading = false
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Failed to load share links"
                    )
                }
            }
    }
}
