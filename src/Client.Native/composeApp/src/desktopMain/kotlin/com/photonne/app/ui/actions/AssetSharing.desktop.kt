package com.photonne.app.ui.actions

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    actual suspend fun saveAsset(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile = withContext(Dispatchers.IO) {
        val path = pickSavePath(fileName, allExtensions(mimeType))
            ?: throw AssetSharingUnavailable("Save cancelled")
        File(path).writeBytes(bytes)
        SavedAssetFile(path = path, displayName = File(path).name, mimeType = mimeType)
    }

    actual suspend fun saveZip(
        bytes: ByteArray,
        fileName: String
    ): SavedAssetFile = withContext(Dispatchers.IO) {
        val path = pickSavePath(fileName, listOf("zip"))
            ?: throw AssetSharingUnavailable("Save cancelled")
        File(path).writeBytes(bytes)
        SavedAssetFile(path = path, displayName = File(path).name, mimeType = "application/zip")
    }

    /** Temp directory, not a save dialog: "share" stages, it doesn't ask
     *  where to keep a copy (that's what Download is for). */
    actual suspend fun stageForShare(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile = withContext(Dispatchers.IO) {
        val safeName = fileName.replace('/', '_').replace('\\', '_').ifBlank { "shared" }
        val target = File(shareDir(), safeName)
        target.writeBytes(bytes)
        SavedAssetFile(path = target.absolutePath, displayName = safeName, mimeType = mimeType)
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

    private fun pickSavePath(defaultName: String, extensions: List<String>): String? {
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
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath
        else null
    }

    private fun allExtensions(mimeType: String): List<String> = when {
        mimeType.startsWith("image/") -> listOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif")
        mimeType.startsWith("video/") -> listOf("mp4", "mov", "m4v", "avi", "mkv")
        else -> emptyList()
    }
}
