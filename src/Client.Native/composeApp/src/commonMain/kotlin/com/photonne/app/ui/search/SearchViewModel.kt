package com.photonne.app.ui.search

import com.photonne.app.data.models.PlaceFacet
import com.photonne.app.data.search.RecentSearchStore
import com.photonne.app.data.search.RecentPinned
import com.photonne.app.data.search.RecentSearch
import com.photonne.app.data.search.PinnedSearchFilter
import com.photonne.app.ui.util.withRestored
import androidx.lifecycle.ViewModel
import com.photonne.app.ui.util.LoadedPage
import com.photonne.app.ui.util.reloadLoadedPages
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.api.serverCode
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.ErrorMessages
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.Person
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.search.SearchRepository
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.applying
import com.photonne.app.ui.selection.toggled
import com.photonne.app.ui.selection.toggledAll
import com.photonne.app.ui.selection.withSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import com.photonne.app.data.events.AssetMutation
import com.photonne.app.data.events.AssetMutationBus
import com.photonne.app.util.suspendRunCatching

enum class SearchMode { Text, Semantic }

/** Solo fotos o solo vídeos; [wire] es el `type` de /api/assets/search. */
enum class SearchMediaType(val wire: String) { Image("image"), Video("video") }

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.Text,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val ocrText: String = "",
    val mediaType: SearchMediaType? = null,
    val selectedPersonIds: Set<String> = emptySet(),
    val selectedObjectLabels: Set<String> = emptySet(),
    val selectedSceneLabels: Set<String> = emptySet(),
    /** Places of the user's photos for the start screen (empty on old servers). */
    val places: List<PlaceFacet> = emptyList(),
    /** A tag, camera or place tapped in the viewer; see [PinnedSearchFilter]. */
    val pinned: PinnedSearchFilter? = null,
    val results: List<TimelineItem> = emptyList(),
    val hasMore: Boolean = false,
    /** La semántica no pagina: true si la respuesta llenó el tope, es decir,
     *  hay más parecidas que no se muestran. */
    val semanticCapped: Boolean = false,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val error: UiError? = null,
    /** La página siguiente falló: pie con reintento bajo los resultados. Va
     *  aparte de [error] para no colarse en el diálogo «Añadir a álbum». */
    val appendError: UiError? = null,
    val facetsLoaded: Boolean = false,
    val facetsLoading: Boolean = false,
    val objectLabels: List<ObjectLabel> = emptyList(),
    val sceneLabels: List<SceneLabel> = emptyList(),
    val people: List<Person> = emptyList(),
    /** Buscador de la sección de personas de los filtros (en el servidor). */
    val peopleQuery: String = "",
    /** Resultado de [peopleQuery]; null sin búsqueda (se enseña [people]). */
    val peopleSearchResults: List<Person>? = null,
    val peopleSearching: Boolean = false,
    /** Personas seleccionadas vistas alguna vez, para seguir pintando su chip
     *  (y poder quitarlo) aunque la búsqueda actual no las incluya. */
    val selectedPeopleCache: Map<String, Person> = emptyMap(),
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false,
    /** Aviso de una sola vez: la búsqueda con IA no está disponible en el
     *  servidor y se ha pasado a texto. Lo consume [SearchViewModel.consumeSemanticUnavailable]. */
    val semanticUnavailable: Boolean = false
) {
    val activeFilterCount: Int
        get() = listOf(
            from != null || to != null,
            selectedPersonIds.isNotEmpty(),
            selectedObjectLabels.isNotEmpty(),
            selectedSceneLabels.isNotEmpty(),
            ocrText.isNotBlank(),
            mediaType != null,
            pinned != null
        ).count { it }

    val hasAnyCriteria: Boolean
        get() = query.isNotBlank() || activeFilterCount > 0

    /**
     * Criterios que la modalidad actual usa de verdad: la semántica ignora
     * los filtros, así que con consulta vacía y filtros residuales la
     * pantalla debe enseñar el estado inicial, no "sin resultados".
     */
    val hasCriteriaForMode: Boolean
        get() = if (mode == SearchMode.Semantic) query.isNotBlank() else hasAnyCriteria

    val isSelectionActive: Boolean get() = selection.isNotEmpty()

    /** Chips de personas de la hoja de filtros: primero las seleccionadas que
     *  la lista actual no trae, luego la lista (búsqueda o las primeras). */
    val visiblePeople: List<Person>
        get() {
            val list = peopleSearchResults ?: people
            val listed = list.mapTo(HashSet()) { it.id }
            val pinned = selectedPersonIds.mapNotNull { id ->
                selectedPeopleCache[id]?.takeIf { id !in listed }
            }
            return pinned + list
        }
}

