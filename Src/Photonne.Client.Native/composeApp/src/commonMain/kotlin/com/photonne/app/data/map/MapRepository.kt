package com.photonne.app.data.map

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.MapPoint

class MapRepository(
    private val api: PhotonneApi
) {
    /**
     * One-shot fetch of every GPS-tagged asset the caller can see. The
     * payload is small (~30 bytes per point) so the client trades a few
     * hundred KB of memory for instant marker recomputation as the user
     * pans and zooms.
     */
    suspend fun points(): List<MapPoint> = api.getMapPoints()
}
