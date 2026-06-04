package com.photonne.app.ui.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.AssetDetail
import com.photonne.app.data.models.ExifData
import kotlinx.coroutines.Job
import kotlinx.datetime.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val detail: AssetDetail? = null,
    val isLoading: Boolean = false,
    val error: UiError? = null,
)

/**
 * Holds the metadata for the *currently visible* asset in the pager.
 * Detail is fetched lazily and cached per asset id while the screen is
 * alive. Caller invokes [select] every time the visible page changes.
 */
class AssetDetailViewModel(
    private val repository: AssetDetailRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AssetDetailUiState())
    val state: StateFlow<AssetDetailUiState> = _state.asStateFlow()

    private val cache = mutableMapOf<String, AssetDetail>()
    private var currentJob: Job? = null
    private var currentId: String? = null

    fun select(assetId: String) {
        if (assetId == currentId && _state.value.detail?.id == assetId) return
        currentId = assetId
        // Synthetic ids ("device:<uri>") represent local-only entries
        // that have no server detail to fetch. Skip the round-trip and
        // let callers fall back to the local TimelineItem fields.
        if (assetId.startsWith("device:")) {
            currentJob?.cancel()
            _state.value = AssetDetailUiState()
            return
        }
        cache[assetId]?.let { cached ->
            _state.value = AssetDetailUiState(detail = cached)
            return
        }
        currentJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null, detail = null) }
        currentJob = viewModelScope.launch {
            runCatching { repository.getDetail(assetId) }
                .onSuccess { detail ->
                    cache[assetId] = detail
                    if (currentId == assetId) {
                        _state.value = AssetDetailUiState(detail = detail)
                    }
                }
                .onFailure { error ->
                    if (currentId == assetId) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = errorFactory.from(error, "Error al cargar los detalles")
                            )
                        }
                    }
                }
        }
    }

    /**
     * Optimistically flips the favorite state for [assetId] both in the
     * cache and in the visible UI state, then calls the API. On failure
     * the state is reverted. The confirmed value is delivered through
     * [onCommitted] so callers (e.g. App) can propagate it to the
     * timeline grid.
     */
    fun toggleFavorite(assetId: String, onCommitted: (Boolean) -> Unit) {
        val previous = cache[assetId]?.isFavorite
            ?: _state.value.detail?.takeIf { it.id == assetId }?.isFavorite
            ?: false
        val optimistic = !previous

        applyFavorite(assetId, optimistic)

        viewModelScope.launch {
            runCatching { repository.toggleFavorite(assetId) }
                .onSuccess { confirmed ->
                    if (confirmed != optimistic) applyFavorite(assetId, confirmed)
                    onCommitted(confirmed)
                }
                .onFailure { error ->
                    applyFavorite(assetId, previous)
                    _state.update {
                        it.copy(error = errorFactory.from(error, "No se pudo actualizar el favorito"))
                    }
                }
        }
    }

    private fun applyFavorite(assetId: String, isFavorite: Boolean) {
        cache[assetId]?.let { cache[assetId] = it.copy(isFavorite = isFavorite) }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) {
                current.copy(detail = detail.copy(isFavorite = isFavorite))
            } else current
        }
    }

    fun archive(assetId: String, onCompleted: (assetId: String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.archive(listOf(assetId)) }
                .onSuccess {
                    cache.remove(assetId)
                    onCompleted(assetId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "Failed to archive"))
                    }
                }
        }
    }

    fun trash(assetId: String, onCompleted: (assetId: String) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.trash(listOf(assetId)) }
                .onSuccess {
                    cache.remove(assetId)
                    onCompleted(assetId)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "Failed to delete"))
                    }
                }
        }
    }

    fun updateDescription(assetId: String, description: String?) {
        val cleaned = description?.trim()?.takeIf { it.isNotEmpty() }
        // Optimistic local update — both cache and visible state.
        cache[assetId]?.let { cache[assetId] = it.copy(caption = cleaned) }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) {
                current.copy(detail = detail.copy(caption = cleaned))
            } else current
        }
        viewModelScope.launch {
            runCatching { repository.updateDescription(assetId, cleaned) }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "Failed to update description"))
                    }
                }
        }
    }

    /**
     * Asks the server what date it could recover for [assetId] (EXIF re-read
     * from the file and/or inferred from name/folder) WITHOUT applying it.
     * Returns null on failure — the sheet shows a "nothing found" hint.
     */
    suspend fun captureDateSuggestion(assetId: String): com.photonne.app.data.models.CaptureDateSuggestion? =
        runCatching { repository.getCaptureDateSuggestion(assetId) }.getOrNull()

    /**
     * Overrides the capture date of [assetId]. Updates the visible EXIF date
     * optimistically, then reconciles with the server's authoritative value
     * (it persists UTC and may differ by sub-second). When [writeToFile] is
     * true the server also writes the date into the physical file's EXIF.
     */
    fun updateCaptureDate(assetId: String, dateTaken: Instant, writeToFile: Boolean) {
        fun withDate(d: AssetDetail, date: Instant): AssetDetail =
            d.copy(exif = (d.exif ?: ExifData()).copy(dateTaken = date))

        // Optimistic local update — both cache and visible state.
        cache[assetId]?.let { cache[assetId] = withDate(it, dateTaken) }
        _state.update { current ->
            val detail = current.detail
            if (detail != null && detail.id == assetId) current.copy(detail = withDate(detail, dateTaken))
            else current
        }
        viewModelScope.launch {
            runCatching { repository.updateCaptureDate(assetId, dateTaken, writeToFile) }
                .onSuccess { resp ->
                    val confirmed = resp.dateTaken ?: return@onSuccess
                    cache[assetId]?.let { cache[assetId] = withDate(it, confirmed) }
                    _state.update { current ->
                        val detail = current.detail
                        if (detail != null && detail.id == assetId) current.copy(detail = withDate(detail, confirmed))
                        else current
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = errorFactory.from(error, "Failed to update capture date"))
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