class SearchViewModel(
    private val repository: SearchRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
    mutationBus: AssetMutationBus,
    private val recentSearches: RecentSearchStore,
) : ViewModel() {

    /** The searches run lately, for the start screen's "Recientes". */
    val recent: StateFlow<List<RecentSearch>> = recentSearches.recent

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var pendingSearch: Job? = null

    init {
        // Punto 52: las mutaciones confirmadas por el servidor (archivar,
        // papelera, restaurar, purgar, favorito) llegan por el bus; App.kt ya
        // no parchea esta lista a mano.
        viewModelScope.launch {
            mutationBus.events.collect { event ->
                when (event) {
                    is AssetMutation.Removed -> event.assetIds.forEach(::removeItem)
                    is AssetMutation.Purged -> event.assetIds.forEach(::removeItem)
                    is AssetMutation.Restored, AssetMutation.AllChanged -> refresh()
                    is AssetMutation.FavoriteChanged -> setFavorite(event.assetId, event.isFavorite)
                    // Lote L8: la foto cambia de sitio en el orden por fecha.
                    is AssetMutation.DateChanged -> refresh()
                    // Mover de carpeta no cambia esta lista.
                    is AssetMutation.Moved -> Unit
                }
            }
        }
    }

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
                semanticCapped = false,
                error = null,
                selection = emptySet()
            )
        }
        scheduleSearch()
    }

    fun setMediaType(type: SearchMediaType?) {
        _state.update { it.copy(mediaType = type, selection = emptySet()) }
        scheduleSearch(immediate = true)
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
            val person = (it.peopleSearchResults.orEmpty() + it.people)
                .firstOrNull { p -> p.id == personId }
            it.copy(
                selectedPersonIds = next,
                selectedPeopleCache = if (person != null) {
                    it.selectedPeopleCache + (personId to person)
                } else it.selectedPeopleCache,
                selection = emptySet()
            )
        }
        scheduleSearch(immediate = true)
    }

    fun showResultsForSceneLabel(label: String) =
        showResultsFor(sceneLabels = setOf(label))

    fun showResultsForObjectLabel(label: String) =
        showResultsFor(objectLabels = setOf(label))

    /** A tag, camera or place tapped in the viewer: its photos, on their own. */
    fun showResultsFor(pinned: PinnedSearchFilter) = showResultsFor(pinnedFilter = pinned)

    /** The pinned chip's ✕: the search goes on without it. */
    fun clearPinned() {
        _state.update { it.copy(pinned = null, results = emptyList(), hasMore = false, selection = emptySet()) }
        scheduleSearch(immediate = true)
    }

    /** Persona tocada en la pantalla inicial de la búsqueda. Entra en la caché
     *  para que su chip de filtro tenga nombre. */
    fun showResultsForPerson(person: Person) {
        _state.update { it.copy(selectedPeopleCache = it.selectedPeopleCache + (person.id to person)) }
        showResultsFor(personIds = setOf(person.id))
    }

    /** Sustituye cualquier criterio anterior por uno solo y busca ya. */
    private fun showResultsFor(
        personIds: Set<String> = emptySet(),
        objectLabels: Set<String> = emptySet(),
        sceneLabels: Set<String> = emptySet(),
        pinnedFilter: PinnedSearchFilter? = null
    ) {
        pendingSearch?.cancel()
        _state.update {
            it.copy(
                query = "",
                mode = SearchMode.Text,
                from = null,
                to = null,
                ocrText = "",
                mediaType = null,
                selectedPersonIds = personIds,
                selectedObjectLabels = objectLabels,
                selectedSceneLabels = sceneLabels,
                pinned = pinnedFilter,
                results = emptyList(),
                hasMore = false,
                semanticCapped = false,
                error = null,
                selection = emptySet()
            )
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
            previous.copy(selection = previous.selection.toggledAll(previous.results.map { it.id }))
        }
    }

    fun bulkArchive(onResult: (UiError?) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previous = _state.value.results
        _state.update {
            it.copy(
                isBulkMutating = true,
                error = null,
                results = it.results.filterNot { item -> item.id in it.selection },
                selection = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching { assetRepository.archive(ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false) }
                    onResult(null)
                }
                .onFailure { error ->
                    onResult(revertBulk(previous, ids, error, ErrorMessages.ARCHIVE_FAILED))
                }
        }
    }

    fun bulkTrash(onResult: (UiError?) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        val previous = _state.value.results
        _state.update {
            it.copy(
                isBulkMutating = true,
                error = null,
                results = it.results.filterNot { item -> item.id in it.selection },
                selection = emptySet()
            )
        }
        viewModelScope.launch {
            runCatching { assetRepository.trash(ids) }
                .onSuccess {
                    _state.update { it.copy(isBulkMutating = false) }
                    onResult(null)
                }
                .onFailure { error ->
                    onResult(revertBulk(previous, ids, error, ErrorMessages.TRASH_FAILED))
                }
        }
    }

    fun bulkAddToAlbum(albumId: String, onComplete: (List<TimelineItem>) -> Unit = {}) {
        val state = _state.value
        val items = state.results.filter { it.id in state.selection }
        val ids = items.map { it.id }
        if (ids.isEmpty() || state.isBulkMutating) return
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

    fun clearAll() {
        pendingSearch?.cancel()
        _state.update {
            it.copy(
                query = "",
                ocrText = "",
                mediaType = null,
                from = null,
                to = null,
                selectedObjectLabels = emptySet(),
                selectedSceneLabels = emptySet(),
                selectedPersonIds = emptySet(),
                pinned = null,
                results = emptyList(),
                hasMore = false,
                // A loadMore in flight checks this flag before appending: left
                // set, its page brought the old results back onto the
                // cleared screen.
                isAppending = false,
                semanticCapped = false,
                error = null,
                selection = emptySet()
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun consumeSemanticUnavailable() {
        _state.update { it.copy(semanticUnavailable = false) }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isLoading || snapshot.isAppending || !snapshot.hasMore ||
            snapshot.mode != SearchMode.Text) return
        _state.update { it.copy(isAppending = true, appendError = null) }
        viewModelScope.launch {
            suspendRunCatching {
                repository.textSearch(
                    query = snapshot.query.takeIf { it.isNotBlank() },
                    from = snapshot.from,
                    to = snapshot.to,
                    personIds = snapshot.selectedPersonIds.toList(),
                    objectLabels = snapshot.selectedObjectLabels.toList(),
                    sceneLabels = snapshot.selectedSceneLabels.toList(),
                    ocrText = snapshot.ocrText.takeIf { it.isNotBlank() },
                    mediaType = snapshot.mediaType?.wire,
                    offset = snapshot.results.size,
                    pinned = snapshot.pinned
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
                        // Same guard as above: a new search owns the state now.
                        if (it.isAppending) {
                            it.copy(
                                isAppending = false,
                                appendError = errorFactory.from(error, "No se pudo cargar más")
                            )
                        } else it
                    }
                }
        }
    }

    private var peopleSearchJob: Job? = null

    /**
     * Busca personas en el servidor para la hoja de filtros: la lista inicial
     * solo trae las 80 con más fotos, y sin esto no se podía filtrar por nadie
     * más. Espera una pausa al teclear.
     */
    fun setPeopleQuery(text: String) {
        _state.update { it.copy(peopleQuery = text) }
        peopleSearchJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(peopleSearchResults = null, peopleSearching = false) }
            return
        }
        peopleSearchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(peopleSearching = true) }
            runCatching { repository.people(search = trimmed) }
                .onSuccess { found ->
                    _state.update { it.copy(peopleSearchResults = found, peopleSearching = false) }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.update { it.copy(peopleSearchResults = emptyList(), peopleSearching = false) }
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
                    // The places row is extra: if it fails, the rest still shows.
                    val places = runCatching { repository.places() }.getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            facetsLoading = false,
                            facetsLoaded = true,
                            objectLabels = objects,
                            sceneLabels = scenes,
                            people = people,
                            places = places
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            facetsLoading = false,
                            error = errorFactory.from(error, "No se pudieron cargar los filtros")
                        )
                    }
                }
        }
    }

    /**
     * Repite la búsqueda actual (tras una restauración, un Deshacer…) pidiendo
     * tantos resultados como había cargados, no solo la primera página.
     */
    fun refresh() {
        ensureFacetsLoaded()
        if (_state.value.hasAnyCriteria) {
            scheduleSearch(immediate = true, keepLoaded = _state.value.results.size)
        }
    }

    /** [keepLoaded]: resultados de texto que la nueva búsqueda debe cubrir. */
    private fun scheduleSearch(immediate: Boolean = false, keepLoaded: Int = 0) {
        pendingSearch?.cancel()
        val snapshot = _state.value
        if (!snapshot.hasAnyCriteria) {
            _state.update {
                it.copy(
                    results = emptyList(),
                    hasMore = false,
                    semanticCapped = false,
                    isLoading = false,
                    isAppending = false
                )
            }
            return
        }
        pendingSearch = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MILLIS)
            runSearch(keepLoaded)
            // A search the user stays on joins "Recientes"; the next one
            // cancels this job, so the words typed on the way don't.
            delay(RECENT_DELAY_MILLIS)
            rememberSearch(_state.value)
        }
    }

    private fun rememberSearch(state: SearchUiState) {
        if (!state.hasCriteriaForMode || state.error != null) return
        val semantic = state.mode == SearchMode.Semantic
        val names = state.selectedPersonIds.map { id ->
            (state.people.firstOrNull { it.id == id } ?: state.selectedPeopleCache[id])?.name ?: ""
        }.filter { it.isNotBlank() }
        val label = buildList {
            if (state.query.isNotBlank()) add("«${state.query.trim()}»")
            if (!semantic) {
                addAll(names)
                addAll(state.selectedObjectLabels)
                addAll(state.selectedSceneLabels)
                state.pinned?.let { add(it.label) }
                if (state.ocrText.isNotBlank()) add("“${state.ocrText.trim()}”")
            }
        }.joinToString(" · ")
        recentSearches.add(
            RecentSearch(
                label = label,
                query = state.query.trim(),
                semantic = semantic,
                from = state.from?.toString().takeUnless { semantic },
                to = state.to?.toString().takeUnless { semantic },
                ocrText = if (semantic) "" else state.ocrText.trim(),
                mediaType = state.mediaType?.wire.takeUnless { semantic },
                personIds = if (semantic) emptyList() else state.selectedPersonIds.toList(),
                objectLabels = if (semantic) emptyList() else state.selectedObjectLabels.toList(),
                sceneLabels = if (semantic) emptyList() else state.selectedSceneLabels.toList(),
                pinned = state.pinned?.takeUnless { semantic }?.let(RecentPinned::of)
            )
        )
    }

    /** A recent search tapped on the start screen: run it again as it was. */
    fun applyRecent(entry: RecentSearch) {
        pendingSearch?.cancel()
        _state.update {
            it.copy(
                query = entry.query,
                mode = if (entry.semantic) SearchMode.Semantic else SearchMode.Text,
                from = entry.from?.let { day -> runCatching { LocalDate.parse(day) }.getOrNull() },
                to = entry.to?.let { day -> runCatching { LocalDate.parse(day) }.getOrNull() },
                ocrText = entry.ocrText,
                mediaType = SearchMediaType.entries.firstOrNull { type -> type.wire == entry.mediaType },
                selectedPersonIds = entry.personIds.toSet(),
                selectedObjectLabels = entry.objectLabels.toSet(),
                selectedSceneLabels = entry.sceneLabels.toSet(),
                pinned = entry.pinned?.toFilter(),
                results = emptyList(),
                hasMore = false,
                semanticCapped = false,
                error = null,
                selection = emptySet()
            )
        }
        scheduleSearch(immediate = true)
    }

    fun clearRecent() = recentSearches.clear()

    private suspend fun runSearch(keepLoaded: Int = 0) {
        val snapshot = _state.value
        _state.update {
            it.copy(isLoading = true, isAppending = false, error = null, appendError = null)
        }
        // A search cancelled by a newer one must not report an error nor wipe
        // the results the newer one is about to bring.
        val result = suspendRunCatching {
            when (snapshot.mode) {
                SearchMode.Text -> reloadLoadedPages<TimelineItem, Int>(
                    keepLoaded,
                    idOf = { it.id }
                ) { offset ->
                    repository.textSearch(
                        query = snapshot.query.takeIf { it.isNotBlank() },
                        from = snapshot.from,
                        to = snapshot.to,
                        personIds = snapshot.selectedPersonIds.toList(),
                        objectLabels = snapshot.selectedObjectLabels.toList(),
                        sceneLabels = snapshot.selectedSceneLabels.toList(),
                        ocrText = snapshot.ocrText.takeIf { it.isNotBlank() },
                        mediaType = snapshot.mediaType?.wire,
                        offset = offset,
                        pinned = snapshot.pinned
                    ).let { page ->
                        val next = (offset ?: 0) + page.items.size
                        LoadedPage(page.items, page.hasMore && page.items.isNotEmpty(), next)
                    }
                }.let { it.items to it.hasMore }
                SearchMode.Semantic -> {
                    if (snapshot.query.isBlank()) {
                        emptyList<TimelineItem>() to false
                    } else {
                        // Se pide el tope del servidor (antes el 50 por
                        // defecto, cortado en silencio); si llega lleno, la
                        // pantalla lo dice.
                        val semantic = repository.semanticSearch(
                            snapshot.query.trim(),
                            limit = SEMANTIC_LIMIT
                        )
                        semantic.items.map { it.asset } to false
                    }
                }
            }
        }
        if (snapshot.mode == SearchMode.Semantic &&
            isSemanticSearchUnavailable(result.exceptionOrNull())
        ) {
            // Server without the AI models: a generic error left the screen in
            // AI mode with the filters locked. Fall back to text with the same
            // criteria and say why.
            _state.update {
                it.copy(
                    mode = SearchMode.Text,
                    semanticUnavailable = true,
                    results = emptyList(),
                    hasMore = false,
                    semanticCapped = false,
                    selection = emptySet()
                )
            }
            runSearch()
            return
        }
        result
            .onSuccess { (items, hasMore) ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        results = items,
                        hasMore = hasMore,
                        semanticCapped = snapshot.mode == SearchMode.Semantic &&
                            items.size >= SEMANTIC_LIMIT,
                        selection = emptySet()
                    )
                }
            }
            .onFailure { error ->
                // The previous results answered other criteria: leaving them
                // under the new query read as if the failed search had found
                // them. Empty list, so the screen shows the error with retry.
                _state.update {
                    it.copy(
                        isLoading = false,
                        results = emptyList(),
                        hasMore = false,
                        semanticCapped = false,
                        selection = emptySet(),
                        error = errorFactory.from(error, "No se pudo buscar")
                    )
                }
            }
    }

    private fun revertBulk(
        previousItems: List<TimelineItem>,
        ids: List<String>,
        throwable: Throwable,
        fallback: String
    ): UiError {
        val uiError = errorFactory.from(throwable, fallback)
        _state.update {
            it.copy(
                results = it.results.withRestored(previousItems, ids.toSet()),
                isBulkMutating = false,
                error = uiError
            )
        }
        return uiError
    }

    companion object {
        /** `ApiError.Code` del 503 de `/assets/search/semantic` sin modelos de IA. */
        private const val SEMANTIC_UNAVAILABLE_CODE = "semantic_search_unavailable"

        internal fun isSemanticSearchUnavailable(error: Throwable?): Boolean =
            (error as? PhotonneApiException)?.serverCode == SEMANTIC_UNAVAILABLE_CODE

        private const val DEBOUNCE_MILLIS = 350L
        /** How long a search must stay on screen to count as one the user ran. */
        private const val RECENT_DELAY_MILLIS = 1_500L
        /** Tope de `/assets/search/semantic` (SemanticSearchEndpoint). */
        const val SEMANTIC_LIMIT = 200
    }
}
