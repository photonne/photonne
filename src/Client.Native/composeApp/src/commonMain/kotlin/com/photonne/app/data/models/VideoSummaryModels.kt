package com.photonne.app.data.models

import kotlinx.serialization.Serializable

/**
 * A summary video the user asked for (GET /api/video-summaries): the summary
 * of a year or of an album, rendered by the server into the user's «Recuerdos»
 * folder. One per year or album; asking again renders it again.
 */
@Serializable
data class VideoSummary(
    val id: String,
    /** "year" or "album". */
    val source: String,
    val year: Int? = null,
    val albumId: String? = null,
    val title: String,
    val includePhotos: Boolean = false,
    /** "all", "landscape" or "portrait". */
    val orientation: String = "all",
    val durationMinutes: Int = 5,
    val showTitles: Boolean = true,
    val musicId: String? = null,
    val musicName: String? = null,
    val review: Boolean = false,
    val hasPlan: Boolean = false,
    /** "pending", "running", "planned", "completed" or "failed". */
    val status: String,
    /** 0..1 while running. */
    val progress: Double = 0.0,
    val error: String? = null,
    /** The rendered video, once there is one (null again if it went to the trash). */
    val assetId: String? = null,
    val clipCount: Int = 0,
)

/** A year with material for a summary (GET /api/video-summaries/years). */
@Serializable
data class VideoSummaryYear(val year: Int, val videos: Int, val photos: Int)

/** POST /api/video-summaries. Exactly one of [year] and [albumId], as [source] says. */
@Serializable
data class RequestVideoSummaryBody(
    val source: String,
    val year: Int? = null,
    val albumId: String? = null,
    val includePhotos: Boolean,
    val orientation: String,
    val durationMinutes: Int,
    val showTitles: Boolean,
    val musicId: String? = null,
    val musicName: String? = null,
    val review: Boolean,
)
