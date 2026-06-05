package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class TimelineItem(
    val id: String,
    val fileName: String,
    val fullPath: String,
    val fileSize: Long,
    @Serializable(with = FlexibleInstantSerializer::class) val fileCreatedAt: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val fileModifiedAt: Instant,
    val extension: String,
    @Serializable(with = FlexibleInstantSerializer::class) val scannedAt: Instant,
    val type: String,
    val checksum: String? = null,
    val hasExif: Boolean = false,
    val hasThumbnails: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isFileMissing: Boolean = false,
    val dominantColor: String? = null,
    val isReadOnly: Boolean = false,
    // Local-only fields populated when the timeline is interleaved with
    // device-pending entries from the Backup module. The server never
    // emits these, so they're @Transient to stay out of serialization.
    @Transient val localThumbnailModel: String? = null,
    @Transient val localUri: String? = null,
    @Transient val localSyncBadge: LocalSyncBadge? = null
) {
    val isVideo: Boolean get() = type.equals("VIDEO", ignoreCase = true)
    val isLocalOnly: Boolean get() = localUri != null

    /**
     * iOS Live Photo: a still paired with a short motion clip. The server
     * tags these (sibling `.mov` next to the image) and the tag rides along
     * in [tags]; a server asset can offer its motion clip at
     * `/api/assets/{id}/motion`. Device-local entries can't be played in
     * place, so they never count as Live here.
     */
    val isLivePhoto: Boolean
        get() = !isLocalOnly && !isVideo &&
            tags.any { it.equals("LivePhoto", ignoreCase = true) }
}

/** Sync state surfaced as a small badge over a device-pending thumbnail. */
enum class LocalSyncBadge { Pending, Uploading, Failed }

@Serializable
data class TimelinePage(
    val items: List<TimelineItem> = emptyList(),
    val hasMore: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class) val nextCursor: Instant? = null
)

/**
 * One calendar month of the timeline skeleton, as served by
 * `GET /api/assets/timeline/buckets`. [count] is the server-side contract
 * for how many assets `GET /api/assets/timeline/buckets/{key}` returns —
 * the grid reserves scroll height from it before any asset data arrives.
 */
@Serializable
data class TimelineBucket(
    /** Calendar month key, "yyyy-MM" (e.g. "2026-06"). */
    val key: String,
    val count: Int
)

/**
 * One year of the compressed yearly view (`GET /api/assets/timeline/years`):
 * total visible-asset count plus a sample distributed evenly across the
 * year, newest first. The Year zoom level renders a fixed number of rows
 * per year from [items] and shows [count] in the header.
 */
@Serializable
data class TimelineYearSummary(
    val year: Int,
    val count: Int,
    val items: List<TimelineItem> = emptyList()
)
