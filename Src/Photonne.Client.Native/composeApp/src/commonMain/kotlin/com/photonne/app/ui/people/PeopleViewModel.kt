package com.photonne.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.Person
import com.photonne.app.data.people.PeopleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PeopleUiState(
    val people: List<Person> = emptyList(),
    val total: Int = 0,
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiError? = null,
    val hasMore: Boolean = false,
    val loaded: Boolean = false,
    val showHidden: Boolean = false,
    /** Texto del buscador; se filtra en el servidor (nombre sin mayúsculas ni acentos). */
    val search: String = "",
    val isSearchActive: Boolean = false,
    val sort: PeopleSort = PeopleSort.FaceCount,
) {
    val isEmpty: Boolean get() = loaded && people.isEmpty() && !isInitialLoading
    /** Vacío por culpa del filtro, no porque no haya personas. */
    val isNoResults: Boolean get() = isEmpty && search.isNotBlank()
}

/**
 * Orden de la lista, traducido a los parámetros de `GET /api/people`. "Sin
 * nombre primero" es para ponerse al día etiquetando: dentro de cada bloque
 * manda el nº de fotos, igual que el orden por defecto.
 */
enum class PeopleSort(val sortKey: String?, val sortDir: String?, val unnamedFirst: Boolean) {
    FaceCount(sortKey = null, sortDir = null, unnamedFirst = false),
    Name(sortKey = "name", sortDir = "asc", unnamedFirst = false),
    UnnamedFirst(sortKey = null, sortDir = null, unnamedFirst = true),
}

