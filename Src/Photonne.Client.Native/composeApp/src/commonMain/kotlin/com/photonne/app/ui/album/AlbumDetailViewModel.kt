package com.photonne.app.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class AlbumDetailUiState(
    val albumId: String? = null,
    val albumName: String? = null,
    val albumDescription: String? = null,
    val items: List<TimelineItem> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
    val selection: Set<String> = emptySet(),
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

class AlbumDetailViewModel(
    private val repository: AlbumsRepository,
    private val assetRepository: AssetDetailRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AlbumDetailUiState())
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    fun open(albumId: String, name: String, description: String? = null) {
        if (_state.value.albumId == albumId && _state.value.items.isNotEmpty()) {
            _state.update { it.copy(albumName = name, albumDescription = description ?: it.albumDescription) }
            return
        }
        _state.value = AlbumDetailUiState(
            albumId = albumId,
            albumName = name,
            albumDescription = description,
            isLoading = true
        )
        viewModelScope.launch {
            runCatching { repository.assets(albumId) }
                .onSuccess { items ->
                    _state.update { it.copy(items = items, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Failed to load album")
                        )
                    }
                }
        }
    }

    /**
     * Re-fetch the open album's assets and metadata, bypassing [open]'s
     * already-loaded short-circuit. Backs pull-to-refresh so assets added by
     * other members (and edited name/description) show up without leaving the
     * album.
     */
    fun refresh() {
        val albumId = _state.value.albumId ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                supervisorScope {
                    val assets = async { repository.assets(albumId) }
                    val details = async {
                        runCatching { repository.get(albumId) }.getOrNull()
                    }
                    assets.await() to details.await()
                }
            }
                .onSuccess { (items, details) ->
                    _state.update {
                        it.copy(
                            items = items,
                            albumName = details?.name ?: it.albumName,
                            albumDescription = details?.description ?: it.albumDescription,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "Failed to load album")
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

    fun rename(name: String, description: String?, onSuccess: (AlbumSummary) -> Unit = {}) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.update(albumId, name.trim(), description?.trim()?.takeIf { it.isNotEmpty() })
            }
                .onSuccess { album ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            albumName = album.name,
                            albumDescription = album.description
                        )
                    }
                    onSuccess(album)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to rename album")
                        )
                    }
                }
        }
    }

    fun delete(onDeleted: (String) -> Unit) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.delete(albumId) }
                .onSuccess {
                    _state.value = AlbumDetailUiState()
                    onDeleted(albumId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to delete album")
                        )
                    }
                }
        }
    }

    /** Removes the asset from the local cache without hitting the API. */
    fun applyAssetRemovedLocal(assetId: String) {
        _state.update { previous ->
            previous.copy(items = previous.items.filterNot { it.id == assetId })
        }
    }

    fun applyAssetAdded(albumId: String, item: TimelineItem) {
        _state.update { previous ->
            if (previous.albumId != albumId) return@update previous
            if (previous.items.any { it.id == item.id }) return@update previous
            previous.copy(items = previous.items + item)
        }
    }

    fun applyAssetsAdded(albumId: String, items: List<TimelineItem>) {
        if (items.isEmpty()) return
        _state.update { previous ->
            if (previous.albumId != albumId) return@update previous
            val existingIds = previous.items.mapTo(HashSet()) { it.id }
            val toAppend = items.filter { it.id !in existingIds }
            if (toAppend.isEmpty()) previous else previous.copy(items = previous.items + toAppend)
        }
    }

    fun removeAsset(assetId: String, onSuccess: (assetId: String) -> Unit = {}) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        val previous = _state.value.items
        _state.update {
            it.copy(
                isMutating = true,
                error = null,
                items = it.items.filterNot { item -> item.id == assetId }
            )
        }
        viewModelScope.launch {
            runCatching { repository.removeAsset(albumId, assetId) }
                .onSuccess {
                    _state.update { it.copy(isMutating = false) }
                    onSuccess(assetId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            items = previous,
                            error = errorFactory.from(error, "Failed to remove asset")
                        )
                    }
                }
        }
    }

    fun setCover(assetId: String, onSuccess: (AlbumSummary) -> Unit = {}) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.setCover(albumId, assetId) }
                .onSuccess { album ->
                    _state.update { it.copy(isMutating = false) }
                    onSuccess(album)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to set album cover")
                        )
                    }
                }
        }
    }

    fun leave(onLeft: (String) -> Unit) {
        val albumId = _state.value.albumId ?: return
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.leave(albumId) }
                .onSuccess {
                    _state.value = AlbumDetailUiState()
                    onLeft(albumId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "Failed to leave album")
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

    fun toggleSelectAll() {
        _state.update { previous ->
            val all = previous.items.mapTo(HashSet()) { it.id }
            previous.copy(selection = if (previous.selection == all) emptySet() else all)
        }
    }

    fun bulkArchive() = runAssetBulk(
        action = { assetRepository.archive(it) },
        errorFallback = "Failed to archive"
    )

    fun bulkTrash() = runAssetBulk(
        action = { assetRepository.trash(it) },
        errorFallback = "Failed to delete"
    )

    fun bulkAddToAlbum(albumId: String, onSuccess: (List<TimelineItem>) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val added = _state.value.items.filter { it.id in ids }
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.addAssetsBatch(albumId, ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false, selection = emptySet()) }
                    onSuccess(added)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "Failed to add to album")
                        )
                    }
                }
        }
    }

    /**
     * Detach every selected asset from this album. The repo only exposes a
     * per-asset endpoint, so we fan out one call per id and surface the
     * count we managed to remove for the album-list badge to stay correct.
     */
    fun bulkRemoveFromAlbum(onSuccess: (removed: Int) -> Unit = {}) {
        val albumId = _state.value.albumId ?: return
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
            runCatching {
                for (id in ids) repository.removeAsset(albumId, id)
            }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false) }
                    onSuccess(ids.size)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            items = previousItems,
                            isBulkMutating = false,
                            error = errorFactory.from(error, "Failed to remove from album")
                        )
                    }
                }
        }
    }

    private fun runAssetBulk(
        action: suspend (List<String>) -> Unit,
        errorFallback: String
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
                .onSuccess { _state.update { it.copy(isBulkMutating = false) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            items = previousItems,
                            isBulkMutating = false,
                            error = errorFactory.from(error, errorFallback)
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

}
