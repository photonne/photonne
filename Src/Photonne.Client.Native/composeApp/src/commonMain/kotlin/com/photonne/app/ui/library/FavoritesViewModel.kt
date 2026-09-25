package com.photonne.app.ui.library

import com.photonne.app.ui.util.withRestored
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.ErrorMessages
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TimelineItem
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
import kotlin.time.Instant
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus

data class FavoritesUiState(
    val items: List<TimelineItem> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isRefreshing: Boolean = false,
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
    val nextCursor: Instant? = null,
    val hasMore: Boolean = true,
    val selection: Set<String> = emptySet(),
    val loaded: Boolean = false
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
    val isEmpty: Boolean get() = loaded && items.isEmpty() && !isInitialLoading
}

class FavoritesViewModel(
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
    mutationBus: AssetMutationBus,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    /** The single refresh / append in flight; a new first-page load cancels it. */
    private var pagingJob: Job? = null

    init {
        // Punto 52: las mutaciones confirmadas por el servidor (archivar,
        // papelera, restaurar, purgar, favorito) llegan por el bus; App.kt ya
        // no parchea esta lista a mano.
        viewModelScope.launch {
            mutationBus.events.collect { event ->
                when (event) {
                    is AssetMutation.Removed -> {
                        event.assetIds.forEach(::applyAssetRemovedLocal)
                        refreshQuietly()
                    }
                    is AssetMutation.Purged -> {
                        event.assetIds.forEach(::applyAssetRemovedLocal)
                        refreshQuietly()
                    }
                    is AssetMutation.Restored, AssetMutation.AllChanged -> refreshQuietly()
                    is AssetMutation.FavoriteChanged -> setFavorite(event.assetId, event.isFavorite)
                }
            }
        }
    }

    /**
     * Recarga la primera página sin spinner tras una mutación llegada por el
     * bus: la lista visible sigue siendo válida mientras tanto y un fallo se
     * queda en silencio (ya se reintentará en el siguiente refresh explícito).
     * Solo si la pantalla llegó a cargar; si no, ensureLoaded() lo hará.
     */
    private fun refreshQuietly() {
        if (!_state.value.loaded) return
        // A full refresh in flight already brings fresh data; an append in
        // flight would land on top of the replaced first page, so drop it.
        if (pagingJob?.isActive == true && !_state.value.isAppending) return
        cancelAppend()
        pagingJob = viewModelScope.launch {
            suspendRunCatching { assetRepository.listFavorites(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = page.items,
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            selection = it.selection intersect page.items.map { a -> a.id }.toSet()
                        )
                    }
                }
        }
    }

    private fun cancelAppend() {
        if (_state.value.isAppending) {
            pagingJob?.cancel()
            _state.update { it.copy(isAppending = false) }
        }
    }

    fun ensureLoaded() {
        val snapshot = _state.value
        if (snapshot.loaded || snapshot.isInitialLoading) return
        refresh()
    }

    fun refresh() {
        _state.update {
            it.copy(
                isAppending = false,
                isRefreshing = it.loaded,
                isInitialLoading = !it.loaded,
                error = null
            )
        }
        pagingJob?.cancel()
        pagingJob = viewModelScope.launch {
            suspendRunCatching { assetRepository.listFavorites(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = page.items,
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            isInitialLoading = false,
                            isRefreshing = false,
                            loaded = true,
                            selection = emptySet()
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = errorFactory.from(error, "No se pudieron cargar los favoritos")
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isAppending || snapshot.isInitialLoading || !snapshot.hasMore) return
        if (pagingJob?.isActive == true) return
        val cursor = snapshot.nextCursor ?: return
        _state.update { it.copy(isAppending = true) }
        pagingJob = viewModelScope.launch {
            suspendRunCatching { assetRepository.listFavorites(cursor = cursor) }
                .onSuccess { page ->
                    _state.update {
                        val existing = it.items.mapTo(HashSet()) { item -> item.id }
                        val appended = page.items.filter { item -> item.id !in existing }
                        it.copy(
                            items = it.items + appended,
                            hasMore = page.hasMore,
                            nextCursor = page.nextCursor,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
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
        // When the user unfavorites an asset from the viewer while the
        // Favorites screen is hosting the list, it disappears from the
        // grid; otherwise we just mirror the flag locally.
        if (!isFavorite) {
            applyAssetRemovedLocal(assetId)
        } else {
            _state.update { previous ->
                previous.copy(
                    items = previous.items.map { item ->
                        if (item.id == assetId) item.copy(isFavorite = true) else item
                    }
                )
            }
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

    fun applyAssetRemovedLocal(assetId: String) {
        _state.update { previous ->
            previous.copy(
                items = previous.items.filterNot { it.id == assetId },
                selection = previous.selection - assetId
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun runBulk(
        action: suspend (List<String>) -> Unit,
        errorFallback: String,
        onResult: (UiError?) -> Unit = {}
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previousItems = _state.value.items
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
                            items = it.items.withRestored(previousItems, ids.toSet()),
                            isBulkMutating = false,
                            error = uiError
                        )
                    }
                    onResult(uiError)
                }
        }
    }
}
