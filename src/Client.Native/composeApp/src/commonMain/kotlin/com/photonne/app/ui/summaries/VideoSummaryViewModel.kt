package com.photonne.app.ui.summaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.VideoSummary
import com.photonne.app.data.models.VideoSummaryYear
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoSummaryUiState(
    /** Null while the years load, for the summary of a year. */
    val source: SummarySource? = null,
    /** True for "the summary of a year": the user picks which. */
    val pickYear: Boolean = false,
    val years: List<VideoSummaryYear> = emptyList(),
    val summaries: List<VideoSummary> = emptyList(),
    val options: SummaryOptions = SummaryOptions(),
    /** The user's "every December" switch; null until read. */
    val autoYear: Boolean? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    /** Why the last request failed, fit to show. */
    val error: String? = null,
    /** "Creating «…»", once per request, for a snackbar. */
    val requested: String? = null,
) {
    val current: VideoSummary? get() = source?.let { summaries.forSource(it) }
    val active: Boolean get() = current?.isActive == true
    val noYears: Boolean get() = pickYear && !isLoading && years.isEmpty()
    val canSubmit: Boolean get() = source != null && !isSubmitting && !active && !noYears
}

/**
 * The summary video dialog, as on the web: pick the year (or take the album),
 * the options, and ask; then follow the render until it's in the library.
 * Starts from the options the last request for that year or album used.
 */
class VideoSummaryViewModel(
    private val api: PhotonneApi,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoSummaryUiState())
    val state: StateFlow<VideoSummaryUiState> = _state.asStateFlow()

    private var poll: Job? = null

    /** [album] null: the summary of a year. */
    fun open(album: SummarySource.Album?) {
        poll?.cancel()
        _state.value = VideoSummaryUiState(source = album, pickYear = album == null, isLoading = true)
        viewModelScope.launch {
            val summaries = runCatching { api.getVideoSummaries() }.getOrElse { emptyList() }
            val years = if (album == null) runCatching { api.getVideoSummaryYears() }.getOrElse { emptyList() }
            else emptyList()
            val auto = if (album == null) {
                runCatching { api.adminGetSetting(SummaryOptions.AUTO_YEAR_KEY).value == "true" }.getOrNull()
            } else null
            _state.update { s ->
                val source = s.source ?: years.firstOrNull()?.let { SummarySource.Year(it.year) }
                s.copy(
                    source = source,
                    years = years,
                    summaries = summaries,
                    options = source?.let { summaries.forSource(it) }?.let { SummaryOptions.of(it) } ?: SummaryOptions(),
                    autoYear = auto,
                    isLoading = false,
                )
            }
            followWhileActive()
        }
    }

    fun selectYear(year: Int) {
        val source = SummarySource.Year(year)
        _state.update { s ->
            s.copy(
                source = source,
                options = s.summaries.forSource(source)?.let { SummaryOptions.of(it) } ?: SummaryOptions(),
                error = null,
            )
        }
    }

    fun updateOptions(change: (SummaryOptions) -> SummaryOptions) {
        _state.update { it.copy(options = change(it.options)) }
    }

    fun submit() {
        val s = _state.value
        val source = s.source ?: return
        if (!s.canSubmit) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching { api.requestVideoSummary(requestBody(source, s.options)) }
                .onSuccess { summary -> accepted(summary) }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = errorFactory.from(failure, "No se pudo pedir el vídeo resumen").userMessage,
                        )
                    }
                }
        }
    }

    /** A "planned" summary rendered with the clips the server chose. */
    fun renderAsPlanned() {
        val current = _state.value.current ?: return
        if (current.status != "planned" || _state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching { api.renderVideoSummary(current.id) }
                .onSuccess { summary -> accepted(summary) }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = errorFactory.from(failure, "No se pudo montar el vídeo").userMessage,
                        )
                    }
                }
        }
    }

    fun setAutoYear(enabled: Boolean) {
        val before = _state.value.autoYear
        _state.update { it.copy(autoYear = enabled) }
        viewModelScope.launch {
            runCatching { api.adminSaveSetting(SummaryOptions.AUTO_YEAR_KEY, enabled.toString()) }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            autoYear = before,
                            error = errorFactory.from(failure, "No se pudo guardar el ajuste").userMessage,
                        )
                    }
                }
        }
    }

    fun requestedShown() = _state.update { it.copy(requested = null) }

    fun close() {
        poll?.cancel()
        poll = null
    }

    private fun accepted(summary: VideoSummary) {
        _state.update { s ->
            s.copy(
                isSubmitting = false,
                summaries = listOf(summary) + s.summaries.filterNot { it.id == summary.id },
                requested = summary.title,
            )
        }
        followWhileActive()
    }

    /** Re-reads the list every few seconds while something renders. */
    private fun followWhileActive() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (_state.value.summaries.any { it.isActive }) {
                delay(POLL_MILLIS)
                runCatching { api.getVideoSummaries() }
                    .onSuccess { list -> _state.update { it.copy(summaries = list) } }
            }
        }
    }

    override fun onCleared() {
        close()
    }

    companion object {
        const val POLL_MILLIS = 3_000L
    }
}
