package com.photonne.app.ui.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.models.FolderSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoldersUiState(
    val folders: List<FolderSummary> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class FoldersViewModel(
    private val repository: FoldersRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FoldersUiState())
    val state: StateFlow<FoldersUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { folders ->
                    _state.value = FoldersUiState(folders = folders.sortedBy { it.path.lowercase() })
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
}
