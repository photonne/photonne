package com.photonne.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.timeline.MemoriesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoriesUiState(
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val attempted: Boolean = false
)

class MemoriesViewModel(
    private val repository: MemoriesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MemoriesUiState())
    val state: StateFlow<MemoriesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { items ->
                    _state.value = MemoriesUiState(items = items, attempted = true)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            attempted = true,
                            errorMessage = error.message ?: "Failed to load memories"
                        )
                    }
                }
        }
    }
}
