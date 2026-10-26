package com.photonne.app.data.search

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A search the user ran, enough to run it again: the words and the filters,
 * plus what its chip says. Dates go as ISO days.
 */
@Serializable
data class RecentSearch(
    val label: String,
    val query: String = "",
    val semantic: Boolean = false,
    val from: String? = null,
    val to: String? = null,
    val ocrText: String = "",
    /** "image" / "video". */
    val mediaType: String? = null,
    val personIds: List<String> = emptyList(),
    val objectLabels: List<String> = emptyList(),
    val sceneLabels: List<String> = emptyList(),
    val pinned: RecentPinned? = null,
)

/** [PinnedSearchFilter] as it is stored. */
@Serializable
data class RecentPinned(
    /** "tag", "camera" or "place". */
    val kind: String,
    val value: String,
    /** The camera's make, or the place's name. */
    val extra: String? = null,
) {
    fun toFilter(): PinnedSearchFilter? = when (kind) {
        "tag" -> PinnedSearchFilter.Tag(value)
        "camera" -> PinnedSearchFilter.Camera(extra, value)
        "place" -> PinnedSearchFilter.Place(value, extra ?: value)
        else -> null
    }

    companion object {
        fun of(filter: PinnedSearchFilter): RecentPinned = when (filter) {
            is PinnedSearchFilter.Tag -> RecentPinned("tag", filter.name)
            is PinnedSearchFilter.Camera -> RecentPinned("camera", filter.model, filter.make)
            is PinnedSearchFilter.Place -> RecentPinned("place", filter.id, filter.name)
        }
    }
}

/**
 * The searches run on this device, newest first, for the search screen's
 * "Recientes". A convenience kept in the app's settings, not a history: at
 * most [MAX] and gone with "Borrar".
 */
class RecentSearchStore(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _recent = MutableStateFlow(read())
    val recent: StateFlow<List<RecentSearch>> = _recent.asStateFlow()

    fun add(entry: RecentSearch) {
        if (entry.label.isBlank()) return
        val same = entry.copy(label = "")
        save((listOf(entry) + _recent.value.filterNot { it.copy(label = "") == same }).take(MAX))
    }

    fun clear() = save(emptyList())

    private fun read(): List<RecentSearch> =
        settings.getStringOrNull(KEY)
            ?.let { runCatching { json.decodeFromString<List<RecentSearch>>(it) }.getOrNull() }
            .orEmpty()
            .take(MAX)

    private fun save(list: List<RecentSearch>) {
        _recent.value = list
        if (list.isEmpty()) settings.remove(KEY)
        else settings.putString(KEY, json.encodeToString(list))
    }

    companion object {
        const val MAX = 8
        private const val KEY = "search.recent"
    }
}