class PeopleViewModel(
    private val repository: PeopleRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(PeopleUiState())
    val state: StateFlow<PeopleUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.loaded || _state.value.isInitialLoading) return
        refresh()
    }

    /** Pide la página [offset] con el filtro y el orden vigentes. */
    private suspend fun fetch(snapshot: PeopleUiState, offset: Int) = repository.list(
        includeHidden = snapshot.showHidden,
        limit = PAGE_SIZE,
        offset = offset,
        search = snapshot.search.trim().takeIf { it.isNotEmpty() },
        sort = snapshot.sort,
    )

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    fun refresh() = reload(showRefreshIndicator = true)

    /**
     * Recarga desde la primera página. Cancela la carga anterior: con el
     * buscador, una respuesta lenta de "An" no puede pisar la de "Ana".
     */
    private fun reload(showRefreshIndicator: Boolean) {
        _state.update {
            it.copy(
                isRefreshing = it.loaded && showRefreshIndicator,
                isInitialLoading = !it.loaded,
                error = null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runCatching { fetch(_state.value, offset = 0) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            people = page.items,
                            total = page.total,
                            hasMore = page.items.size < page.total,
                            isInitialLoading = false,
                            isRefreshing = false,
                            isAppending = false,
                            loaded = true
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            error = errorFactory.from(error, "No se pudieron cargar las personas")
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isAppending || !snapshot.hasMore || snapshot.isInitialLoading) return
        _state.update { it.copy(isAppending = true) }
        loadJob = viewModelScope.launch {
            runCatching { fetch(snapshot, offset = snapshot.people.size) }
                .onSuccess { page ->
                    _state.update { previous ->
                        val existing = previous.people.mapTo(HashSet()) { it.id }
                        val appended = page.items.filter { it.id !in existing }
                        val merged = previous.people + appended
                        previous.copy(
                            people = merged,
                            total = page.total,
                            hasMore = merged.size < page.total,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isAppending = false,
                            error = errorFactory.from(error, "No se pudo cargar más")
                        )
                    }
                }
        }
    }

    /**
     * Carga TODAS las páginas restantes de una tirada. El selector de fusión
     * tiene que poder encontrar a cualquier persona, no solo a las ya
     * scrolleadas; con paginado perezoso las no cargadas eran infusionables.
     */
    fun loadAllPages() {
        // Con un filtro puesto, "todas las páginas" serían solo las que casan:
        // el selector de fusión dejaría fuera a media lista. Se quita el filtro
        // y se empieza de cero.
        if (_state.value.search.isNotBlank()) {
            searchJob?.cancel()
            loadJob?.cancel()
            _state.update {
                it.copy(
                    search = "",
                    isSearchActive = false,
                    people = emptyList(),
                    hasMore = true,
                    isInitialLoading = false,
                    isAppending = false
                )
            }
        }
        val snapshot = _state.value
        if (snapshot.isAppending || !snapshot.hasMore || snapshot.isInitialLoading) return
        _state.update { it.copy(isAppending = true) }
        loadJob = viewModelScope.launch {
            while (true) {
                val current = _state.value
                val page = runCatching { fetch(current, offset = current.people.size) }.getOrElse { error ->
                    if (error is CancellationException) throw error
                    _state.update {
                        it.copy(
                            isAppending = false,
                            error = errorFactory.from(error, "No se pudo cargar más")
                        )
                    }
                    return@launch
                }
                val existing = current.people.mapTo(HashSet()) { it.id }
                val appended = page.items.filter { it.id !in existing }
                val merged = current.people + appended
                val done = merged.size >= page.total || appended.isEmpty()
                _state.update {
                    it.copy(
                        people = merged,
                        total = page.total,
                        hasMore = merged.size < page.total,
                        isAppending = !done
                    )
                }
                if (done) break
            }
        }
    }

    fun toggleSearch() {
        val wasActive = _state.value.isSearchActive
        _state.update { it.copy(isSearchActive = !wasActive) }
        // Cerrar el buscador también quita el filtro.
        if (wasActive && _state.value.search.isNotEmpty()) setSearch("")
    }

    /** Filtra en el servidor tras una pausa corta al teclear. */
    fun setSearch(text: String) {
        if (text == _state.value.search) return
        _state.update { it.copy(search = text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reload(showRefreshIndicator = false)
        }
    }

    fun setSort(sort: PeopleSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        reload(showRefreshIndicator = false)
    }

    fun toggleShowHidden() {
        _state.update {
            it.copy(showHidden = !it.showHidden, loaded = false, people = emptyList())
        }
        refresh()
    }

    fun rename(personId: String, name: String?, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.rename(personId, name) }
                .onSuccess {
                    val cleaned = name?.trim()?.takeIf { it.isNotEmpty() }
                    _state.update { previous ->
                        previous.copy(
                            isMutating = false,
                            people = previous.people.map { person ->
                                if (person.id == personId) person.copy(name = cleaned) else person
                            }
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo renombrar")
                        )
                    }
                }
        }
    }

    fun hide(personId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.hide(personId) }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            isMutating = false,
                            // Drop from the visible list unless the user has
                            // already toggled "show hidden" on.
                            people = if (previous.showHidden)
                                previous.people.map { p ->
                                    if (p.id == personId) p.copy(isHidden = true) else p
                                }
                            else previous.people.filterNot { it.id == personId },
                            total = (previous.total - if (previous.showHidden) 0 else 1)
                                .coerceAtLeast(0)
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo ocultar")
                        )
                    }
                }
        }
    }

    fun unhide(personId: String, onSuccess: () -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.unhide(personId) }
                .onSuccess {
                    _state.update { previous ->
                        previous.copy(
                            isMutating = false,
                            people = previous.people.map { p ->
                                if (p.id == personId) p.copy(isHidden = false) else p
                            }
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo mostrar")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Kick off a full per-user reclustering pass. The server does the work
     * in the background; we just surface the count of new persons it
     * created and refresh the list so any of them show up immediately.
     */
    fun recluster(onSuccess: (personsCreated: Int) -> Unit = {}) {
        if (_state.value.isMutating) return
        _state.update { it.copy(isMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.recluster() }
                .onSuccess { result ->
                    _state.update { it.copy(isMutating = false) }
                    onSuccess(result.personsCreated)
                    refresh()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isMutating = false,
                            error = errorFactory.from(error, "No se pudo reagrupar")
                        )
                    }
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 80
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
