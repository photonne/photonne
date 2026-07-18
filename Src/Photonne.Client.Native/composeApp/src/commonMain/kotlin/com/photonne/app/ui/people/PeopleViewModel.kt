package com.photonne.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.Person
import com.photonne.app.data.people.PeopleRepository
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
    val showHidden: Boolean = false
) {
    val isEmpty: Boolean get() = loaded && people.isEmpty() && !isInitialLoading
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

    fun refresh() {
        _state.update {
            it.copy(
                isRefreshing = it.loaded,
                isInitialLoading = !it.loaded,
                error = null
            )
        }
        viewModelScope.launch {
            runCatching {
                repository.list(includeHidden = _state.value.showHidden, limit = PAGE_SIZE, offset = 0)
            }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            people = page.items,
                            total = page.total,
                            hasMore = page.items.size < page.total,
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
        viewModelScope.launch {
            runCatching {
                repository.list(
                    includeHidden = snapshot.showHidden,
                    limit = PAGE_SIZE,
                    offset = snapshot.people.size
                )
            }
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
                    _state.update {
                        it.copy(
                            isAppending = false,
                            error = errorFactory.from(error, "No se pudo cargar más")
                        )
                    }
                }
        }
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
    }
}
