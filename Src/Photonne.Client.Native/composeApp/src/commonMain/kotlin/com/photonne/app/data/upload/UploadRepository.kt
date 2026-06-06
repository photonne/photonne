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

    /** Streaming variant for large files — see [PhotonneApi.uploadAssetStream]. */
    suspend fun uploadStream(
        fileName: String,
        mimeType: String,
        source: kotlinx.io.Source,
        sizeBytes: Long,
        destination: String? = null,
        deviceName: String? = null
    ): UploadAssetResponse =
        api.uploadAssetStream(fileName, mimeType, source, sizeBytes, destination, deviceName)
}
