package com.photonne.app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.MemoryKind
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.timeline.MemoriesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A block of the Recuerdos section. Coarser than [MemoryKind] on purpose: two
 * kinds can share a heading ("Martina a lo largo de los años" and "Martina y
 * Joan juntos" are both Personas), and grouping by kind would print that heading
 * twice. Declaration order is reading order.
 */
enum class MemorySectionId {
    Today,
    People,
    Trips,
    ThisMonth,
    Favorites,
    Things,

    /** Kinds this build doesn't know — a newer server's. Rendered last, headerless. */
    Other;

    companion object {
        fun of(kind: MemoryKind): MemorySectionId = when (kind) {
            MemoryKind.OnThisDay -> Today
            MemoryKind.PersonThroughYears, MemoryKind.PeopleTogether -> People
            MemoryKind.Trip -> Trips
            MemoryKind.ThisMonth -> ThisMonth
            MemoryKind.FavoritesOfYear -> Favorites
            MemoryKind.CuratedScene, MemoryKind.PetsAndFood -> Things
            MemoryKind.Unknown -> Other
        }
    }
}

data class MemorySection(
    val id: MemorySectionId,
    val memories: List<Memory>,
)

data class MemoryFeedUiState(
    val sections: List<MemorySection> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val attempted: Boolean = false,
    /** Id of the memory whose assets are being fetched for the viewer. */
    val openingId: String? = null,
)

/**
 * Drives the Recuerdos section. Separate from MemoriesViewModel on purpose: that
 * one owns the timeline strip's live "on this day" list, this one owns the
 * generated feed. Merging them would tie the strip's refresh to a much heavier
 * request it doesn't need.
 */
class MemoryFeedViewModel(
    private val repository: MemoriesRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryFeedUiState())
    val state: StateFlow<MemoryFeedUiState> = _state.asStateFlow()

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.feed() }
                .onSuccess { memories ->
                    _state.value = MemoryFeedUiState(
                        sections = groupIntoSections(memories),
                        attempted = true,
                    )
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            attempted = true,
                            error = errorFactory.from(error, "Failed to load memories"),
                        )
                    }
                }
        }
    }

    /**
     * Fetches a memory's assets and hands them to [onLoaded] to open the viewer.
     * The feed only carries a cover, so the photos are a round-trip away — hence
     * the [MemoryFeedUiState.openingId] spinner rather than a silent pause.
     */
    fun open(memoryId: String, onLoaded: (List<TimelineItem>) -> Unit) {
        if (_state.value.openingId != null) return
        _state.update { it.copy(openingId = memoryId, error = null) }
        viewModelScope.launch {
            runCatching { repository.detail(memoryId) }
                .onSuccess { detail ->
                    _state.update { it.copy(openingId = null) }
                    if (detail.assets.isNotEmpty()) onLoaded(detail.assets)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            openingId = null,
                            error = errorFactory.from(error, "Failed to open memory"),
                        )
                    }
                }
        }
    }

    /**
     * Groups the feed into sections, keeping the server's ranking inside each one.
     * The server sorts purely by score, which is the right answer to "what's
     * best" but reads as a jumble down a screen — a person card wedged between
     * two year cards looks like a bug rather than a ranking.
     */
    private fun groupIntoSections(memories: List<Memory>): List<MemorySection> =
        memories
            .groupBy { MemorySectionId.of(MemoryKind.from(it.kind)) }
            .toList()
            .sortedBy { (id, _) -> id.ordinal }
            .map { (id, items) -> MemorySection(id, items) }
}
