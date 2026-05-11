package com.photonne.app.ui.actions

/**
 * Platform glue for the "Share" and "Download" actions on the asset
 * selection top bar.
 *
 * - [saveAsset] writes a single asset (image or video original) into
 *   the OS-managed downloads location, returning where it landed so
 *   the caller can surface a snackbar.
 * - [saveZip] writes a multi-asset zip into the same place; same
 *   contract.
 * - [shareFiles] hands a list of locally-stored files to the OS share
 *   sheet (`Intent.ACTION_SEND_MULTIPLE` on Android, `Activity` view
 *   controller on iOS, default app association on Desktop). The
 *   caller is expected to have written the files to disk via
 *   [saveAsset] / [saveZip] first.
 *
 * iOS does not have a real implementation yet; the actual just
 * throws so the surrounding view-model can surface a localized
 * "not implemented" banner.
 */
expect class AssetSharing {
    suspend fun saveAsset(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile

    suspend fun saveZip(
        bytes: ByteArray,
        fileName: String
    ): SavedAssetFile

    suspend fun shareFiles(files: List<SavedAssetFile>, mimeType: String)
}

/** Location where a saved asset / zip lives so it can be re-used by the share sheet. */
data class SavedAssetFile(
    val path: String,
    val displayName: String,
    val mimeType: String
)

/** Thrown by platforms that haven't shipped a real share or save yet. */
class AssetSharingUnavailable(message: String) : RuntimeException(message)
