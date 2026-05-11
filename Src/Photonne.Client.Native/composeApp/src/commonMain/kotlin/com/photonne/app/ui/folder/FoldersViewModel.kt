package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.folder.filterPersonalFolders
import com.photonne.app.data.models.FolderSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoldersUiState(
    val folders: List<FolderSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null
)

class FoldersViewModel(
    private val repository: FoldersRepository,
    private val authState: AuthStateHolder
) : ViewModel() {

    private val _state = MutableStateFlow(FoldersUiState())
    val state: StateFlow<FoldersUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val userId = (authState.state.value as? AuthState.Authenticated)?.user?.id
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { folders ->
                    val visible = if (userId.isNullOrBlank()) folders
                        else filterPersonalFolders(folders, userId)
                    _state.value = FoldersUiState(
                        folders = visible.sortedBy { it.path.lowercase() }
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load folders"
                        )
                    }
                }
        }
    }

    fun create(name: String, parentFolderId: String?, onCreated: (FolderSummary) -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                repository.create(
                    name = name.trim(),
                    parentFolderId = parentFolderId,
                    isSharedSpace = false
                )
            }
                .onSuccess { folder ->
                    _state.update {
                        it.copy(
                            folders = (listOf(folder) + it.folders).sortedBy { f -> f.path.lowercase() },
                            isMutating = false
                        )
                    }
                    onCreated(folder)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            errorMessage = error.message ?: "Failed to create folder"
                        )
                    }
                }
        }
    }

    fun applyUpdate(updated: FolderSummary) {
        _state.update { previous ->
            previous.copy(
                folders = previous.folders
                    .map { if (it.id == updated.id) updated else it }
                    .sortedBy { it.path.lowercase() }
            )
        }
    }

    fun applyDelete(folderId: String) {
        _state.update { previous ->
            previous.copy(folders = previous.folders.filterNot { it.id == folderId })
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
