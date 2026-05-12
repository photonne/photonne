package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.AdminStatsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminStatsUiState(
    val data: AdminStatsResponse? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AdminStatsViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminStatsUiState())
    val state: StateFlow<AdminStatsUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.getStats() }
                .onSuccess { stats ->
                    _state.update { it.copy(data = stats, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Could not load stats"
                        )
                    }
                }
        }
    }
}
