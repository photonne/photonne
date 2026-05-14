package com.photonne.app.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the timeline groups its assets into headers. */
enum class TimelineGrouping { Year, Month, Day }

/**
 * Discrete zoom levels for the Timeline grid, ordered from most
 * zoomed-out (largest time bucket, smallest cells) to most zoomed-in
 * (smallest time bucket, largest cells). Pinch-in steps through this
 * list in declaration order, so cell size must grow monotonically —
 * within Day grouping that means S → M → L (not L → M → S).
 * [cellMinSizeDp] feeds
 * [androidx.compose.foundation.lazy.grid.GridCells.Adaptive] — kept as a
 * plain Int here so this enum stays in the data layer with no Compose
 * dependency.
 */
enum class TimelineZoomLevel(val cellMinSizeDp: Int, val grouping: TimelineGrouping) {
    Year(cellMinSizeDp = 35, grouping = TimelineGrouping.Year),
    Month(cellMinSizeDp = 70, grouping = TimelineGrouping.Month),
    DaySmall(cellMinSizeDp = 90, grouping = TimelineGrouping.Day),
    DayMedium(cellMinSizeDp = 120, grouping = TimelineGrouping.Day),
    DayLarge(cellMinSizeDp = 165, grouping = TimelineGrouping.Day);

    companion object {
        val Default: TimelineZoomLevel = Month
    }
}

/**
 * Holds the user's chosen [TimelineZoomLevel] and persists it via the
 * platform [Settings] backend, mirroring [ThemePreferenceStore]. Reads
 * the value on construction so the grid renders at the preferred zoom
 * on first frame.
 */
class TimelineZoomStore(private val settings: Settings) {
    private val _value = MutableStateFlow(load())
    val value: StateFlow<TimelineZoomLevel> = _value.asStateFlow()

    fun update(level: TimelineZoomLevel) {
        if (_value.value == level) return
        settings.putString(KEY, level.name)
        _value.value = level
    }

    private fun load(): TimelineZoomLevel {
        val raw = settings.getStringOrNull(KEY) ?: return TimelineZoomLevel.Default
        return runCatching { TimelineZoomLevel.valueOf(raw) }
            .getOrDefault(TimelineZoomLevel.Default)
    }

    private companion object {
        const val KEY = "photonne.timeline_zoom_level"
    }
}
