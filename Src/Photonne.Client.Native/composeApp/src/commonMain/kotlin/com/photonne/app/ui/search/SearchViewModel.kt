package com.photonne.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.Person
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.search.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class SearchMode { Text, Semantic }

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.Text,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val ocrText: String = "",
    val selectedPersonIds: Set<String> = emptySet(),
    val selectedObjectLabels: Set<String> = emptySet(),
    val selectedSceneLabels: Set<String> = emptySet(),
    val results: List<TimelineItem> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val errorMessage: String? = null,
    val facetsLoaded: Boolean = false,
    val facetsLoading: Boolean = false,
    val objectLabels: List<ObjectLabel> = emptyList(),
    val sceneLabels: List<SceneLabel> = emptyList(),
    val people: List<Person> = emptyList(),
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false
) {
    val activeFilterCount: Int
        get() = listOf(
            from != null || to != null,
            selectedPersonIds.isNotEmpty(),
            selectedObjectLabels.isNotEmpty(),
            selectedSceneLabels.isNotEmpty(),
            ocrText.isNotBlank()
        ).count { it }

    val hasAnyCriteria: Boolean
        get() = query.isNotBlank() || activeFilterCount > 0

    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

class SearchViewModel(
    private val repository: SearchRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var pendingSearch: Job? = null

    fun setQuery(value: String) {
        _state.update { it.copy(query = value, selection = emptySet()) }
        scheduleSearch()
    }

    fun setOcrText(value: String) {
        _state.update { it.copy(ocrText = value, selection = emptySet()) }
        scheduleSearch()
    }

    fun setMode(mode: SearchMode) {
        if (mode == _state.value.mode) return
        _state.update {
            it.copy(
                mode = mode,
                results = emptyList(),
                hasMore = false,
                errorMessage = null,
                selection = emptySet()
            )
        }
        scheduleSearch()
    }

    fun setDateRange(from: LocalDate?, to: LocalDate?) {
        _state.update { it.copy(from = from, to = to, selection = emptySet()) }
        scheduleSearch(immediate = true)
    }

    fun toggleObjectLabel(label: String) {
        _state.update {
            val next = it.selectedObjectLabels.toMutableSet()
            if (!next.add(label)) next.remove(label)
            it.copy(selectedObjectLabels = next, selection = emptySet())
        }
        scheduleSearch(immediate = true)
    }

    fun toggleSceneLabel(label: String) {
        _state.update {
            val next = it.selectedSceneLabels.toMutableSet()
            if (!next.add(label)) next.remove(label)
            it.copy(selectedSceneLabels = next, selection = emptySet())
        }
        scheduleSearch(immediate = true)
    }

    fun togglePerson(personId: String) {
        _state.update {
            val next = it.selectedPersonIds.toMutableSet()
            if (!next.add(personId)) next.remove(personId)
            it.copy(selectedPersonIds = next, selection = emptySet())
        }
        scheduleSearch(immediate = true)
    }

    fun setFavorite(assetId: String, isFavorite: Boolean) {
        _state.update { previous ->
            previous.copy(
                results = previous.results.map { item ->
                    if (item.id == assetId) item.copy(isFavorite = isFavorite) else item
                }
            )
        }
    }

    fun removeItem(assetId: String) {
        _state.update { previous ->
            previous.copy(
                results = previous.results.filterNot { it.id == assetId },
                selection = previous.selection - assetId
            )
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

    fun bulkArchive() {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previous = _state.value.results
        _state.update {
            it.copy(
                isBulkMutating = true,
                errorMessage = null,
                results = it.results.filterNot { item -> item.id in it.selection },
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
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previous = _state.value.results
        _state.update {
            it.copy(
                isBulkMutating = true,
                errorMessage = null,
                results = it.results.filterNot { item -> item.id in it.selection },
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
        val state = _state.value
        val items = state.results.filter { it.id in state.selection }
        val ids = items.map { it.id }
        if (ids.isEmpty() || state.isBulkMutating) return
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

    fun clearAll() {
        pendingSearch?.cancel()
        _state.update {
            it.copy(
                query = "",
                ocrText = "",
                from = null,
                to = null,
                selectedObjectLabels = emptySet(),
                selectedSceneLabels = emptySet(),
                selectedPersonIds = emptySet(),
                results = emptyList(),
                hasMore = false,
                errorMessage = null,
                selection = emptySet()
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.isAppending || !snapshot.hasMore ||
            snapshot.mode != SearchMode.Text) return
        _state.update { it.copy(isAppending = true) }
        viewModelScope.launch {
            runCatching {
                repository.textSearch(
                    query = snapshot.query.takeIf { it.isNotBlank() },
                    from = snapshot.from,
                    to = snapshot.to,
                    personIds = snapshot.selectedPersonIds.toList(),
                    objectLabels = snapshot.selectedObjectLabels.toList(),
                    sceneLabels = snapshot.selectedSceneLabels.toList(),
                    ocrText = snapshot.ocrText.takeIf { it.isNotBlank() },
                    offset = snapshot.results.size
                )
            }
                .onSuccess { page ->
                    _state.update {
                        // Snapshot may have changed if a new search kicked in
                        // between the request firing and the response landing;
                        // bail out unless we're still appending to the same
                        // result set.
                        if (it.isAppending) {
                            val existingIds = it.results.mapTo(HashSet()) { item -> item.id }
                            val appended = page.items.filter { item -> item.id !in existingIds }
                            it.copy(
                                results = it.results + appended,
                                hasMore = page.hasMore,
                                isAppending = false
                            )
                        } else it
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isAppending = false,
                            errorMessage = error.message ?: "Search failed"
                        )
                    }
                }
        }
    }

    fun ensureFacetsLoaded() {
        val current = _state.value
        if (current.facetsLoaded || current.facetsLoading) return
        _state.update { it.copy(facetsLoading = true) }
        viewModelScope.launch {
            runCatching {
                Triple(
                    repository.objectLabels(),
                    repository.sceneLabels(),
                    repository.people()
                )
            }
                .onSuccess { (objects, scenes, people) ->
                    _state.update {
                        it.copy(
                            facetsLoading = false,
                            facetsLoaded = true,
                            objectLabels = objects,
                            sceneLabels = scenes,
                            people = people
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            facetsLoading = false,
                            errorMessage = error.message ?: "Failed to load filters"
                        )
                    }
                }
        }
    }

    private fun scheduleSearch(immediate: Boolean = false) {
        pendingSearch?.cancel()
        val snapshot = _state.value
        if (!snapshot.hasAnyCriteria) {
            _state.update {
                it.copy(
                    results = emptyList(),
                    hasMore = false,
                    isLoading = false,
                    isAppending = false
                )
            }
            return
        }
        pendingSearch = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MILLIS)
            runSearch()
        }
    }

    private suspend fun runSearch() {
        val snapshot = _state.value
        _state.update { it.copy(isLoading = true, isAppending = false, errorMessage = null) }
        runCatching {
            when (snapshot.mode) {
                SearchMode.Text -> repository.textSearch(
                    query = snapshot.query.takeIf { it.isNotBlank() },
                    from = snapshot.from,
                    to = snapshot.to,
                    personIds = snapshot.selectedPersonIds.toList(),
                    objectLabels = snapshot.selectedObjectLabels.toList(),
                    sceneLabels = snapshot.selectedSceneLabels.toList(),
                    ocrText = snapshot.ocrText.takeIf { it.isNotBlank() }
                ).let { it.items to it.hasMore }
                SearchMode.Semantic -> {
                    if (snapshot.query.isBlank()) {
                        emptyList<TimelineItem>() to false
                    } else {
                        val semantic = repository.semanticSearch(snapshot.query.trim())
                        semantic.items.map { it.asset } to false
                    }
                }
            }
        }
            .onSuccess { (items, hasMore) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        results = items,
                        hasMore = hasMore,
                        selection = emptySet()
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Search failed"
                    )
                }
            }
    }

    private fun revertBulk(previousItems: List<TimelineItem>, message: String) {
        _state.update {
            it.copy(
                results = previousItems,
                isBulkMutating = false,
                errorMessage = message
            )
        }
    }

    companion object {
        private const val DEBOUNCE_MILLIS = 350L
    }
}
