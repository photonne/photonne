package com.photonne.app.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.PersonSuggestion
import com.photonne.app.data.people.PeopleRepository
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
    val isAppending: Boolean = false,
    val isBulkMutating: Boolean = false,
    val error: UiError? = null,
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
        viewModelScope.launch { loadInternal(append = false) }
    }

    fun refresh() {
        if (_state.value.personId == null) return
        _state.update {
            it.copy(isInitialLoading = it.items.isEmpty(), error = null)
        }
        viewModelScope.launch { loadInternal(append = false) }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.isAppending || snapshot.isInitialLoading || !snapshot.hasMore) return
        _state.update { it.copy(isAppending = true) }
        viewModelScope.launch { loadInternal(append = true) }
    }

    private suspend fun loadInternal(append: Boolean) {
        val personId = _state.value.personId ?: return
        val offset = if (append) _state.value.items.size else 0
        runCatching { repository.suggestions(personId = personId, offset = offset) }
            .onSuccess { page ->
                _state.update { previous ->
                    val merged = if (append) {
                        val existing = previous.items.mapTo(HashSet()) { it.id }
                        previous.items + page.items.filter { it.id !in existing }
                    } else page.items
                    previous.copy(
                        items = merged,
                        total = page.total,
                        hasMore = merged.size < page.total,
                        isInitialLoading = false,
                        isAppending = false
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        isInitialLoading = false,
                        isAppending = false,
                        error = errorFactory.from(error, "Failed to load suggestions")
                    )
                }
            }
    }

    fun acceptFace(faceId: String, onSuccess: () -> Unit = {}) {
        markPending(faceId, true)
        viewModelScope.launch {
            runCatching { repository.acceptFaceSuggestion(faceId) }
                .onSuccess {
                    dropItem(faceId)
                    onSuccess()
                }
                .onFailure { error -> setError(faceId, error, "Failed to accept") }
        }
    }

    fun dismissFace(faceId: String, onSuccess: () -> Unit = {}) {
        markPending(faceId, true)
        viewModelScope.launch {
            runCatching { repository.dismissFaceSuggestion(faceId) }
                .onSuccess {
                    dropItem(faceId)
                    onSuccess()
                }
                .onFailure { error -> setError(faceId, error, "Failed to dismiss") }
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
                            error = errorFactory.from(error, "Failed to accept all")
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
                            error = errorFactory.from(error, "Failed to dismiss all")
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

    private fun setError(faceId: String, throwable: Throwable, fallback: String) {
        _state.update {
            it.copy(
                pendingFaceIds = it.pendingFaceIds - faceId,
                error = errorFactory.from(throwable, fallback)
            )
        }
    }
}
