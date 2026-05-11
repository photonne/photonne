package com.photonne.app.ui.actions

/**
 * iOS does not have a real implementation yet — bridging
 * `UIActivityViewController` from Compose Multiplatform requires a
 * UIViewController interop layer that we'll add in a follow-up,
 * matching the upload picker stub. Each entry point throws
 * [AssetSharingUnavailable] so the calling view-model can surface a
 * localized "not supported on iOS yet" banner.
 */
actual class AssetSharing {
    actual suspend fun saveAsset(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile {
        throw AssetSharingUnavailable("Saving to the iOS Photos library is not implemented yet")
    }

    actual suspend fun saveZip(
        bytes: ByteArray,
        fileName: String
    ): SavedAssetFile {
        throw AssetSharingUnavailable("Saving ZIPs to the iOS Files app is not implemented yet")
    }

    actual suspend fun shareFiles(files: List<SavedAssetFile>, mimeType: String) {
        throw AssetSharingUnavailable("The iOS share sheet bridge is not implemented yet")
    }
}
