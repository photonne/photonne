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
        deviceName: String? = null,
        fileModifiedAtMillis: Long? = null,
        fileCreatedAtMillis: Long? = null
    ): UploadAssetResponse = api.uploadAsset(
        fileName, mimeType, bytes, destination, deviceName,
        fileModifiedAtMillis, fileCreatedAtMillis
    )

    /** Whether the server still lacks the motion clip of the still [assetId]. */
    suspend fun motionClipMissing(assetId: String): Boolean =
        assetId in api.motionClipsMissing(listOf(assetId))

    /** Attaches a Live Photo's motion clip, read whole, to its still [assetId]. */
    suspend fun attachMotionClip(assetId: String, fileName: String, mimeType: String, bytes: ByteArray) {
        val source = kotlinx.io.Buffer().apply { write(bytes) }
        api.attachMotionClip(assetId, fileName, mimeType, source, bytes.size.toLong())
    }

    /** Streaming variant for large files — see [PhotonneApi.uploadAssetStream]. */
    suspend fun uploadStream(
        fileName: String,
        mimeType: String,
        source: kotlinx.io.Source,
        sizeBytes: Long,
        destination: String? = null,
        deviceName: String? = null,
        fileModifiedAtMillis: Long? = null,
        fileCreatedAtMillis: Long? = null,
        onProgress: ((bytesSent: Long, totalBytes: Long) -> Unit)? = null
    ): UploadAssetResponse = api.uploadAssetStream(
        fileName, mimeType, source, sizeBytes, destination, deviceName,
        fileModifiedAtMillis, fileCreatedAtMillis, onProgress
    )
}
