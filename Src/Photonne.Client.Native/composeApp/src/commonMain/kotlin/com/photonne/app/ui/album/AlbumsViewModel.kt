package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.models.AlbumSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AlbumsTab { Mine, Shared, MyLinks }

data class AlbumsUiState(
    val albums: List<AlbumSummary> = emptyList(),
    val selectedTab: AlbumsTab = AlbumsTab.Mine,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null
) {
    val visibleAlbums: List<AlbumSummary> get() = when (selectedTab) {
        AlbumsTab.Mine -> albums.filter { it.isOwner && !it.isShared }
        AlbumsTab.Shared -> albums.filter { !it.isOwner || it.isShared }
        AlbumsTab.MyLinks -> albums.filter { it.isOwner && it.hasActiveShareLink }
    }
}

class AlbumsViewModel(
    private val repository: AlbumsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumsUiState())
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    init { refresh() }

    fun selectTab(tab: AlbumsTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { albums ->
                    _state.update {
                        it.copy(albums = albums, isLoading = false, errorMessage = null)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load albums"
                        )
                    }
                }
        }
    }

    fun create(name: String, description: String?, onCreated: (AlbumSummary) -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.create(name.trim(), description?.trim()?.takeIf { it.isNotEmpty() }) }
                .onSuccess { album ->
                    _state.update {
                        it.copy(albums = listOf(album) + it.albums, isMutating = false)
                    }
                    onCreated(album)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to create album"
                        )
                    }
                }
        }
    }

    fun applyUpdate(updated: AlbumSummary) {
        _state.update { previous ->
            previous.copy(albums = previous.albums.map { if (it.id == updated.id) updated else it })
        }
    }

    fun applyDelete(albumId: String) {
        _state.update { previous ->
            previous.copy(albums = previous.albums.filterNot { it.id == albumId })
        }
    }

    fun applyAssetAdded(albumId: String) {
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) it.copy(assetCount = it.assetCount + 1) else it
                }
            )
        }
    }

    fun applyAssetsAdded(albumId: String, addedCount: Int) {
        if (addedCount <= 0) return
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) it.copy(assetCount = it.assetCount + addedCount) else it
                }
            )
        }
    }

    fun applyAssetRemoved(albumId: String) {
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) {
                        it.copy(assetCount = (it.assetCount - 1).coerceAtLeast(0))
                    } else it
                }
            )
        }
    }

    fun applyAssetsRemoved(albumId: String, removedCount: Int) {
        if (removedCount <= 0) return
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) {
                        it.copy(assetCount = (it.assetCount - removedCount).coerceAtLeast(0))
                    } else it
                }
            )
        }
    }

    fun applyShareLinkChanged(albumId: String, hasActiveShareLink: Boolean) {
        _state.update { previous ->
            previous.copy(
                albums = previous.albums.map {
                    if (it.id == albumId) it.copy(hasActiveShareLink = hasActiveShareLink) else it
                }
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
