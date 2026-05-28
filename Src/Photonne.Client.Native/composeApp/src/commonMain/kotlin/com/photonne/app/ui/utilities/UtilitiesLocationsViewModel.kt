package com.photonne.app.ui.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.FolderTreeNode
import com.photonne.app.data.utilities.UtilitiesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UtilitiesLocationsUiState(
    val roots: List<FolderTreeNode> = emptyList(),
    val expanded: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: UiError? = null,
)

class UtilitiesLocationsViewModel(
    private val repository: UtilitiesRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(UtilitiesLocationsUiState())
    val state: StateFlow<UtilitiesLocationsUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.roots.isNotEmpty() || _state.value.isLoading) return
        load()
    }

    fun refresh() = load()

    private fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.folderTree() }
                .onSuccess { roots ->
                    _state.update {
                        // Auto-expand the root level so the tree is
                        // useful immediately without an extra tap.
                        it.copy(
                            roots = roots,
                            isLoading = false,
                            expanded = roots.map { node -> node.id }.toSet()
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Could not load folder tree")
                        )
                    }
                }
        }
    }

    fun toggle(folderId: String) {
        _state.update { current ->
            current.copy(
                expanded = current.expanded.toMutableSet().also { set ->
                    if (folderId in set) set.remove(folderId) else set.add(folderId)
                }
            )
        }
    }
}
