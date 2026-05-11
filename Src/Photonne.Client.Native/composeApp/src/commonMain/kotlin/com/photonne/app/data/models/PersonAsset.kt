package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/** Slim asset row returned by `/api/search/people/{id}/assets`. The
 * server only sends what's needed to render an `AssetGrid` cell — the
 * viewer re-fetches full `AssetDetail` when a user taps a photo. */
@Serializable
data class PersonAsset(
    val id: String,
    val fileName: String = "",
    val type: String = "Image",
    @Serializable(with = FlexibleInstantSerializer::class) val fileCreatedAt: Instant,
    val hasThumbnails: Boolean = false,
    val dominantColor: String? = null
)

@Serializable
data class PersonAssetsPage(
    val total: Int = 0,
    val items: List<PersonAsset> = emptyList()
)

/** Map a [PersonAsset] into a synthetic [TimelineItem] so it can flow
 * through `AssetGrid` and `AssetDetailScreen` without parallel paths. */
fun PersonAsset.toTimelineItem(): TimelineItem = TimelineItem(
    id = id,
    fileName = fileName,
    fullPath = "",
    fileSize = 0L,
    fileCreatedAt = fileCreatedAt,
    fileModifiedAt = fileCreatedAt,
    extension = "",
    scannedAt = fileCreatedAt,
    type = type,
    hasThumbnails = hasThumbnails,
    dominantColor = dominantColor
)
