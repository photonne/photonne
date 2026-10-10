package com.photonne.app.ui.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
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
    val error: UiError? = null,
    val count: Int = DEFAULT_COUNT,
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
    private val repository: UtilitiesRepository,
    private val errorFactory: UiErrorFactory,
    mutationBus: AssetMutationBus,
) : ViewModel() {

    private val _state = MutableStateFlow(UtilitiesLargeFilesUiState())
    val state: StateFlow<UtilitiesLargeFilesUiState> = _state.asStateFlow()

    init {
        // Lote L10: lo borrado o archivado desde el visor sale de la lista; lo
        // que vuelve pide recargar (solo si la pantalla ya se había cargado).
        viewModelScope.launch {
            mutationBus.events.collect { event ->
                when (event) {
                    is AssetMutation.Removed -> removeItems(event.assetIds)
                    is AssetMutation.Purged -> removeItems(event.assetIds)
                    is AssetMutation.Restored, AssetMutation.AllChanged ->
                        if (_state.value.items.isNotEmpty()) load()
                    is AssetMutation.FavoriteChanged -> _state.update { current ->
                        current.copy(items = current.items.map {
                            if (it.id == event.assetId) it.copy(isFavorite = event.isFavorite) else it
                        })
                    }
                    is AssetMutation.DateChanged -> Unit
                }
            }
        }
    }

    private fun removeItems(assetIds: List<String>) {
        val ids = assetIds.toSet()
        _state.update { current -> current.copy(items = current.items.filterNot { it.id in ids }) }
    }

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
        _state.update { it.copy(isLoading = true, error = null) }
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
                            error = errorFactory.from(error, "No se pudieron cargar los archivos grandes")
                        )
                    }
                }
        }
    }
}
