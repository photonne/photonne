package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A pre-clustered group of GPS-tagged assets. Mirrors the backend's
 * `MapClusterResponse`: positions are pre-aggregated and visually
 * de-overlapped server-side based on the requested zoom level, so the
 * client only renders.
 */
@Serializable
data class MapCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val count: Int = 1,
    val assetIds: List<String> = emptyList(),
    @Serializable(with = FlexibleInstantSerializer::class)
    val earliestDate: Instant,
    @Serializable(with = FlexibleInstantSerializer::class)
    val latestDate: Instant,
    val firstAssetId: String? = null,
    val hasThumbnail: Boolean = false
)
