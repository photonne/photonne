package com.photonne.app.ui.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.MoveOutcome
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.YearGroup
import com.photonne.app.data.organize.OrganizeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class OrganizeInboxUiState(
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
    /** Current selection grouped by capture year (with ids), for the "se repartirán
     *  en…" chips under the move picker and the "Revisar" grid. */
    val moveYearGroups: List<YearGroup> = emptyList(),
    /** Set after a year-organized move so the UI can show a "repartidas en…"
     *  summary; null when the last move was flat (or none yet). */
    val lastMoveSummary: MoveOutcome? = null,
) {
    /** Pending count = the loaded items; the whole inbox is materialized here as
     *  the user pages, and the header reflects what's visible. The authoritative
     *  badge on the entry point comes from the cheap /count endpoint. */
    val isEmpty: Boolean get() = loaded && items.isEmpty() && !isInitialLoading
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

/**
 * Drives the "Para organizar" inbox screen: a paged grid of the assets still
 * under MobileBackup, with multi-select and a move-out action that files them
 * into a folder (which removes them from the inbox).
 */
class OrganizeInboxViewModel(
    private val repository: OrganizeRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(OrganizeInboxUiState())
    val state: StateFlow<OrganizeInboxUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        val snapshot = _state.value
        if (snapshot.loaded || snapshot.isInitialLoading) return
        refresh()
    }

    fun refresh() {
        _state.update {
            it.copy(
                isRefreshing = it.loaded,
                isInitialLoading = !it.loaded,
                error = null
            )
        }
        viewModelScope.launch {
            runCatching { repository.inbox(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        val remaining = it.selection.intersect(page.items.mapTo(HashSet()) { item -> item.id })
                        it.copy(
                            items = page.items,
                            selection = remaining,
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
                            error = errorFactory.from(error, "No se pudo cargar la bandeja")
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
            runCatching { repository.inbox(cursor = cursor) }
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
        _state.update { previous ->
            val next = previous.selection.toMutableSet()
            if (!next.add(assetId)) next.remove(assetId)
            previous.copy(selection = next)
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

    /**
     * Files the selected assets into [targetFolderId] (physical move out of
     * MobileBackup). On success the moved items are dropped from the inbox and
     * [onComplete] receives the moved ids so callers can refresh the entry-point
     * count.
     */
    /** Loads the current selection grouped by capture year so the move picker can
     *  show "se repartirán en…" and the "Revisar" grid while "Organizar por año"
     *  is checked. Server-computed (from CapturedAt) so it matches the real move. */
    fun loadMoveYearBreakdown() {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty()) {
            _state.update { it.copy(moveYearGroups = emptyList()) }
            return
        }
        viewModelScope.launch {
            runCatching { repository.yearBreakdown(ids) }
                .onSuccess { groups -> _state.update { it.copy(moveYearGroups = groups) } }
                // A failed preview shouldn't block the move; just show nothing.
                .onFailure { _state.update { it.copy(moveYearGroups = emptyList()) } }
        }
    }

    fun clearMoveSummary() {
        _state.update { it.copy(lastMoveSummary = null) }
    }

    fun moveSelectedAssets(
        targetFolderId: String,
        organizeByYear: Boolean = false,
        onComplete: (List<String>) -> Unit = {}
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.moveAssets(targetFolderId, ids, organizeByYear) }
                .onSuccess { outcome ->
                    val moved = ids.toHashSet()
                    _state.update {
                        it.copy(
                            items = it.items.filterNot { item -> item.id in moved },
                            selection = emptySet(),
                            isBulkMutating = false,
                            moveYearGroups = emptyList(),
                            lastMoveSummary = outcome.takeIf { o -> o.yearBreakdown.isNotEmpty() },
                        )
                    }
                    onComplete(ids)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudieron mover")
                        )
                    }
                }
        }
    }

    /** Adds the selection to an album. The assets stay in the inbox (adding to an
     *  album doesn't file them out of MobileBackup); we just clear the selection.
     *  [onComplete] receives the added items for local album-count fan-out. */
    fun bulkAddToAlbum(albumId: String, onComplete: (List<TimelineItem>) -> Unit = {}) {
        val items = _state.value.items.filter { it.id in _state.value.selection }
        val ids = items.map { it.id }
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
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
                            error = errorFactory.from(error, "No se pudo añadir al álbum")
                        )
                    }
                }
        }
    }

    fun bulkArchive() {
        bulkRemoveLocally("No se pudieron archivar") { assetRepository.archive(it) }
    }

    fun bulkTrash() {
        bulkRemoveLocally("No se pudieron eliminar") { assetRepository.trash(it) }
    }

    /**
     * Archive/trash also file an asset out of the inbox (it's no longer pending),
     * so on success they drop the moved ids locally — same shape as the move.
     */
    private fun bulkRemoveLocally(fallbackMessage: String, action: suspend (List<String>) -> Unit) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { action(ids) }
                .onSuccess {
                    val moved = ids.toHashSet()
                    _state.update {
                        it.copy(
                            items = it.items.filterNot { item -> item.id in moved },
                            selection = emptySet(),
                            isBulkMutating = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, fallbackMessage)
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
