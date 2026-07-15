package com.photonne.app.ui.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.MemoryDetail
import com.photonne.app.data.models.MemoryKind
import com.photonne.app.data.timeline.MemoriesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reading order for the feed's rows. No longer titles anything — the row's header
 * comes from the server now — but it still answers "which row comes first", which
 * the server's Score deliberately doesn't: a ranking that reshuffles every night
 * is exactly what makes a memory impossible to find twice.
 *
 * Coarser than [MemoryKind] on purpose: two kinds can share a row ("Martina a lo
 * largo de los años" and "Martina y Joan" are both Personas).
 */
enum class MemorySectionId {
    Today,
    People,
    Trips,
    ThisMonth,
    Favorites,
    Things,

    /** Kinds this build doesn't know, or rows the server hasn't grouped yet.
     * Rendered last, headerless. */
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

/**
 * One row of the feed: a theme, and its cards. For a themed row the cards are its
 * years ("Días de playa" → 2024, 2023, 2021); for "Personas" they're the people.
 */
data class MemoryRow(
    /** Stable identity for the list key. The server's theme, or the section's name. */
    val key: String,
    /** The server's rendered header. Empty when it didn't send one — see [sectionId]. */
    val title: String,
    /**
     * Set only when [title] is empty: a server older than the grouping, or rows
     * the nightly pass hasn't refreshed yet. The screen titles these from its own
     * strings, which is the one thing the client is allowed to name — these are
     * the app's own sections, not the server's catalogue of themes.
     */
    val sectionId: MemorySectionId?,
    val memories: List<Memory>,
)

data class MemoryFeedUiState(
    val rows: List<MemoryRow> = emptyList(),
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
                        rows = groupIntoRows(memories),
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
     * Fetches a memory and hands it to [onLoaded] to open it as an album.
     * The feed only carries a cover, so the photos are a round-trip away — hence
     * the [MemoryFeedUiState.openingId] spinner rather than a silent pause.
     *
     * Passes the whole [MemoryDetail], not just its assets: the detail screen's
     * cover needs the title, subtitle and cover id that came with it.
     */
    fun open(memoryId: String, onLoaded: (MemoryDetail) -> Unit) {
        if (_state.value.openingId != null) return
        _state.update { it.copy(openingId = memoryId, error = null) }
        viewModelScope.launch {
            runCatching { repository.detail(memoryId) }
                .onSuccess { detail ->
                    _state.update { it.copy(openingId = null) }
                    if (detail.assets.isNotEmpty()) onLoaded(detail)
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

}

/**
 * Folds the flat feed into one row per theme.
 *
 * The server sorts purely by score, which answers "what's best" but reads as a
 * jumble down a screen: fifty full-width cards where "Días de playa" turns up at
 * positions 3, 17 and 31. The theme was always there — it just lived inside the
 * title text. Now it's a row.
 *
 * Row order is deliberately NOT the score. It's the declared [MemorySectionId]
 * order, then the header alphabetically: the whole point is that "Días de playa"
 * is where you left it yesterday, and a ranking that re-sorts itself every night
 * can't promise that. Within a row the server's ranking survives untouched.
 *
 * A file-level function, not a method: it's the one piece of this screen with
 * real logic, and this way it's testable without standing up a ViewModel.
 */
internal fun groupIntoRows(memories: List<Memory>): List<MemoryRow> =
    memories
        .groupBy { memory ->
            val section = MemorySectionId.of(MemoryKind.from(memory.kind))
            // Without a theme there is nothing finer to group by, so the row
            // falls back to the app's own section. Coarser — "Personas" instead
            // of a row per theme — but it still has a header and still holds its
            // place. A server that predates the grouping, or one deployed but not
            // yet regenerated, degrades to the shape this section had before,
            // rather than to one anonymous strip.
            RowKey(section, memory.themeKey.ifEmpty { section.name })
        }
        .toList()
        .sortedWith(
            compareBy(
                { (rowKey, _) -> rowKey.section.ordinal },
                { (_, items) -> items.first().groupTitle },
            )
        )
        .map { (rowKey, items) ->
            val isThemed = rowKey.key != rowKey.section.name
            MemoryRow(
                key = rowKey.key,
                title = if (isThemed) items.first().groupTitle else "",
                sectionId = if (isThemed) null else rowKey.section,
                memories = items,
            )
        }

/** A row's identity, carrying the section that orders it. */
private data class RowKey(val section: MemorySectionId, val key: String)
