package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderDetailUiState(
    val folderId: String? = null,
    val folderName: String? = null,
    val parentFolderId: String? = null,
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

class FolderDetailViewModel(
    private val repository: FoldersRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FolderDetailUiState())
    val state: StateFlow<FolderDetailUiState> = _state.asStateFlow()

    fun open(folderId: String, name: String, parentFolderId: String?) {
        if (_state.value.folderId == folderId && _state.value.items.isNotEmpty()) {
            _state.update { it.copy(folderName = name, parentFolderId = parentFolderId) }
            return
        }
        _state.value = FolderDetailUiState(
            folderId = folderId,
            folderName = name,
            parentFolderId = parentFolderId,
            isLoading = true
        )
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
            previous.copy(
                items = previous.items.filterNot { it.id == assetId },
                selection = previous.selection - assetId
            )
        }
    }

    fun toggleSelection(assetId: String) {
        _state.update { previous ->
            val next = previous.selection.toMutableSet()
            if (!next.add(assetId)) next.remove(assetId)
            previous.copy(selection = next)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun rename(name: String, onSuccess: (FolderSummary) -> Unit = {}) {
        val folderId = _state.value.folderId ?: return
        if (_state.value.isMutating) return
        val currentParent = _state.value.parentFolderId
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.update(folderId, name.trim(), parentFolderId = currentParent) }
                .onSuccess { folder ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            folderName = folder.name,
                            parentFolderId = folder.parentFolderId
                        )
                    }
                    onSuccess(folder)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to rename folder"
                        )
                    }
                }
        }
    }

    fun move(targetParentFolderId: String?, onSuccess: (FolderSummary) -> Unit = {}) {
        val folderId = _state.value.folderId ?: return
        val name = _state.value.folderName ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.update(folderId, name, parentFolderId = targetParentFolderId) }
                .onSuccess { folder ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            folderName = folder.name,
                            parentFolderId = folder.parentFolderId
                        )
                    }
                    onSuccess(folder)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to move folder"
                        )
                    }
                }
        }
    }

    fun moveSelectedAssets(targetFolderId: String, onSuccess: (movedIds: List<String>) -> Unit = {}) {
        val sourceFolderId = _state.value.folderId ?: return
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.moveAssets(
                    sourceFolderId = sourceFolderId,
                    targetFolderId = targetFolderId,
                    assetIds = ids
                )
            }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            items = previous.items.filterNot { it.id in ids },
                            selection = emptySet(),
                            isBulkMutating = false
                        )
                    }
                    onSuccess(ids)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            errorMessage = error.message ?: "Failed to move assets"
                        )
                    }
                }
        }
    }

    fun delete(onDeleted: (String) -> Unit) {
        val folderId = _state.value.folderId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.delete(folderId) }
                .onSuccess {
                    _state.value = FolderDetailUiState()
                    onDeleted(folderId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to delete folder"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
