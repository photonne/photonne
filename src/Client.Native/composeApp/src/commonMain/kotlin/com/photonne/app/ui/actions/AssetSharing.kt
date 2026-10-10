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
 * - [shareText] hands a link (plain text) to the same sheet.
 * - [stageForShare] writes one file into the app's PRIVATE share cache
 *   (never the gallery nor Downloads): sharing must not leave a local
 *   copy behind — it duplicated the photo on the phone and the backup
 *   then picked it up as new. [clearShareCache] drops the previous
 *   share's files first.
 * - [shareFiles] hands a list of staged files to the OS share sheet
 *   (`Intent.ACTION_SEND` / `ACTION_SEND_MULTIPLE` on Android,
 *   `UIActivityViewController` on iOS, default app association on
 *   Desktop). Several photos go as several files, never a ZIP: chat
 *   apps show a ZIP as a document, not as photos.
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

    /** Writes [bytes] into the private share cache and returns its location. */
    suspend fun stageForShare(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile

    /** Deletes whatever earlier shares left in the private share cache. */
    suspend fun clearShareCache()

    suspend fun shareFiles(files: List<SavedAssetFile>, mimeType: String)

    /**
     * Entrega [text] (un enlace de compartición) a la hoja del sistema.
     * Distinto de [shareFiles]: aquí no hay fichero que escribir, solo el
     * enlace que el usuario quiere mandar por WhatsApp, correo o lo que use.
     */
    suspend fun shareText(text: String, subject: String? = null)
}

/** Location where a saved asset / zip lives so it can be re-used by the share sheet. */
data class SavedAssetFile(
    val path: String,
    val displayName: String,
    val mimeType: String
)

/** Thrown by platforms that haven't shipped a real share or save yet. */
class AssetSharingUnavailable(message: String) : RuntimeException(message)
