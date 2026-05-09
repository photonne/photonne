package com.photonne.app.data.timeline

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.TimelineItem

class MemoriesRepository(
    private val api: PhotonneApi
) {
    suspend fun list(): List<TimelineItem> = api.getMemories()
}
