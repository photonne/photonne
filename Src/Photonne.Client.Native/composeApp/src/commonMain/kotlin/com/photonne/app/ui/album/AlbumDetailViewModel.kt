package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val albumId: String? = null,
    val albumName: String? = null,
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AlbumDetailViewModel(
    private val repository: AlbumsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumDetailUiState())
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    fun open(albumId: String, name: String) {
        if (_state.value.albumId == albumId && _state.value.items.isNotEmpty()) {
            _state.update { it.copy(albumName = name) }
            return
        }
        _state.value = AlbumDetailUiState(albumId = albumId, albumName = name, isLoading = true)
        viewModelScope.launch {
            runCatching { repository.assets(albumId) }
                .onSuccess { items ->
                    _state.update { it.copy(items = items, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Error al cargar el álbum"
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
}
