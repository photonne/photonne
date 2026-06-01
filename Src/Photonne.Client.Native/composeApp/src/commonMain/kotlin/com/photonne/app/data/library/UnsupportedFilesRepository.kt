package com.photonne.app.data.library

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.AssetContentBytes
import com.photonne.app.data.models.UnsupportedFilesPage
import kotlinx.datetime.Instant

class UnsupportedFilesRepository(
    private val api: PhotonneApi
) {
    suspend fun listUnsupportedFiles(cursor: Instant? = null): UnsupportedFilesPage =
        api.listUnsupportedFiles(cursor)

    suspend fun downloadOriginal(id: String): AssetContentBytes =
        api.getUnsupportedFileContent(id)
}
