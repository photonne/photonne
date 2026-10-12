package com.photonne.app.ui.actions

/**
 * Platform glue for the "Share" and "Download" actions on the asset
 * selection top bar.
 *
 * - [openDownload] opens a file in the OS-managed downloads location
 *   (an asset, a multi-asset ZIP) that the caller streams into; its
 *   commit says where it landed so the caller can surface a snackbar.
 *   [saveAsset] is the same for a payload already in memory.
 * - [shareText] hands a link (plain text) to the same sheet.
 * - [openShareStage] opens one file in the app's PRIVATE share cache
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
 * iOS has no Downloads folder: a finished download opens the share
 * sheet so the user saves it where they want.
 */
expect class AssetSharing {
    /**
     * Opens [fileName] where downloads go, ready to be written while the
     * bytes arrive (see [drainInto]). Throws [AssetSharingUnavailable] when
     * the user backs out of choosing where (desktop).
     */
    suspend fun openDownload(fileName: String, mimeType: String): DownloadSink

    /** Opens [fileName] in the private share cache, to be streamed into. */
    suspend fun openShareStage(fileName: String, mimeType: String): DownloadSink

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
