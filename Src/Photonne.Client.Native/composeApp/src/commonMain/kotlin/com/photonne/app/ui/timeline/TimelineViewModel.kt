package com.photonne.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.timeline.TimelineRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class TimelineUiState(
    val items: List<TimelineItem> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val nextCursor: Instant? = null,
    val hasMore: Boolean = true,
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false
) {
    val isEmpty: Boolean get() = !isInitialLoading && items.isEmpty() && errorMessage == null
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

class TimelineViewModel(
    private val repository: TimelineRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private var pagingJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        pagingJob?.cancel()
        _state.update {
            it.copy(
                isRefreshing = it.items.isNotEmpty(),
                isInitialLoading = it.items.isEmpty(),
                errorMessage = null
            )
        }
        pagingJob = viewModelScope.launch {
            runCatching { repository.loadPage(cursor = null) }
                .onSuccess { result ->
                    _state.value = TimelineUiState(
                        items = result.items,
                        nextCursor = result.nextCursor,
                        hasMore = result.hasMore
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "Error al cargar la línea de tiempo"
                        )
                    }
                }
        }
    }

    fun setFavorite(assetId: String, isFavorite: Boolean) {
        _state.update { previous ->
            val updated = previous.items.map { item ->
                if (item.id == assetId) item.copy(isFavorite = isFavorite) else item
            }
            previous.copy(items = updated)
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isAppending || current.isInitialLoading || !current.hasMore) return
        val cursor = current.nextCursor ?: return

        _state.update { it.copy(isAppending = true, errorMessage = null) }
        pagingJob = viewModelScope.launch {
            runCatching { repository.loadPage(cursor = cursor) }
                .onSuccess { result ->
                    _state.update { previous ->
                        previous.copy(
                            items = previous.items + result.items,
                            nextCursor = result.nextCursor,
                            hasMore = result.hasMore,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isAppending = false,
                            errorMessage = error.message ?: "Error al paginar"
                        )
                    }
                }
        }
    }

    // ---------- Selection ----------

    fun toggleSelection(assetId: String) {
        _state.update { previous ->
            val next = previous.selection.toMutableSet()
            if (!next.add(assetId)) next.remove(assetId)
            previous.copy(selection = next)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun selectedItems(): List<TimelineItem> {
        val state = _state.value
        return state.items.filter { it.id in state.selection }
    }

    private fun selectedIds(): List<String> = _state.value.selection.toList()

    fun bulkArchive() {
        val ids = selectedIds()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previous = _state.value.items
        _state.update {
            it.copy(
                isBulkMutating = true,
                errorMessage = null,
                items = it.items.filterNot { item -> item.id in it.selection },
                selection = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching { assetRepository.archive(ids) }
                .onSuccess { _state.update { it.copy(isBulkMutating = false) } }
                .onFailure { error -> revertBulk(previous, error.message ?: "Failed to archive") }
        }
    }

    fun bulkTrash() {
        val ids = selectedIds()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previous = _state.value.items
        _state.update {
            it.copy(
                isBulkMutating = true,
                errorMessage = null,
                items = it.items.filterNot { item -> item.id in it.selection },
                selection = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching { assetRepository.trash(ids) }
                .onSuccess { _state.update { it.copy(isBulkMutating = false) } }
                .onFailure { error -> revertBulk(previous, error.message ?: "Failed to delete") }
        }
    }

    fun bulkAddToAlbum(albumId: String, onComplete: (List<TimelineItem>) -> Unit = {}) {
        val items = selectedItems()
        val ids = items.map { it.id }
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { albumsRepository.addAssetsBatch(albumId, ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false, selection = emptySet()) }
                    onComplete(items)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            errorMessage = error.message ?: "Failed to add to album"
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun revertBulk(previousItems: List<TimelineItem>, message: String) {
        _state.update {
            it.copy(
                items = previousItems,
                isBulkMutating = false,
                errorMessage = message
            )
        }
    }
}
