package com.photonne.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.AdminEnrichmentFailureDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One Failed/Suppressed task row in the admin failures registry, plus the
 * client-side busy/error state of its own action buttons.
 */
data class AdminEnrichmentFailureItem(
    val failure: AdminEnrichmentFailureDto,
    val isBusy: Boolean = false,
    val actionError: String? = null
)

data class AdminEnrichmentFailuresUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRetryingAll: Boolean = false,
    val items: List<AdminEnrichmentFailureItem> = emptyList(),
    val total: Int = 0,
    val countsByType: Map<String, Int> = emptyMap(),
    val typeFilter: String? = null,
    val nextCursor: String? = null,
    val loadError: String? = null
)

/**
 * Backs the "Assets con problemas" admin screen: the cross-user registry of
 * enrichment tasks that failed (or were dismissed), with per-row retry and
 * suppress plus a filtered retry-all. Mirrors [AdminRepository]'s
 * `/api/admin/enrichment/failures` endpoints.
 */
class AdminEnrichmentFailuresViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminEnrichmentFailuresUiState())
    val state: StateFlow<AdminEnrichmentFailuresUiState> = _state.asStateFlow()

    private var started = false

    /** First load; [initialType] pre-selects the filter the actionUrl asked for. */
    fun start(initialType: String?) {
        if (started) return
        started = true
        _state.update { it.copy(typeFilter = initialType?.takeIf { t -> t.isNotBlank() }) }
        refresh()
    }

    fun setFilter(type: String?) {
        if (_state.value.typeFilter == type) return
        _state.update { it.copy(typeFilter = type) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            runCatching { repository.enrichmentFailures(type = _state.value.typeFilter) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = page.items.map { dto -> AdminEnrichmentFailureItem(failure = dto) },
                            total = page.total,
                            countsByType = page.countsByType,
                            nextCursor = page.nextCursor
                        )
                    }
                }
                .onFailure { ex ->
                    _state.update {
                        it.copy(isLoading = false, loadError = ex.message ?: "No se pudo cargar")
                    }
                }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            runCatching {
                repository.enrichmentFailures(type = _state.value.typeFilter, cursor = cursor)
            }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            items = it.items + page.items.map { dto -> AdminEnrichmentFailureItem(failure = dto) },
                            total = page.total,
                            countsByType = page.countsByType,
                            nextCursor = page.nextCursor
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    fun retry(taskId: String) = runRowAction(taskId) { repository.retryEnrichmentFailure(taskId) }

    fun suppress(taskId: String) = runRowAction(taskId) { repository.suppressEnrichmentFailure(taskId) }

    fun retryAll() {
        if (_state.value.isRetryingAll) return
        viewModelScope.launch {
            _state.update { it.copy(isRetryingAll = true) }
            runCatching { repository.retryAllEnrichmentFailures(type = _state.value.typeFilter) }
            _state.update { it.copy(isRetryingAll = false) }
            refresh()
        }
    }

    private fun runRowAction(taskId: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            markBusy(taskId, busy = true)
            runCatching { action() }
                .onSuccess {
                    markBusy(taskId, busy = false)
                    refresh()
                }
                .onFailure { ex ->
                    _state.update { cur ->
                        cur.copy(items = cur.items.map { item ->
                            if (item.failure.taskId == taskId)
                                item.copy(isBusy = false, actionError = ex.message ?: "No se pudo aplicar")
                            else item
                        })
                    }
                }
        }
    }

    private fun markBusy(taskId: String, busy: Boolean) {
        _state.update { cur ->
            cur.copy(items = cur.items.map { item ->
                if (item.failure.taskId == taskId) item.copy(isBusy = busy, actionError = null)
                else item
            })
        }
    }
}
