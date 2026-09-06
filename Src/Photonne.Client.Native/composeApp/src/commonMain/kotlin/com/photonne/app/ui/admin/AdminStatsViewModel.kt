package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.AdminIndexingCoverageResponse
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.AdminStatsResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminStatsUiState(
    val data: AdminStatsResponse? = null,
    val coverage: AdminIndexingCoverageResponse? = null,
    val isLoading: Boolean = false,
    val error: UiError? = null,
)

class AdminStatsViewModel(
    private val repository: AdminRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminStatsUiState())
    val state: StateFlow<AdminStatsUiState> = _state.asStateFlow()

    fun load() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // Coverage rides alongside the stats fetch but never blocks it: an
            // old server without the endpoint just leaves the card hidden.
            val coverageDeferred = async {
                runCatching { repository.getIndexingCoverage() }.getOrNull()
            }
            runCatching { repository.getStats() }
                .onSuccess { stats ->
                    val coverage = coverageDeferred.await()
                    _state.update { it.copy(data = stats, coverage = coverage, isLoading = false) }
                }
                .onFailure { error ->
                    coverageDeferred.await()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "No se pudieron cargar las estadísticas")
                        )
                    }
                }
        }
    }
}
