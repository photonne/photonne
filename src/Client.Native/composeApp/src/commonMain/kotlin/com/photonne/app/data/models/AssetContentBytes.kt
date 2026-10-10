package com.photonne.app.data.models

/**
 * Raw asset bytes returned by `GET /api/assets/{id}/content`. The
 * platform layer needs both the payload and the server-declared MIME
 * type / suggested filename so it can save with the right extension
 * and show the share sheet under the correct preview app.
 */
data class AssetContentBytes(
    val bytes: ByteArray,
    val mimeType: String,
    val suggestedFileName: String
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = mimeType.hashCode() * 31 + suggestedFileName.hashCode()
}
