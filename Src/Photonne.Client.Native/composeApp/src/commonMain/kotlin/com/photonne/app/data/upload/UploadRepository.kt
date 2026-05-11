package com.photonne.app.data.upload

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.UploadAssetResponse

class UploadRepository(
    private val api: PhotonneApi
) {
    suspend fun upload(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): UploadAssetResponse = api.uploadAsset(fileName, mimeType, bytes)
}
