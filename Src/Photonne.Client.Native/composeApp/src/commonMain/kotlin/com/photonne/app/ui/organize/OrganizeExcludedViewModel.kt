package com.photonne.app.ui.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.organize.OrganizeRepository
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.applying
import com.photonne.app.ui.selection.toggled
import com.photonne.app.ui.selection.toggledAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

data class OrganizeExcludedUiState(
    val items: List<TimelineItem> = emptyList(),
    val selection: Set<String> = emptySet(),
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isRefreshing: Boolean = false,
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
    val nextCursor: Instant? = null,
    val hasMore: Boolean = true,
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = loaded && items.isEmpty() && !isInitialLoading
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

/**
 * "Apartadas" de la bandeja: lo que el usuario marcó como "no hace falta
 * organizarlo". Antes solo volvía con el Deshacer del snackbar; aquí se revisa
 * y se devuelve a la bandeja en bloque.
 */
class OrganizeExcludedViewModel(
    private val repository: OrganizeRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(OrganizeExcludedUiState())
    val state: StateFlow<OrganizeExcludedUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        val snapshot = _state.value
        if (snapshot.loaded || snapshot.isInitialLoading) return
        refresh()
    }

    fun refresh() {
        _state.update {
            it.copy(isRefreshing = it.loaded, isInitialLoading = !it.loaded, error = null)
        }
        viewModelScope.launch {
            runCatching { repository.excluded(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        val ids = page.items.mapTo(HashSet()) { item -> item.id }
                        it.copy(
                            items = page.items,
                            selection = it.selection.intersect(ids),
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            isInitialLoading = false,
                            isRefreshing = false,
                            loaded = true
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = errorFactory.from(error, "No se pudieron cargar las apartadas")
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isAppending || snapshot.isInitialLoading || !snapshot.hasMore) return
        val cursor = snapshot.nextCursor ?: return
        _state.update { it.copy(isAppending = true) }
        viewModelScope.launch {
            runCatching { repository.excluded(cursor = cursor) }
                .onSuccess { page ->
                    _state.update {
                        val existing = it.items.mapTo(HashSet()) { item -> item.id }
                        it.copy(
                            items = it.items + page.items.filter { item -> item.id !in existing },
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isAppending = false, error = errorFactory.from(error, "No se pudo cargar más"))
                    }
                }
        }
    }

    fun toggleSelection(assetId: String) {
        _state.update { it.copy(selection = it.selection.toggled(assetId)) }
    }

    fun applySelection(patch: SelectionPatch) {
        if (patch.isEmpty) return
        _state.update { it.copy(selection = it.selection.applying(patch)) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun toggleSelectAll() {
        _state.update { previous ->
            previous.copy(selection = previous.selection.toggledAll(previous.items.map { it.id }))
        }
    }

    /**
     * Devuelve lo seleccionado a la bandeja (`excluded = false`). Sale de esta
     * lista al momento; [onDone] recibe cuántas han vuelto para el snackbar y
     * para que el llamante refresque la bandeja y su contador.
     */
    fun includeSelected(onDone: (Int) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.setExcluded(ids, excluded = false) }
                .onSuccess {
                    val touched = ids.toHashSet()
                    _state.update {
                        it.copy(
                            items = it.items.filterNot { item -> item.id in touched },
                            selection = emptySet(),
                            isBulkMutating = false
                        )
                    }
                    onDone(ids.size)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudieron devolver a la bandeja")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
