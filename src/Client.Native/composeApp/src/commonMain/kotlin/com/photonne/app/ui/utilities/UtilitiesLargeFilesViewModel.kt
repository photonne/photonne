package com.photonne.app.ui.utilities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.utilities.UtilitiesRepository
import com.photonne.app.util.suspendRunCatching
import kotlinx.coroutines.Job
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
    /** Files marked to go to the trash. */
    val selection: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
) {
    val totalBytes: Long get() = items.sumOf { it.fileSize }
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
    val selectedBytes: Long get() = items.filter { it.id in selection }.sumOf { it.fileSize }

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
                    // Mover de carpeta no cambia esta lista.
                    is AssetMutation.Moved -> Unit
                }
            }
        }
    }

    private fun removeItems(assetIds: List<String>) {
        val ids = assetIds.toSet()
        _state.update { current ->
            current.copy(
                items = current.items.filterNot { it.id in ids },
                selection = current.selection - ids
            )
        }
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

    private var loadJob: Job? = null

    /**
     * Cancels the load in flight and starts over. It used to return while
     * loading, so picking another count during a load was dropped: the chip
     * showed the new count over the list of the old one.
     */
    private fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        val count = _state.value.count
        loadJob = viewModelScope.launch {
            suspendRunCatching { repository.largeFiles(count) }
                .onSuccess { items ->
                    // What was marked and is still listed stays marked.
                    val ids = items.mapTo(HashSet()) { it.id }
                    _state.update {
                        it.copy(items = items, isLoading = false, selection = it.selection.filterTo(HashSet()) { id -> id in ids })
                    }
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

    fun toggle(id: String) {
        _state.update {
            it.copy(selection = if (id in it.selection) it.selection - id else it.selection + id)
        }
    }

    /** Everything listed, or nothing when everything already was. */
    fun toggleSelectAll() {
        _state.update {
            val all = it.items.mapTo(HashSet()) { item -> item.id }
            it.copy(selection = if (it.selection.containsAll(all)) emptySet() else all)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    /**
     * Sends the marked files to the trash. The result goes to [onResult]
     * (`error == null` → done, with the ids so the snackbar can undo), as in
     * Duplicados.
     */
    fun deleteSelected(onResult: (deleted: List<String>, error: UiError?) -> Unit = { _, _ -> }) {
        val selected = _state.value.selection.toList()
        if (selected.isEmpty() || _state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            suspendRunCatching { repository.deleteAssets(selected) }
                .onSuccess {
                    _state.update { it.copy(isDeleting = false) }
                    removeItems(selected)
                    onResult(selected, null)
                }
                .onFailure { error ->
                    _state.update { it.copy(isDeleting = false) }
                    onResult(selected, errorFactory.from(error, "No se pudo eliminar"))
                }
        }
    }
}
