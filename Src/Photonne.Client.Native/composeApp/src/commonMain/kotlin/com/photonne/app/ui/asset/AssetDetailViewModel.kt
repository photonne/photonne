package com.photonne.app.ui.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.models.AssetDetail
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val detail: AssetDetail? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Holds the metadata for the *currently visible* asset in the pager.
 * Detail is fetched lazily and cached per asset id while the screen is
 * alive. Caller invokes [select] every time the visible page changes.
 */
class AssetDetailViewModel(
    private val repository: AssetDetailRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AssetDetailUiState())
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    private val cache = mutableMapOf<String, AssetDetail>()
    private var currentJob: Job? = null
    private var currentId: String? = null

    fun select(assetId: String) {
        if (assetId == currentId && _state.value.detail?.id == assetId) return
        currentId = assetId
        cache[assetId]?.let { cached ->
            _state.value = AssetDetailUiState(detail = cached)
            return
        }
        currentJob?.cancel()
        _state.update { it.copy(isLoading = true, errorMessage = null, detail = null) }
        currentJob = viewModelScope.launch {
            runCatching { repository.getDetail(assetId) }
                .onSuccess { detail ->
                    cache[assetId] = detail
                    if (currentId == assetId) {
                        _state.value = AssetDetailUiState(detail = detail)
                    }
                }
                .onFailure { error ->
                    if (currentId == assetId) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Error al cargar los detalles"
                            )
                        }
                    }
                }
        }
    }
}
