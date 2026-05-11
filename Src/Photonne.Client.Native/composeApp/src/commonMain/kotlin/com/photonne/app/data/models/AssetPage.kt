package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Cursor-paginated response shared by `/api/assets/archived` and `/api/assets/trash`. */
@Serializable
data class AssetPage(
    val items: List<TimelineItem> = emptyList(),
    val hasMore: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class)
    val nextCursor: Instant? = null
)
