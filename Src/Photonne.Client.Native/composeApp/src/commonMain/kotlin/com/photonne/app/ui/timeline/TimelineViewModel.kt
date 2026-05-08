package com.photonne.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.timeline.TimelineRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

data class TimelineUiState(
    val items: List<TimelineItem> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val nextCursor: Instant? = null,
    val hasMore: Boolean = true
) {
    val isEmpty: Boolean get() = !isInitialLoading && items.isEmpty() && errorMessage == null
}

class TimelineViewModel(
    private val repository: TimelineRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineUiState())
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    private var pagingJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        pagingJob?.cancel()
        _state.update { it.copy(isRefreshing = it.items.isNotEmpty(), isInitialLoading = it.items.isEmpty(), errorMessage = null) }
        pagingJob = viewModelScope.launch {
            runCatching { repository.loadPage(cursor = null) }
                .onSuccess { result ->
                    _state.value = TimelineUiState(
                        items = result.items,
                        nextCursor = result.nextCursor,
                        hasMore = result.hasMore
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message ?: "Error al cargar la línea de tiempo"
                        )
                    }
                }
        }
    }

    fun setFavorite(assetId: String, isFavorite: Boolean) {
        _state.update { previous ->
            val updated = previous.items.map { item ->
                if (item.id == assetId) item.copy(isFavorite = isFavorite) else item
            }
            previous.copy(items = updated)
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isAppending || current.isInitialLoading || !current.hasMore) return
        val cursor = current.nextCursor ?: return

        _state.update { it.copy(isAppending = true, errorMessage = null) }
        pagingJob = viewModelScope.launch {
            runCatching { repository.loadPage(cursor = cursor) }
                .onSuccess { result ->
                    _state.update { previous ->
                        previous.copy(
                            items = previous.items + result.items,
                            nextCursor = result.nextCursor,
                            hasMore = result.hasMore,
                            isAppending = false
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isAppending = false,
                            errorMessage = error.message ?: "Error al paginar"
                        )
                    }
                }
        }
    }
}
