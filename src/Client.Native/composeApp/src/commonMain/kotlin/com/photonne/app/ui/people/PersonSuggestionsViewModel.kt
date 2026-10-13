package com.photonne.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.PersonSuggestion
import com.photonne.app.data.people.PeopleRepository
import com.photonne.app.util.suspendRunCatching
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonSuggestionsUiState(
    val personId: String? = null,
    val personName: String? = null,
    val items: List<PersonSuggestion> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false,
    val isInitialLoading: Boolean = false,
    /** Recarga por gesto con contenido ya visible (pull-to-refresh). */
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
    /** La página siguiente falló: pie con reintento bajo la rejilla. */
    val appendError: UiError? = null,
    /** Per-face flag while we're posting accept/dismiss for that row. */
    val pendingFaceIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = !isInitialLoading && items.isEmpty()
}

/**
 * Loads pending face suggestions for a single person and exposes the
 * accept / dismiss bulk + per-face actions. Lives outside of
 * `PersonDetailViewModel` so its state survives navigation away from
 * the suggestions screen.
 */
class PersonSuggestionsViewModel(
    private val repository: PeopleRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(PersonSuggestionsUiState())
    val state: StateFlow<PersonSuggestionsUiState> = _state.asStateFlow()

    /** The page being fetched. Opening another person (or refreshing) replaces
     *  it, so a slow page of the previous person can't land on this one. */
    private var loadJob: Job? = null

    fun open(personId: String, personName: String?) {
        if (_state.value.personId == personId && _state.value.items.isNotEmpty()) {
            _state.update { it.copy(personName = personName) }
            return
        }
        _state.value = PersonSuggestionsUiState(
            personId = personId,
            personName = personName,
            isInitialLoading = true
        )
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadInternal(append = false) }
    }

    fun refresh() {
        if (_state.value.personId == null) return
        _state.update {
            it.copy(
                isInitialLoading = it.items.isEmpty(),
                isRefreshing = it.items.isNotEmpty(),
                error = null,
                appendError = null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadInternal(append = false) }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isAppending || snapshot.isInitialLoading || !snapshot.hasMore) return
        _state.update { it.copy(isAppending = true, appendError = null) }
        loadJob = viewModelScope.launch { loadInternal(append = true) }
    }

    private suspend fun loadInternal(append: Boolean) {
        val personId = _state.value.personId ?: return
        val offset = if (append) _state.value.items.size else 0
        suspendRunCatching { repository.suggestions(personId = personId, offset = offset) }
            .onSuccess { page ->
                _state.update { previous ->
                    if (previous.personId != personId) return@update previous
                    val merged = if (append) {
                        val existing = previous.items.mapTo(HashSet()) { it.id }
                        previous.items + page.items.filter { it.id !in existing }
                    } else page.items
                    previous.copy(
                        items = merged,
                        total = page.total,
                        hasMore = merged.size < page.total,
                        isInitialLoading = false,
                        isRefreshing = false,
                        isAppending = false
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    if (it.personId != personId) return@update it
                    if (append) {
                        it.copy(
                            isAppending = false,
                            appendError = errorFactory.from(error, "No se pudo cargar más")
                        )
                    } else {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isAppending = false,
                            error = errorFactory.from(error, "No se pudieron cargar las sugerencias")
                        )
                    }
                }
            }
    }

    /**
     * [onError] carries a per-face failure to a snackbar: the face stays in the
     * list, so `state.error` (painted only over an empty list) never showed it.
     */
    fun acceptFace(
        faceId: String,
        onError: (UiError) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        markPending(faceId, true)
        viewModelScope.launch {
            runCatching { repository.acceptFaceSuggestion(faceId) }
                .onSuccess {
                    dropItem(faceId)
                    onSuccess()
                }
                .onFailure { error -> onError(faceFailed(faceId, error, "No se pudo aceptar")) }
        }
    }

    fun dismissFace(
        faceId: String,
        onError: (UiError) -> Unit = {},
        onSuccess: () -> Unit = {}
    ) {
        markPending(faceId, true)
        viewModelScope.launch {
            runCatching { repository.dismissFaceSuggestion(faceId) }
                .onSuccess {
                    dropItem(faceId)
                    onSuccess()
                }
                .onFailure { error -> onError(faceFailed(faceId, error, "No se pudo descartar")) }
        }
    }

    fun acceptAll(onSuccess: (affected: Int) -> Unit = {}) {
        val personId = _state.value.personId ?: return
        if (_state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.acceptAllSuggestions(personId) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            items = emptyList(),
                            total = 0,
                            hasMore = false
                        )
                    }
                    onSuccess(result.affected)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudieron aceptar todas")
                        )
                    }
                }
        }
    }

    fun dismissAll(onSuccess: (affected: Int) -> Unit = {}) {
        val personId = _state.value.personId ?: return
        if (_state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.dismissAllSuggestions(personId) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            items = emptyList(),
                            total = 0,
                            hasMore = false
                        )
                    }
                    onSuccess(result.affected)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudieron descartar todas")
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun markPending(faceId: String, pending: Boolean) {
        _state.update {
            it.copy(
                pendingFaceIds = if (pending) it.pendingFaceIds + faceId
                else it.pendingFaceIds - faceId
            )
        }
    }

    private fun dropItem(faceId: String) {
        _state.update {
            it.copy(
                items = it.items.filterNot { item -> item.id == faceId },
                total = (it.total - 1).coerceAtLeast(0),
                pendingFaceIds = it.pendingFaceIds - faceId
            )
        }
    }

    private fun faceFailed(faceId: String, throwable: Throwable, fallback: String): UiError {
        markPending(faceId, false)
        return errorFactory.from(throwable, fallback)
    }
}
