package com.photonne.app.ui.actions

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path

/**
 * Desktop variant: "save to downloads" pops a JFileChooser asking
 * the user where to put the file; "share" hands the file to the OS
 * default app via `java.awt.Desktop.open`, which on most platforms
 * surfaces a system share-sheet-equivalent. There's no concept of
 * `ACTION_SEND_MULTIPLE` on the desktop, so for a multi-file share
 * we open the temp folder holding them and rely on the user dragging
 * them where they're needed.
 */
actual class AssetSharing {

    /**
     * Asks where first (the dialog shows once the server has answered, so
     * the suggested name is the real one) and then streams into that file.
     */
    actual suspend fun openDownload(
        fileName: String,
        mimeType: String
    ): DownloadSink = withContext(Dispatchers.IO) {
        val extensions = if (mimeType == "application/zip") listOf("zip") else allExtensions(mimeType)
        // Closing the dialog is the user cancelling, not a failure: callers
        // treat a CancellationException as "stop quietly" instead of showing
        // "No se pudo descargar".
        val path = pickSavePath(fileName, extensions)
            ?: throw CancellationException("Save cancelled")
        FileDownloadSink(target = Path(path), displayName = File(path).name, mimeType = mimeType)
    }

    /** Temp directory, not a save dialog: "share" stages, it doesn't ask
     *  where to keep a copy (that's what Download is for). */
    actual suspend fun openShareStage(
        fileName: String,
        mimeType: String
    ): DownloadSink = withContext(Dispatchers.IO) {
        val safeName = fileName.replace('/', '_').replace('\\', '_').ifBlank { "shared" }
        val target = File(shareDir(), safeName)
        FileDownloadSink(target = Path(target.absolutePath), displayName = safeName, mimeType = mimeType)
    }

    actual suspend fun clearShareCache() = withContext(Dispatchers.IO) {
        shareDir().listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }

    private fun shareDir(): File =
        File(System.getProperty("java.io.tmpdir"), "photonne-share").apply { mkdirs() }

    actual suspend fun shareFiles(files: List<SavedAssetFile>, mimeType: String) {
        if (files.isEmpty()) return
        withContext(Dispatchers.IO) {
            if (!Desktop.isDesktopSupported()) {
                throw AssetSharingUnavailable("OS share not supported on this desktop")
            }
            val desktop = Desktop.getDesktop()
            // No share sheet on the desktop: one file opens in its default app
            // as before; several open the folder holding them, ready to drag.
            val target = if (files.size == 1) {
                File(files.first().path)
            } else {
                File(files.first().path).parentFile ?: File(files.first().path)
            }
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(target)
            } else {
                throw AssetSharingUnavailable("OS share not supported on this desktop")
            }
        }
    }

    /**
     * El escritorio no tiene hoja de compartir de texto: lo más cercano y
     * útil es dejar el enlace en el portapapeles del sistema.
     */
    actual suspend fun shareText(text: String, subject: String?) {
        if (text.isBlank()) return
        withContext(Dispatchers.IO) {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                ?: throw AssetSharingUnavailable("No system clipboard available")
            clipboard.setContents(StringSelection(text), null)
        }
    }

    /** Swing components belong on the event dispatch thread; callers come in
     *  from Dispatchers.IO, so the dialog is shown there and waited for. */
    private fun pickSavePath(defaultName: String, extensions: List<String>): String? {
        var picked: String? = null
        val show = {
            val chooser = JFileChooser().apply {
                dialogTitle = "Save as"
                selectedFile = File(defaultName)
                if (extensions.isNotEmpty()) {
                    fileFilter = FileNameExtensionFilter(
                        extensions.joinToString(", ") { ".$it" },
                        *extensions.toTypedArray()
                    )
                }
            }
            val result = chooser.showSaveDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) picked = chooser.selectedFile.absolutePath
        }
        if (SwingUtilities.isEventDispatchThread()) show() else SwingUtilities.invokeAndWait(show)
        return picked
    }

    private fun allExtensions(mimeType: String): List<String> = when {
        mimeType.startsWith("image/") -> listOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif")
        mimeType.startsWith("video/") -> listOf("mp4", "mov", "m4v", "avi", "mkv")
        else -> emptyList()
    }
}
