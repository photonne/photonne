package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw GPS-tagged asset returned by `/api/assets/map/points`. The client
 * clusters these on-the-fly in screen space so that markers split and
 * merge smoothly as the user pans and zooms — same UX as the PWA's
 * Leaflet.markercluster.
 */
@Serializable
data class MapPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("hasThumbnail") val hasThumbnail: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class) val date: Instant
)
