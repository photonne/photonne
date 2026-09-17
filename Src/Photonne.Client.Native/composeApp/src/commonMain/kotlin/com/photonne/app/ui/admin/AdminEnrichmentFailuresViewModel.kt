package com.photonne.app.ui.admin

import com.photonne.app.resources.admin_enrichment_failures_error_action
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_enrichment_failures_error_load
import com.photonne.app.resources.admin_enrichment_failures_error_retry_all
import com.photonne.app.resources.admin_enrichment_failures_retry_all_done
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.jetbrains.compose.resources.getString
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.api.AdminEnrichmentFailureDto
import com.photonne.app.data.api.EnrichmentFailureKind
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
    /** How the open problems split by cause. Lets the screen say "3.412 no se
     *  arreglan reintentando" before anyone reads a row. */
    val countsByKind: Map<String, Int> = emptyMap(),
    val typeFilter: String? = null,
    /** Narrows the list to one cause. Null = todas. */
    val kindFilter: EnrichmentFailureKind? = null,
    val nextCursor: String? = null,
    val loadError: UiError? = null,
    /** Outcome of "Reintentar todo", for the snackbar. */
    val resultMessage: String? = null
)

/**
 * Backs the "Assets con problemas" admin screen: the cross-user registry of
 * enrichment tasks that failed (or were dismissed), with per-row retry and
 * suppress plus a filtered retry-all. Mirrors [AdminRepository]'s
 * `/api/admin/enrichment/failures` endpoints.
 */
class AdminEnrichmentFailuresViewModel(
    private val repository: AdminRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminEnrichmentFailuresUiState())
    val state: StateFlow<AdminEnrichmentFailuresUiState> = _state.asStateFlow()

    // One list request at a time. Two in flight meant the last to ANSWER won —
    // quick taps on the filter chips could leave the rows of one filter under
    // the chip of another — and a "load more" still running when a refresh
    // landed appended its old page to the new first one: the same taskId twice
    // in a LazyColumn keyed by it, which is a crash, not a glitch.
    private var listJob: Job? = null

    /**
     * Called on every entry. [initialType] pre-selects the filter the caller
     * asked for ("N con errores" on a Run Tasks row, a notification's
     * actionUrl). The view model outlives the screen, so this can't be a
     * first-time-only thing: it used to be, and from the second visit on the
     * requested type was ignored and the list was whatever it had been.
     */
    fun start(initialType: String?) {
        _state.update { it.copy(typeFilter = initialType?.takeIf { t -> t.isNotBlank() }) }
        refresh()
    }

    fun setFilter(type: String?) {
        if (_state.value.typeFilter == type) return
        _state.update { it.copy(typeFilter = type) }
        refresh()
    }

    fun setKindFilter(kind: EnrichmentFailureKind?) {
        if (_state.value.kindFilter == kind) return
        _state.update { it.copy(kindFilter = kind) }
        refresh()
    }

    fun consumeResult() {
        _state.update { it.copy(resultMessage = null) }
    }

    fun dismissError() {
        _state.update { it.copy(loadError = null) }
    }

    fun refresh() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isLoadingMore = false, loadError = null) }
            try {
                val page = repository.enrichmentFailures(
                    type = _state.value.typeFilter,
                    kind = _state.value.kindFilter?.name
                )
                _state.update {
                    it.copy(
                        isLoading = false,
                        items = page.items.map { dto -> AdminEnrichmentFailureItem(failure = dto) },
                        total = page.total,
                        countsByType = page.countsByType,
                        countsByKind = page.countsByKind,
                        nextCursor = page.nextCursor
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ex: Throwable) {
                // The rows stay: a refresh that failed is no reason to blank them.
                val error = errorFactory.from(ex, getString(Res.string.admin_enrichment_failures_error_load))
                _state.update { it.copy(isLoading = false, loadError = error) }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.isLoadingMore || _state.value.isLoading) return
        listJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true, loadError = null) }
            try {
                val page = repository.enrichmentFailures(
                    type = _state.value.typeFilter,
                    kind = _state.value.kindFilter?.name,
                    cursor = cursor
                )
                _state.update {
                    // The registry moves under the cursor (a retry elsewhere, a
                    // worker finishing), so a page can repeat a row already here.
                    val known = it.items.mapTo(HashSet()) { item -> item.failure.taskId }
                    val fresh = page.items.filter { dto -> known.add(dto.taskId) }
                    it.copy(
                        isLoadingMore = false,
                        items = it.items + fresh.map { dto -> AdminEnrichmentFailureItem(failure = dto) },
                        total = page.total,
                        countsByType = page.countsByType,
                        countsByKind = page.countsByKind,
                        nextCursor = page.nextCursor
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ex: Throwable) {
                val error = errorFactory.from(ex, getString(Res.string.admin_enrichment_failures_error_load))
                _state.update { it.copy(isLoadingMore = false, loadError = error) }
            }
        }
    }

    fun retry(taskId: String) = runRowAction(taskId) { repository.retryEnrichmentFailure(taskId) }

    fun suppress(taskId: String) = runRowAction(taskId) { repository.suppressEnrichmentFailure(taskId) }

    fun retryAll() {
        if (_state.value.isRetryingAll) return
        viewModelScope.launch {
            _state.update { it.copy(isRetryingAll = true, loadError = null) }
            // Honours the cause filter too: reintentar los permanentes solo
            // reproduce el mismo fallo y vuelve a llenar la cola.
            try {
                repository.retryAllEnrichmentFailures(
                    type = _state.value.typeFilter,
                    kind = _state.value.kindFilter?.name
                )
                val done = getString(Res.string.admin_enrichment_failures_retry_all_done)
                _state.update { it.copy(isRetryingAll = false, resultMessage = done) }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ex: Throwable) {
                // Used to be dropped: a retry-all that failed looked exactly
                // like one that worked, until the list came back unchanged.
                val error = errorFactory.from(ex, getString(Res.string.admin_enrichment_failures_error_retry_all))
                _state.update { it.copy(isRetryingAll = false, loadError = error) }
            }
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
                                item.copy(isBusy = false, actionError = errorFactory.from(ex, getString(Res.string.admin_enrichment_failures_error_action)).userMessage)
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
