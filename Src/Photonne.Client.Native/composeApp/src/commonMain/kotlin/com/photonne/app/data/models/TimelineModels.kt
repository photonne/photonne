package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

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
    val isReadOnly: Boolean = false
) {
    val isVideo: Boolean get() = type.equals("VIDEO", ignoreCase = true)
}

@Serializable
data class TimelinePage(
    val items: List<TimelineItem> = emptyList(),
    val hasMore: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class) val nextCursor: Instant? = null
)
