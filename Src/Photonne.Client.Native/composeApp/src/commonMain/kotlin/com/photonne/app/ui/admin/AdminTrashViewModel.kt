package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.TrashStatsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminTrashUiState(
    val stats: TrashStatsResponse? = null,
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class AdminTrashViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminTrashUiState())
    val state: StateFlow<AdminTrashUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.getTrashStats() }
                .onSuccess { stats ->
                    _state.update { it.copy(stats = stats, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load trash stats"
                        )
                    }
                }
        }
    }

    fun cleanupExpired() {
        if (_state.value.isCleaning) return
        _state.update { it.copy(isCleaning = true, errorMessage = null, statusMessage = null) }
        viewModelScope.launch {
            runCatching { repository.cleanupExpiredTrash() }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isCleaning = false,
                            statusMessage = result.message
                                .ifBlank { "Removed ${result.deleted} items" }
                        )
                    }
                    load()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isCleaning = false,
                            errorMessage = error.message ?: "Cleanup failed"
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _state.update { it.copy(errorMessage = null, statusMessage = null) }
    }
}
