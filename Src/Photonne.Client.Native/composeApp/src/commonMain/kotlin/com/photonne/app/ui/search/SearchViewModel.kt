package com.photonne.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val selectedPersonIds: Set<String> = emptySet(),
    val selectedObjectLabels: Set<String> = emptySet(),
    val selectedSceneLabels: Set<String> = emptySet(),
    val results: List<TimelineItem> = emptyList(),
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val facetsLoaded: Boolean = false,
    val facetsLoading: Boolean = false,
    val objectLabels: List<ObjectLabel> = emptyList(),
    val sceneLabels: List<SceneLabel> = emptyList(),
    val people: List<Person> = emptyList()
) {
    val activeFilterCount: Int
        get() = listOf(
            from != null || to != null,
            selectedPersonIds.isNotEmpty(),
            selectedObjectLabels.isNotEmpty(),
            selectedSceneLabels.isNotEmpty()
        ).count { it }

    val hasAnyCriteria: Boolean
        get() = query.isNotBlank() || activeFilterCount > 0
}

class SearchViewModel(
    private val repository: SearchRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var pendingSearch: Job? = null

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        scheduleSearch()
    }

    fun setMode(mode: SearchMode) {
        if (mode == _state.value.mode) return
        _state.update {
            it.copy(
                mode = mode,
                results = emptyList(),
                hasMore = false,
                errorMessage = null
            )
        }
        scheduleSearch()
    }

    fun setDateRange(from: LocalDate?, to: LocalDate?) {
        _state.update { it.copy(from = from, to = to) }
        scheduleSearch(immediate = true)
    }

    fun toggleObjectLabel(label: String) {
        _state.update {
            val next = it.selectedObjectLabels.toMutableSet()
            if (!next.add(label)) next.remove(label)
            it.copy(selectedObjectLabels = next)
        }
        scheduleSearch(immediate = true)
    }

    fun toggleSceneLabel(label: String) {
        _state.update {
            val next = it.selectedSceneLabels.toMutableSet()
            if (!next.add(label)) next.remove(label)
            it.copy(selectedSceneLabels = next)
        }
        scheduleSearch(immediate = true)
    }

    fun togglePerson(personId: String) {
        _state.update {
            val next = it.selectedPersonIds.toMutableSet()
            if (!next.add(personId)) next.remove(personId)
            it.copy(selectedPersonIds = next)
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
            previous.copy(results = previous.results.filterNot { it.id == assetId })
        }
    }

    fun clearAll() {
        pendingSearch?.cancel()
        _state.update {
            it.copy(
                query = "",
                from = null,
                to = null,
                selectedObjectLabels = emptySet(),
                selectedSceneLabels = emptySet(),
                selectedPersonIds = emptySet(),
                results = emptyList(),
                hasMore = false,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
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
            _state.update { it.copy(results = emptyList(), hasMore = false, isLoading = false) }
            return
        }
        pendingSearch = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MILLIS)
            runSearch()
        }
    }

    private suspend fun runSearch() {
        val snapshot = _state.value
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching {
            when (snapshot.mode) {
                SearchMode.Text -> repository.textSearch(
                    query = snapshot.query.takeIf { it.isNotBlank() },
                    from = snapshot.from,
                    to = snapshot.to,
                    personIds = snapshot.selectedPersonIds.toList(),
                    objectLabels = snapshot.selectedObjectLabels.toList(),
                    sceneLabels = snapshot.selectedSceneLabels.toList()
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
                        hasMore = hasMore
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

    companion object {
        private const val DEBOUNCE_MILLIS = 350L
    }
}
