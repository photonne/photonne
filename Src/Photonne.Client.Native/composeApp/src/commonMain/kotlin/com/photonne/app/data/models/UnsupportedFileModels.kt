package com.photonne.app.data.models

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * A file found on disk that the app can't display (unknown extension). Surfaced
 * in the "Archivos no compatibles" screen so the user sees everything physically present
 * in their storage and can download the original — or delete it, when the
 * server says they may ([canDelete]; false from servers that predate it).
 */
@Serializable
data class UnsupportedFileItem(
    val id: String,
    val fileName: String,
    val fullPath: String,
    val fileSize: Long,
    val extension: String,
    @Serializable(with = FlexibleInstantSerializer::class) val fileCreatedAt: Instant,
    @Serializable(with = FlexibleInstantSerializer::class) val discoveredAt: Instant,
    val canDelete: Boolean = false
)

@Serializable
data class UnsupportedFilesPage(
    val items: List<UnsupportedFileItem> = emptyList(),
    val hasMore: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class) val nextCursor: Instant? = null
)
