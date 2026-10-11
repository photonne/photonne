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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.photonne.app.ui.util.sortedByNatural
import com.russhwolf.settings.Settings
import com.photonne.app.util.suspendRunCatching

/** Orden de las rejillas de Escenas y Objetos (y de sus filas en Colecciones). */
enum class LabelSort { Count, Name }

/**
 * Ordena etiquetas: por número de fotos (lo primero, lo que más sale) o por
 * nombre en orden natural. El servidor ya las manda por número; reordenar aquí
 * vale para las 200 cargadas o el resultado de la búsqueda.
 */
fun <T> List<T>.sortedLabels(sort: LabelSort, label: (T) -> String, count: (T) -> Int): List<T> =
    when (sort) {
        LabelSort.Count -> sortedByDescending(count)
        LabelSort.Name -> sortedByNatural { label(it).lowercase() }
    }

data class ExploreFacetsUiState(
    val objects: List<ObjectLabel> = emptyList(),
    val scenes: List<SceneLabel> = emptyList(),
    val isLoading: Boolean = false,
    /** Objetos y escenas cargan por separado: el fallo de uno no tapa al otro. */
    val objectsError: UiError? = null,
    val scenesError: UiError? = null,
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
    val scenesSort: LabelSort = LabelSort.Count,
    val objectsSort: LabelSort = LabelSort.Count,
) {
    /** Lo que pinta la rejilla de Escenas: búsqueda o las primeras, en su orden. */
    val visibleScenes: List<SceneLabel>
        get() = (sceneResults ?: scenes).sortedLabels(scenesSort, { it.label }, { it.assetCount })

    val visibleObjects: List<ObjectLabel>
        get() = (objectResults ?: objects).sortedLabels(objectsSort, { it.label }, { it.assetCount })

    /** Las filas de Colecciones: sin búsqueda, en el orden de su página. */
    val scenesInPageOrder: List<SceneLabel>
        get() = scenes.sortedLabels(scenesSort, { it.label }, { it.assetCount })

    val objectsInPageOrder: List<ObjectLabel>
        get() = objects.sortedLabels(objectsSort, { it.label }, { it.assetCount })
}

class ExploreFacetsViewModel(
    private val repository: SearchRepository,
    private val errorFactory: UiErrorFactory,
    private val settings: Settings,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ExploreFacetsUiState(
            scenesSort = readSort(SCENES_SORT_KEY),
            objectsSort = readSort(OBJECTS_SORT_KEY),
        )
    )
    val state: StateFlow<ExploreFacetsUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        val current = _state.value
        // Un intento FALLIDO no cuenta como cargado: si no, el error quedaba
        // clavado hasta reiniciar la app aunque se volviera a entrar.
        val failed = current.objectsError != null || current.scenesError != null
        if (current.isLoading || (current.attempted && !failed)) return
        refresh()
    }

    /**
     * Objetos y escenas en paralelo y cada uno con su error: antes iban en un
     * solo bloque y el fallo de uno dejaba sin nada a los dos (y a las filas de
     * Explorar). Lo que falla conserva lo que ya tenía. La búsqueda en curso
     * también se conserva: refrescar no borra el texto del buscador.
     */
    fun refresh() {
        _state.update { it.copy(isLoading = true, objectsError = null, scenesError = null) }
        viewModelScope.launch {
            val objects = async { suspendRunCatching { repository.objectLabels(limit = 200) } }
            val scenes = async { suspendRunCatching { repository.sceneLabels(limit = 200) } }
            val objectsResult = objects.await()
            val scenesResult = scenes.await()
            _state.update {
                it.copy(
                    objects = objectsResult.getOrDefault(it.objects),
                    scenes = scenesResult.getOrDefault(it.scenes),
                    objectsError = objectsResult.exceptionOrNull()?.let { error ->
                        errorFactory.from(error, "No se pudieron cargar los objetos")
                    },
                    scenesError = scenesResult.exceptionOrNull()?.let { error ->
                        errorFactory.from(error, "No se pudieron cargar las escenas")
                    },
                    isLoading = false,
                    attempted = true
                )
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

    fun setScenesSort(sort: LabelSort) {
        settings.putString(SCENES_SORT_KEY, sort.name)
        _state.update { it.copy(scenesSort = sort) }
    }

    fun setObjectsSort(sort: LabelSort) {
        settings.putString(OBJECTS_SORT_KEY, sort.name)
        _state.update { it.copy(objectsSort = sort) }
    }

    private fun readSort(key: String): LabelSort =
        settings.getStringOrNull(key)
            ?.let { raw -> LabelSort.entries.firstOrNull { it.name == raw } }
            ?: LabelSort.Count

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val SCENES_SORT_KEY = "photonne.explore.scenes.sort"
        const val OBJECTS_SORT_KEY = "photonne.explore.objects.sort"
    }
}
