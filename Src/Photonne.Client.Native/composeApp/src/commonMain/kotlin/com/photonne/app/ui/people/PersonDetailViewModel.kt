package com.photonne.app.ui.people

import com.photonne.app.ui.util.withRestored
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.ErrorMessages
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.toTimelineItem
import com.photonne.app.data.people.PeopleRepository
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.applying
import com.photonne.app.ui.selection.toggled
import com.photonne.app.ui.selection.toggledAll
import com.photonne.app.ui.selection.withSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.photonne.app.util.suspendRunCatching
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus

data class PersonDetailUiState(
    val personId: String? = null,
    val personName: String? = null,
    val items: List<TimelineItem> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val isInitialLoading: Boolean = false,
    /** Recarga por gesto con contenido ya visible (pull-to-refresh). */
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
    val selection: Set<String> = emptySet()
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

class PersonDetailViewModel(
    private val peopleRepository: PeopleRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
    mutationBus: AssetMutationBus,
) : ViewModel() {

    private val _state = MutableStateFlow(PersonDetailUiState())
    val state: StateFlow<PersonDetailUiState> = _state.asStateFlow()

    /**
     * The page load in flight (first page or append). Opening another person
     * or refreshing cancels it, and results are dropped unless they still
     * belong to the open person.
     */
    private var loadJob: Job? = null

    init {
        // Punto 52: las mutaciones confirmadas por el servidor (archivar,
        // papelera, restaurar, purgar, favorito) llegan por el bus; App.kt ya
        // no parchea esta lista a mano.
        viewModelScope.launch {
            mutationBus.events.collect { event ->
                when (event) {
                    is AssetMutation.Removed -> event.assetIds.forEach(::applyAssetRemovedLocal)
                    is AssetMutation.Purged -> event.assetIds.forEach(::applyAssetRemovedLocal)
                    is AssetMutation.Restored, AssetMutation.AllChanged -> refresh()
                    is AssetMutation.FavoriteChanged -> setFavorite(event.assetId, event.isFavorite)
                }
            }
        }
    }

    fun open(personId: String, personName: String?) {
        if (_state.value.personId == personId && _state.value.items.isNotEmpty()) {
            _state.update { it.copy(personName = personName) }
            return
        }
        _state.value = PersonDetailUiState(
            personId = personId,
            personName = personName,
            isInitialLoading = true
        )
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            suspendRunCatching { peopleRepository.assets(personId, limit = PAGE_SIZE, offset = 0) }
                .onSuccess { page ->
                    _state.update {
                        if (it.personId != personId) return@update it
                        val items = page.items.map { p -> p.toTimelineItem() }
                        it.copy(
                            items = items,
                            total = page.total,
                            hasMore = items.size < page.total,
                            isInitialLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.personId != personId) it
                        else it.copy(
                            isInitialLoading = false,
                            error = errorFactory.from(error, "No se pudieron cargar las fotos")
                        )
                    }
                }
        }
    }

    /** Recarga la primera página conservando el contenido visible mientras. */
    fun refresh() {
        val personId = _state.value.personId ?: return
        if (_state.value.isInitialLoading || _state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, isAppending = false, error = null) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            suspendRunCatching { peopleRepository.assets(personId, limit = PAGE_SIZE, offset = 0) }
                .onSuccess { page ->
                    _state.update {
                        if (it.personId != personId) return@update it
                        val items = page.items.map { p -> p.toTimelineItem() }
                        it.copy(
                            items = items,
                            total = page.total,
                            hasMore = items.size < page.total,
                            isRefreshing = false,
                            // La selección puede apuntar a fotos que ya no están.
                            selection = it.selection.intersect(
                                items.mapTo(HashSet()) { item -> item.id }
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.personId != personId) it
                        else it.copy(
                            isRefreshing = false,
                            error = errorFactory.from(error, "No se pudieron cargar las fotos")
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val personId = snapshot.personId ?: return
        if (snapshot.isAppending || !snapshot.hasMore || snapshot.isInitialLoading) return
        if (loadJob?.isActive == true) return
        _state.update { it.copy(isAppending = true) }
        loadJob = viewModelScope.launch {
            suspendRunCatching {
                peopleRepository.assets(
                    personId = personId,
                    limit = PAGE_SIZE,
                    offset = snapshot.items.size
                )
            }
                .onSuccess { page ->
                    _state.update { previous ->
                        if (previous.personId != personId) return@update previous
                        val existing = previous.items.mapTo(HashSet()) { it.id }
                        val appended = page.items
                            .filter { it.id !in existing }
                            .map { it.toTimelineItem() }
                        val merged = previous.items + appended
                        previous.copy(
                            items = merged,
                            total = page.total,
                            hasMore = merged.size < page.total,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        if (it.personId != personId) it
                        else it.copy(
                            isAppending = false,
                            error = errorFactory.from(error, "No se pudo cargar más")
                        )
                    }
                }
        }
    }

    fun toggleSelection(assetId: String) {
        _state.update { it.copy(selection = it.selection.toggled(assetId)) }
    }

    /** Un frame de arrastre en banda, en una sola mutación — ver [SelectionPatch]. */
    fun applySelection(patch: SelectionPatch) {
        if (patch.isEmpty) return
        _state.update { it.copy(selection = it.selection.applying(patch)) }
    }

    /** Marca o desmarca [ids] en bloque, sin alternar (carril de filas). */
    fun setSelected(ids: Collection<String>, selected: Boolean) {
        _state.update { it.copy(selection = it.selection.withSelection(ids, selected)) }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun toggleSelectAll() {
        _state.update { previous ->
            previous.copy(selection = previous.selection.toggledAll(previous.items.map { it.id }))
        }
    }

    fun setFavorite(assetId: String, isFavorite: Boolean) {
        _state.update { previous ->
            previous.copy(
                items = previous.items.map { item ->
                    if (item.id == assetId) item.copy(isFavorite = isFavorite) else item
                }
            )
        }
    }

    fun applyAssetRemovedLocal(assetId: String) {
        _state.update { previous ->
            previous.copy(
                items = previous.items.filterNot { it.id == assetId },
                selection = previous.selection - assetId
            )
        }
    }

    fun bulkArchive(onResult: (UiError?) -> Unit = {}) = runBulk(
        action = { assetRepository.archive(it) },
        errorFallback = ErrorMessages.ARCHIVE_FAILED,
        onResult = onResult
    )

    fun bulkTrash(onResult: (UiError?) -> Unit = {}) = runBulk(
        action = { assetRepository.trash(it) },
        errorFallback = ErrorMessages.TRASH_FAILED,
        onResult = onResult
    )

    fun bulkAddToAlbum(albumId: String, onSuccess: (List<TimelineItem>) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val items = _state.value.items.filter { it.id in ids }
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { albumsRepository.addAssetsBatch(albumId, ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false, selection = emptySet()) }
                    onSuccess(items)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudo añadir al álbum")
                        )
                    }
                }
        }
    }

    fun applyRename(newName: String?) {
        _state.update { it.copy(personName = newName?.trim()?.takeIf { n -> n.isNotEmpty() }) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Detach every selected asset from this person without rejecting any
     * face. Each asset may carry several faces tied to the person, so the
     * server operates at asset granularity — we forward one request per
     * selected asset.
     */
    fun bulkUnlinkFromPerson(onSuccess: (detached: Int) -> Unit = {}) {
        val personId = _state.value.personId ?: return
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previous = _state.value.items
        _state.update {
            it.copy(
                isBulkMutating = true,
                error = null,
                items = it.items.filterNot { item -> item.id in it.selection },
                selection = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching {
                var detached = 0
                for (id in ids) {
                    val res = peopleRepository.unlinkAsset(personId, id)
                    detached += res.facesDetached
                }
                detached
            }
                .onSuccess { detached ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            total = (it.total - ids.size).coerceAtLeast(0)
                        )
                    }
                    onSuccess(detached)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            items = previous,
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudo desvincular")
                        )
                    }
                }
        }
    }

    private fun runBulk(
        action: suspend (List<String>) -> Unit,
        errorFallback: String,
        onResult: (UiError?) -> Unit = {}
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previousItems = _state.value.items
        val ownerId = _state.value.personId
        _state.update {
            it.copy(
                isBulkMutating = true,
                error = null,
                items = it.items.filterNot { item -> item.id in it.selection },
                selection = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching { action(ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false) }
                    onResult(null)
                }
                .onFailure { error ->
                    val uiError = errorFactory.from(error, errorFallback)
                    _state.update {
                        it.copy(
                            // Only while the same person is still open.
                            items = if (it.personId != ownerId) it.items
                                else it.items.withRestored(previousItems, ids.toSet()),
                            isBulkMutating = false,
                            error = uiError
                        )
                    }
                    onResult(uiError)
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 60
    }
}
