package com.photonne.app.ui.devicebackup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.api.PendingEnrichmentAssetDto
import com.photonne.app.data.api.PendingEnrichmentPage
import com.photonne.app.data.devicebackup.EnrichmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Per-asset row in the enrichment status screen. Mirrors
 * [PendingEnrichmentAssetDto] plus client-side retry state.
 */
data class EnrichmentAssetItem(
    val asset: PendingEnrichmentAssetDto,
    val isRetryingAll: Boolean = false,
    val perTypeRetrying: Set<String> = emptySet(),
    val retryError: String? = null
)

data class EnrichmentStatusUiState(
    val isLoading: Boolean = false,
    val items: List<EnrichmentAssetItem> = emptyList(),
    val totalAssets: Int = 0,
    /** Assets the server is still working on (queued, running or retrying). */
    val inFlightAssets: Int = 0,
    /** Assets with a task out of attempts: only a manual retry moves them. */
    val failedAssets: Int = 0,
    val nextCursor: String? = null,
    val loadError: String? = null
)

/**
 * A server that predates the split only sends [PendingEnrichmentPage.totalAssets],
 * which mixed both; showing it as "processing" is what it always did.
 */
private fun EnrichmentStatusUiState.withCounts(page: PendingEnrichmentPage) = copy(
    totalAssets = page.totalAssets,
    inFlightAssets = page.inFlightAssets ?: page.totalAssets,
    failedAssets = page.failedAssets ?: 0
)

class EnrichmentStatusViewModel(
    private val repository: EnrichmentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(EnrichmentStatusUiState())
    val state: StateFlow<EnrichmentStatusUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            runCatching { repository.listPending(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            items = page.items.map { dto -> EnrichmentAssetItem(asset = dto) },
                            nextCursor = page.nextCursor
                        ).withCounts(page)
                    }
                }
                .onFailure { ex ->
                    _state.update {
                        it.copy(isLoading = false, loadError = ex.message ?: "No se pudo cargar")
                    }
                }
        }
    }

    fun retryTask(assetId: String, taskType: String) {
        viewModelScope.launch {
            markTypeRetrying(assetId, taskType, true)
            runCatching { repository.retryTask(assetId, taskType) }
                .onSuccess {
                    markTypeRetrying(assetId, taskType, false)
                    refreshAfterMutation()
                }
                .onFailure { ex ->
                    markTypeRetrying(assetId, taskType, false)
                    _state.update { cur ->
                        cur.copy(items = cur.items.map { item ->
                            if (item.asset.assetId == assetId)
                                item.copy(retryError = ex.message ?: "No se pudo reintentar")
                            else item
                        })
                    }
                }
        }
    }

    fun retryAll(assetId: String) {
        viewModelScope.launch {
            markIsRetryingAll(assetId, true)
            runCatching { repository.retryAll(assetId) }
                .onSuccess {
                    markIsRetryingAll(assetId, false)
                    refreshAfterMutation()
                }
                .onFailure { ex ->
                    markIsRetryingAll(assetId, false)
                    _state.update { cur ->
                        cur.copy(items = cur.items.map { item ->
                            if (item.asset.assetId == assetId)
                                item.copy(retryError = ex.message ?: "No se pudo reintentar")
                            else item
                        })
                    }
                }
        }
    }

    /**
     * After a retry, fetch the full list again so items whose tasks all
     * completed disappear from view. Keeps client state from getting stale.
     */
    private fun refreshAfterMutation() {
        viewModelScope.launch {
            runCatching { repository.listPending(cursor = null) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = page.items.map { dto -> EnrichmentAssetItem(asset = dto) },
                            nextCursor = page.nextCursor
                        ).withCounts(page)
                    }
                }
            // Swallow errors silently: the user already saw the retry succeed; a
            // refresh failure is not worth interrupting them.
        }
    }

    private fun markTypeRetrying(assetId: String, taskType: String, retrying: Boolean) {
        _state.update { cur ->
            cur.copy(items = cur.items.map { item ->
                if (item.asset.assetId != assetId) item
                else item.copy(
                    perTypeRetrying = if (retrying)
                        item.perTypeRetrying + taskType
                    else item.perTypeRetrying - taskType,
                    retryError = null
                )
            })
        }
    }

    private fun markIsRetryingAll(assetId: String, retrying: Boolean) {
        _state.update { cur ->
            cur.copy(items = cur.items.map { item ->
                if (item.asset.assetId != assetId) item
                else item.copy(isRetryingAll = retrying, retryError = null)
            })
        }
    }
}
