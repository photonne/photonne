package com.photonne.app.data.folder

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.TimelineItem

class FoldersRepository(
    private val api: PhotonneApi
) {
    suspend fun list(): List<FolderSummary> = api.getFolders()

    suspend fun get(folderId: String): FolderSummary = api.getFolder(folderId)

    suspend fun assets(folderId: String): List<TimelineItem> = api.getFolderAssets(folderId)
}
