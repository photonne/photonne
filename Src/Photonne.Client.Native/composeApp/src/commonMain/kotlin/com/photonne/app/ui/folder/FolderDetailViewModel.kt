package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderDetailUiState(
    val folderId: String? = null,
    val folderName: String? = null,
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class FolderDetailViewModel(
    private val repository: FoldersRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FolderDetailUiState())
    val state: StateFlow<FolderDetailUiState> = _state.asStateFlow()

    fun open(folderId: String, name: String) {
        if (_state.value.folderId == folderId && _state.value.items.isNotEmpty()) {
            _state.update { it.copy(folderName = name) }
            return
        }
        _state.value = FolderDetailUiState(folderId = folderId, folderName = name, isLoading = true)
        viewModelScope.launch {
            runCatching { repository.assets(folderId) }
                .onSuccess { items ->
                    _state.update { it.copy(items = items, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load folder"
                        )
                    }
                }
        }
    }

    fun setFavorite(assetId: String, isFavorite: Boolean) {
        _state.update { previous ->
            previous.copy(
                items = previous.items.map { item ->
                    if (item.id == assetId) item.copy(isFavorite = isFavorite) else item
                }
            )
        }
    }

    fun applyAssetRemovedLocal(assetId: String) {
        _state.update { previous ->
            previous.copy(items = previous.items.filterNot { it.id == assetId })
        }
    }
}
