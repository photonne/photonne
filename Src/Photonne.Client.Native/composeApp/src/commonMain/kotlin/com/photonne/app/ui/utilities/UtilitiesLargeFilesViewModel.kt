package com.photonne.app.ui.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.utilities.UtilitiesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UtilitiesLargeFilesUiState(
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val count: Int = DEFAULT_COUNT
) {
    val totalBytes: Long get() = items.sumOf { it.fileSize }

    companion object {
        const val DEFAULT_COUNT = 50

        /** Choices surfaced by the count chip group, mirroring the
         *  options the PWA's Utilities/LargeFiles page exposes. */
        val CountOptions = listOf(25, 50, 100, 200)
    }
}

class UtilitiesLargeFilesViewModel(
    private val repository: UtilitiesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(UtilitiesLargeFilesUiState())
    val state: StateFlow<UtilitiesLargeFilesUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.items.isNotEmpty() || _state.value.isLoading) return
        load()
    }

    fun setCount(count: Int) {
        if (count == _state.value.count) return
        _state.update { it.copy(count = count) }
        load()
    }

    fun refresh() = load()

    private fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        val count = _state.value.count
        viewModelScope.launch {
            runCatching { repository.largeFiles(count) }
                .onSuccess { items ->
                    _state.update { it.copy(items = items, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load large files"
                        )
                    }
                }
        }
    }
}
