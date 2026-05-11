package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cursor-paginated response shared by `/api/assets/archived` and
 * `/api/assets/trash`. The backend uses PascalCase here, so map the JSON
 * keys explicitly to keep the Kotlin model idiomatic.
 */
@Serializable
data class AssetPage(
    @SerialName("Items") val items: List<TimelineItem> = emptyList(),
    @SerialName("HasMore") val hasMore: Boolean = false,
    @SerialName("NextCursor")
    @Serializable(with = FlexibleInstantSerializer::class)
    val nextCursor: Instant? = null
)
