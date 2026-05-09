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

    /**
     * Optimistically flips the favorite state for [assetId] both in the
     * cache and in the visible UI state, then calls the API. On failure
     * the state is reverted. The confirmed value is delivered through
     * [onCommitted] so callers (e.g. App) can propagate it to the
     * timeline grid.
     */
    fun toggleFavorite(assetId: String, onCommitted: (Boolean) -> Unit) {
        val previous = cache[assetId]?.isFavorite
            ?: _state.value.detail?.takeIf { it.id == assetId }?.isFavorite
            ?: false
        val optimistic = !previous

        applyFavorite(assetId, optimistic)

        viewModelScope.launch {
            runCatching { repository.toggleFavorite(assetId) }
                .onSuccess { confirmed ->
                    if (confirmed != optimistic) applyFavorite(assetId, confirmed)
                    onCommitted(confirmed)
                }
                .onFailure { error ->
                    applyFavorite(assetId, previous)
                    _state.update {
                        it.copy(errorMessage = error.message ?: "No se pudo actualizar el favorito")
                    }
                }
        }
    }

    private fun applyFavorite(assetId: String, isFavorite: Boolean) {
        cache[assetId]?.let { cache[assetId] = it.copy(isFavorite = isFavorite) }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) {
                current.copy(detail = detail.copy(isFavorite = isFavorite))
            } else current
        }
    }

    fun archive(assetId: String, onCompleted: (assetId: String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.archive(listOf(assetId)) }
                .onSuccess {
                    cache.remove(assetId)
                    onCompleted(assetId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(errorMessage = error.message ?: "Failed to archive")
                    }
                }
        }
    }

    fun trash(assetId: String, onCompleted: (assetId: String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.trash(listOf(assetId)) }
                .onSuccess {
                    cache.remove(assetId)
                    onCompleted(assetId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(errorMessage = error.message ?: "Failed to delete")
                    }
                }
        }
    }

    fun updateDescription(assetId: String, description: String?) {
        val cleaned = description?.trim()?.takeIf { it.isNotEmpty() }
        // Optimistic local update — both cache and visible state.
        cache[assetId]?.let { cache[assetId] = it.copy(caption = cleaned) }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) {
                current.copy(detail = detail.copy(caption = cleaned))
            } else current
        }
        viewModelScope.launch {
            runCatching { repository.updateDescription(assetId, cleaned) }
                .onFailure { error ->
                    _state.update {
                        it.copy(errorMessage = error.message ?: "Failed to update description")
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}
