package com.photonne.app.data.upload

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.api.UploadAssetResponse

class UploadRepository(
    private val api: PhotonneApi
) {
    suspend fun upload(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        destination: String? = null,
        deviceName: String? = null
    ): UploadAssetResponse = api.uploadAsset(fileName, mimeType, bytes, destination, deviceName)
}
