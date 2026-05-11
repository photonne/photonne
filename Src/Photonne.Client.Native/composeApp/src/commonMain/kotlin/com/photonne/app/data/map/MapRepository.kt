package com.photonne.app.data.map

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.MapCluster

class MapRepository(
    private val api: PhotonneApi
) {
    suspend fun clusters(
        zoom: Int,
        minLat: Double? = null,
        minLng: Double? = null,
        maxLat: Double? = null,
        maxLng: Double? = null
    ): List<MapCluster> = api.getMapClusters(
        zoom = zoom,
        minLat = minLat,
        minLng = minLng,
        maxLat = maxLat,
        maxLng = maxLng
    )
}
