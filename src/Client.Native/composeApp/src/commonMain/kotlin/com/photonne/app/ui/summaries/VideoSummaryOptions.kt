package com.photonne.app.ui.summaries

import com.photonne.app.data.models.RequestVideoSummaryBody
import com.photonne.app.data.models.VideoSummary
import kotlin.math.roundToInt

/** What a summary video is made from: a calendar year or an album. */
sealed interface SummarySource {
    data class Year(val year: Int) : SummarySource
    data class Album(val albumId: String, val name: String) : SummarySource
}

/** Which material goes in by shape; a portrait summary also comes out portrait. */
enum class SummaryOrientation(val wire: String) {
    All("all"),
    Landscape("landscape"),
    Portrait("portrait");

    companion object {
        fun of(wire: String): SummaryOrientation = entries.firstOrNull { it.wire == wire } ?: All
    }
}

/** A track uploaded (on the web) to lay under the summary. */
data class SummaryMusic(val id: String, val name: String)

/**
 * The user's choices, the same as on the web: no photos, every shape, five
 * minutes, titles on, no music and straight to rendering, unless the last
 * request for that year or album said otherwise.
 */
data class SummaryOptions(
    val includePhotos: Boolean = false,
    val orientation: SummaryOrientation = SummaryOrientation.All,
    val durationMinutes: Int = DEFAULT_MINUTES,
    val showTitles: Boolean = true,
    val music: SummaryMusic? = null,
    val review: Boolean = false,
) {
    companion object {
        const val DEFAULT_MINUTES = 5

        /** The lengths the user can pick, in minutes. */
        val DURATIONS: List<Int> = (1..10).toList()

        /** Per-user setting: "true" to get the year's summary every December. */
        const val AUTO_YEAR_KEY = "VideoSummarySettings.AutoYear"

        /** The options an earlier request used, to start from them when asking again. */
        fun of(summary: VideoSummary) = SummaryOptions(
            includePhotos = summary.includePhotos,
            orientation = SummaryOrientation.of(summary.orientation),
            durationMinutes = summary.durationMinutes.takeIf { it in DURATIONS } ?: DEFAULT_MINUTES,
            showTitles = summary.showTitles,
            music = summary.musicId?.let { SummaryMusic(it, summary.musicName.orEmpty()) },
            review = summary.review,
        )
    }
}

/** The user's summary for this source, if they ever asked for one. */
fun List<VideoSummary>.forSource(source: SummarySource): VideoSummary? = firstOrNull {
    when (source) {
        is SummarySource.Year -> it.source == "year" && it.year == source.year
        is SummarySource.Album -> it.source == "album" && it.albumId == source.albumId
    }
}

/** Queued or rendering: the server is still on it. A "planned" one waits for the user. */
val VideoSummary.isActive: Boolean get() = status == "pending" || status == "running"

/** Whole percent for display, 0–100. */
val VideoSummary.percent: Int get() = (progress.coerceIn(0.0, 1.0) * 100).roundToInt()

fun requestBody(source: SummarySource, options: SummaryOptions) = RequestVideoSummaryBody(
    source = if (source is SummarySource.Year) "year" else "album",
    year = (source as? SummarySource.Year)?.year,
    albumId = (source as? SummarySource.Album)?.albumId,
    includePhotos = options.includePhotos,
    orientation = options.orientation.wire,
    durationMinutes = options.durationMinutes,
    showTitles = options.showTitles,
    musicId = options.music?.id,
    musicName = options.music?.name,
    review = options.review,
)
