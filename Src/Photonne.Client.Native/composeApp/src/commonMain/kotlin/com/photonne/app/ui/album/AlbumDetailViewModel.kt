package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val albumId: String? = null,
    val albumName: String? = null,
    val albumDescription: String? = null,
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null
)

class AlbumDetailViewModel(
    private val repository: AlbumsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumDetailUiState())
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    fun open(albumId: String, name: String, description: String? = null) {
        if (_state.value.albumId == albumId && _state.value.items.isNotEmpty()) {
            _state.update { it.copy(albumName = name, albumDescription = description ?: it.albumDescription) }
            return
        }
        _state.value = AlbumDetailUiState(
            albumId = albumId,
            albumName = name,
            albumDescription = description,
            isLoading = true
        )
        viewModelScope.launch {
            runCatching { repository.assets(albumId) }
                .onSuccess { items ->
                    _state.update { it.copy(items = items, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load album"
                        )
                    }
                }
        }
    }

    fun setFavorite(assetId: String, isFavorite: Boolean) {
        _state.update { previous ->
            val updated = previous.items.map { item ->
                if (item.id == assetId) item.copy(isFavorite = isFavorite) else item
            }
            previous.copy(items = updated)
        }
    }

    fun rename(name: String, description: String?, onSuccess: (AlbumSummary) -> Unit = {}) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.update(albumId, name.trim(), description?.trim()?.takeIf { it.isNotEmpty() })
            }
                .onSuccess { album ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            albumName = album.name,
                            albumDescription = album.description
                        )
                    }
                    onSuccess(album)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to rename album"
                        )
                    }
                }
        }
    }

    fun delete(onDeleted: (String) -> Unit) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.delete(albumId) }
                .onSuccess {
                    _state.value = AlbumDetailUiState()
                    onDeleted(albumId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to delete album"
                        )
                    }
                }
        }
    }

    fun removeAsset(assetId: String, onSuccess: (assetId: String) -> Unit = {}) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        val previous = _state.value.items
        _state.update {
            it.copy(
                isMutating = true,
                errorMessage = null,
                items = it.items.filterNot { item -> item.id == assetId }
            )
        }
        viewModelScope.launch {
            runCatching { repository.removeAsset(albumId, assetId) }
                .onSuccess {
                    _state.update { it.copy(isMutating = false) }
                    onSuccess(assetId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            items = previous,
                            errorMessage = error.message ?: "Failed to remove asset"
                        )
                    }
                }
        }
    }

    fun setCover(assetId: String, onSuccess: (AlbumSummary) -> Unit = {}) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.setCover(albumId, assetId) }
                .onSuccess { album ->
                    _state.update { it.copy(isMutating = false) }
                    onSuccess(album)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to set album cover"
                        )
                    }
                }
        }
    }

    fun leave(onLeft: (String) -> Unit) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.leave(albumId) }
                .onSuccess {
                    _state.value = AlbumDetailUiState()
                    onLeft(albumId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to leave album"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
