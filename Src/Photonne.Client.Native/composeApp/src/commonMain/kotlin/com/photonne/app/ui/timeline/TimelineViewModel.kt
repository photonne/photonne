package com.photonne.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.folder.FoldersRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.models.TimelineYearSummary
import com.photonne.app.data.timeline.TimelineBucketState
import com.photonne.app.data.timeline.TimelineBucketStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineUiState(
    /** Skeleton + loaded contents, newest first — see TimelineBucketStore. */
    val buckets: List<TimelineBucketState> = emptyList(),
    /** Compressed yearly view; null until the Year zoom level requests it. */
    val yearSummaries: List<TimelineYearSummary>? = null,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiError? = null,
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false,
) {
    /** Flat view of every loaded bucket's items, display order. */
    val loadedItems: List<TimelineItem> get() = buckets.flatMap { it.items.orEmpty() }
    val isEmpty: Boolean get() = !isInitialLoading && buckets.none { it.count > 0 } && error == null
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

class TimelineViewModel(
    private val store: TimelineBucketStore,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val foldersRepository: FoldersRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            store.buckets.collect { buckets ->
                _state.update { it.copy(buckets = buckets) }
            }
        }
        viewModelScope.launch {
            store.yearSummaries.collect { summaries ->
                _state.update { it.copy(yearSummaries = summaries) }
            }
        }
        refresh()
    }

    /** Re-fetches the skeleton; visible buckets reload via [ensureVisible]. */
    fun refresh() {
        refreshJob?.cancel()
        _state.update {
            it.copy(
                isRefreshing = it.buckets.isNotEmpty(),
                isInitialLoading = it.buckets.isEmpty(),
                error = null
            )
        }
        refreshJob = viewModelScope.launch {
            runCatching { store.refresh() }
                .onSuccess {
                    _state.update { it.copy(isInitialLoading = false, isRefreshing = false) }
                }
                .onFailure { error ->
                    // A refresh() that supersedes an in-flight one (e.g. the
                    // effective URL flipping LAN↔público) cancels this job. That
                    // cancellation is not a load error — swallowing it here would
                    // strand a spurious banner over the successful reload — so
                    // honour cooperative cancellation and rethrow.
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = errorFactory.from(error, "Error al cargar el timeline")
                        )
                    }
                }
        }
    }

    /**
     * Loads the given buckets' contents (the grid reports which keys are on
     * or near the viewport). Already-loaded and in-flight keys are no-ops
     * inside the store, so this is safe to call on every scroll frame.
     */
    fun ensureVisible(bucketKeys: List<String>) {
        if (bucketKeys.isEmpty()) return
        viewModelScope.launch {
            runCatching { store.ensureLoaded(bucketKeys) }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "Error al cargar el timeline"))
                    }
                }
        }
    }

    /**
     * Fetches the compressed yearly view with at least [sample] items per
     * year — called by the Year zoom level with rows×columns. The store
     * caches and dedups, so repeated calls are cheap.
     */
    fun ensureYearSummaries(sample: Int) {
        viewModelScope.launch {
            runCatching { store.ensureYearSummaries(sample) }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "Error al cargar el timeline"))
                    }
                }
        }
    }

    /** Removes the given asset id from the local timeline state without
     *  hitting the API. Used by the AssetDetail viewer after a single-asset
     *  trash/archive so the grid updates immediately. */
    fun removeItemLocal(assetId: String) {
        _state.update { it.copy(selection = it.selection - assetId) }
        viewModelScope.launch { store.removeItem(assetId) }
    }

    fun setFavorite(assetId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            store.updateItem(assetId) { it.copy(isFavorite = isFavorite) }
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

    /**
     * Selects every LOADED item (or clears, when they're all selected
     * already). Unloaded buckets can't be selected — there's nothing local
     * to select yet — which keeps "select all" honest about what it acts on.
     */
    fun toggleSelectAll() {
        _state.update { previous ->
            val all = previous.loadedItems.mapTo(HashSet()) { it.id }
            previous.copy(selection = if (previous.selection == all) emptySet() else all)
        }
    }

    fun selectedItems(): List<TimelineItem> {
        val state = _state.value
        return state.loadedItems.filter { it.id in state.selection }
    }

    private fun selectedIds(): List<String> = _state.value.selection.toList()

    fun bulkArchive() {
        bulkRemove(fallbackMessage = "Failed to archive") { assetRepository.archive(it) }
    }

    fun bulkTrash() {
        bulkRemove(fallbackMessage = "Failed to delete") { assetRepository.trash(it) }
    }

    /**
     * Shared shape of archive/trash: optimistically drop the items from
     * their buckets, call the API, and on failure re-sync from the server —
     * the store has no snapshot to roll back to, and a refresh both reverts
     * the optimistic removal and reflects any partial server success.
     */
    private fun bulkRemove(fallbackMessage: String, action: suspend (List<String>) -> Unit) {
        val ids = selectedIds()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null, selection = emptySet()) }
        viewModelScope.launch {
            store.removeItems(ids)
            runCatching { action(ids) }
                .onSuccess { _state.update { it.copy(isBulkMutating = false) } }
                .onFailure { error ->
                    runCatching { store.refresh() }
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, fallbackMessage)
                        )
                    }
                }
        }
    }

    fun bulkAddToAlbum(albumId: String, onComplete: (List<TimelineItem>) -> Unit = {}) {
        val items = selectedItems()
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
                            error = errorFactory.from(error, "Failed to add to album")
                        )
                    }
                }
        }
    }

    /**
     * Moves the selected assets into [targetFolderId] (physical relocation via
     * the folder move endpoint). Source folders differ per asset — MobileBackup
     * has one subfolder per device — so we pass a null source and let the server
     * check write permission per asset. On success we re-sync from the server:
     * moved items stay on the timeline if the destination is timeline-visible
     * and leave it if it's an excluded/shared folder, and only a refresh knows
     * which. [onComplete] receives the moved ids so callers can refresh the
     * "Para organizar" count.
     */
    fun moveSelectedAssets(targetFolderId: String, onComplete: (List<String>) -> Unit = {}) {
        val ids = selectedIds()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching {
                foldersRepository.moveAssets(
                    sourceFolderId = null,
                    targetFolderId = targetFolderId,
                    assetIds = ids
                )
            }
                .onSuccess {
                    runCatching { store.refresh() }
                    _state.update { it.copy(isBulkMutating = false, selection = emptySet()) }
                    onComplete(ids)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "Failed to move")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
