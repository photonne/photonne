package com.photonne.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.toTimelineItem
import com.photonne.app.data.people.PeopleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonDetailUiState(
    val personId: String? = null,
    val personName: String? = null,
    val items: List<TimelineItem> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isBulkMutating: Boolean = false,
    val errorMessage: String? = null,
    val selection: Set<String> = emptySet()
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

class PersonDetailViewModel(
    private val peopleRepository: PeopleRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PersonDetailUiState())
    val state: StateFlow<PersonDetailUiState> = _state.asStateFlow()

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
        viewModelScope.launch {
            runCatching { peopleRepository.assets(personId, limit = PAGE_SIZE, offset = 0) }
                .onSuccess { page ->
                    _state.update {
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
                        it.copy(
                            isInitialLoading = false,
                            errorMessage = error.message ?: "Failed to load photos"
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        val personId = snapshot.personId ?: return
        if (snapshot.isAppending || !snapshot.hasMore || snapshot.isInitialLoading) return
        _state.update { it.copy(isAppending = true) }
        viewModelScope.launch {
            runCatching {
                peopleRepository.assets(
                    personId = personId,
                    limit = PAGE_SIZE,
                    offset = snapshot.items.size
                )
            }
                .onSuccess { page ->
                    _state.update { previous ->
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
                        it.copy(
                            isAppending = false,
                            errorMessage = error.message ?: "Failed to load more"
                        )
                    }
                }
        }
    }

    fun toggleSelection(assetId: String) {
        _state.update {
            val next = it.selection.toMutableSet()
            if (!next.add(assetId)) next.remove(assetId)
            it.copy(selection = next)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
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

    fun bulkArchive() = runBulk(
        action = { assetRepository.archive(it) },
        errorFallback = "Failed to archive"
    )

    fun bulkTrash() = runBulk(
        action = { assetRepository.trash(it) },
        errorFallback = "Failed to delete"
    )

    fun bulkAddToAlbum(albumId: String, onSuccess: (List<TimelineItem>) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val items = _state.value.items.filter { it.id in ids }
        _state.update { it.copy(isBulkMutating = true, errorMessage = null) }
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
                            errorMessage = error.message ?: "Failed to add to album"
                        )
                    }
                }
        }
    }

    fun applyRename(newName: String?) {
        _state.update { it.copy(personName = newName?.trim()?.takeIf { n -> n.isNotEmpty() }) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
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
                errorMessage = null,
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
                            errorMessage = error.message ?: "Failed to unlink"
                        )
                    }
                }
        }
    }

    private fun runBulk(
        action: suspend (List<String>) -> Unit,
        errorFallback: String
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previousItems = _state.value.items
        _state.update {
            it.copy(
                isBulkMutating = true,
                errorMessage = null,
                items = it.items.filterNot { item -> item.id in it.selection },
                selection = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching { action(ids) }
                .onSuccess { _state.update { it.copy(isBulkMutating = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            items = previousItems,
                            isBulkMutating = false,
                            errorMessage = error.message ?: errorFallback
                        )
                    }
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 60
    }
}
