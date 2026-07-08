package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * One asset deleted from a shared folder, as surfaced by
 * `GET /api/assets/shared-trash`. Carries the admin-facing attribution
 * (who deleted it, from which folder) that the plain trash listing lacks.
 */
@Serializable
data class SharedTrashItem(
    val id: String,
    val fileName: String = "",
    val fullPath: String = "",
    val fileSize: Long = 0,
    val type: String = "",
    val extension: String = "",
    val hasThumbnails: Boolean = false,
    val width: Int? = null,
    val height: Int? = null,
    @Serializable(with = FlexibleInstantSerializer::class)
    val deletedAt: Instant? = null,
    val deletedByUsername: String? = null,
    val deletedFromPath: String? = null,
    val deletedFromFolderName: String? = null
)

/** Cursor-paginated response of `GET /api/assets/shared-trash`. */
@Serializable
data class SharedTrashPage(
    val items: List<SharedTrashItem> = emptyList(),
    val hasMore: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class)
    val nextCursor: Instant? = null
)
