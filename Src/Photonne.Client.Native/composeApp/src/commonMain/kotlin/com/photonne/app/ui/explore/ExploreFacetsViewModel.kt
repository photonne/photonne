package com.photonne.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.data.search.SearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreFacetsUiState(
    val objects: List<ObjectLabel> = emptyList(),
    val scenes: List<SceneLabel> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val attempted: Boolean = false,
    /** Buscador de cada rejilla: filtra en el servidor (`q`), así que llega
     *  más allá de las 200 primeras etiquetas. */
    val objectsQuery: String = "",
    val scenesQuery: String = "",
    /** Resultado de la búsqueda; null sin texto (se enseñan las primeras). */
    val objectResults: List<ObjectLabel>? = null,
    val sceneResults: List<SceneLabel>? = null,
    val objectsSearching: Boolean = false,
    val scenesSearching: Boolean = false,
)

class ExploreFacetsViewModel(
    private val repository: SearchRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreFacetsUiState())
    val state: StateFlow<ExploreFacetsUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        val current = _state.value
        // Un intento FALLIDO no cuenta como cargado: si no, el error quedaba
        // clavado hasta reiniciar la app aunque se volviera a entrar.
        if (current.isLoading || (current.attempted && current.error == null)) return
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val objects = repository.objectLabels(limit = 200)
                val scenes = repository.sceneLabels(limit = 200)
                objects to scenes
            }
                .onSuccess { (objects, scenes) ->
                    // La búsqueda en curso se conserva: refrescar no borra el
                    // texto del buscador.
                    _state.update {
                        it.copy(
                            objects = objects,
                            scenes = scenes,
                            isLoading = false,
                            error = null,
                            attempted = true
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            attempted = true,
                            error = errorFactory.from(error, "No se pudieron cargar las etiquetas")
                        )
                    }
                }
        }
    }

    private var objectsSearchJob: Job? = null
    private var scenesSearchJob: Job? = null

    fun setObjectsQuery(text: String) {
        _state.update { it.copy(objectsQuery = text) }
        objectsSearchJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(objectResults = null, objectsSearching = false) }
            return
        }
        objectsSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(objectsSearching = true) }
            val found = runCatching { repository.objectLabels(limit = 200, search = trimmed) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrDefault(emptyList())
            _state.update { it.copy(objectResults = found, objectsSearching = false) }
        }
    }

    fun setScenesQuery(text: String) {
        _state.update { it.copy(scenesQuery = text) }
        scenesSearchJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(sceneResults = null, scenesSearching = false) }
            return
        }
        scenesSearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(scenesSearching = true) }
            val found = runCatching { repository.sceneLabels(limit = 200, search = trimmed) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrDefault(emptyList())
            _state.update { it.copy(sceneResults = found, scenesSearching = false) }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}
