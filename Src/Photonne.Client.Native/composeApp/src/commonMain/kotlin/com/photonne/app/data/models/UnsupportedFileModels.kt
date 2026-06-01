package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A file found on disk that the app can't display (unknown extension). Surfaced
 * in the "Otros archivos" screen so the user sees everything physically present
 * in their storage and can download the original.
 */
@Serializable
data class UnsupportedFileItem(
    val id: String,
    val fileName: String,
    val fullPath: String,
    val fileSize: Long,
    val extension: String,
    @Serializable(with = FlexibleInstantSerializer::class) val fileCreatedAt: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val discoveredAt: Instant
)

@Serializable
data class UnsupportedFilesPage(
    val items: List<UnsupportedFileItem> = emptyList(),
    val hasMore: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class) val nextCursor: Instant? = null
)
