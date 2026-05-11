package com.photonne.app.ui.actions

import java.awt.Desktop
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
 * we open the first file and rely on the user dragging the rest
 * — documented in the share dialog copy.
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

    actual suspend fun shareFiles(files: List<SavedAssetFile>, mimeType: String) {
        if (files.isEmpty()) return
        withContext(Dispatchers.IO) {
            if (!Desktop.isDesktopSupported()) {
                throw AssetSharingUnavailable("OS share not supported on this desktop")
            }
            val desktop = Desktop.getDesktop()
            val target = File(files.first().path)
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                desktop.open(target)
            } else {
                throw AssetSharingUnavailable("OS share not supported on this desktop")
            }
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
