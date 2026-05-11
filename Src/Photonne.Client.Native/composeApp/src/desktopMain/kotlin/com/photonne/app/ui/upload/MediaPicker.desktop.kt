package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberMediaPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val files = withContext(Dispatchers.IO) { runPicker() }
            onPicked(files)
        }
    }
}

private fun runPicker(): List<PickedFile> {
    val chooser = JFileChooser().apply {
        isMultiSelectionEnabled = true
        dialogTitle = "Select photos and videos"
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = FileNameExtensionFilter(
            "Photos & videos",
            "jpg", "jpeg", "png", "heic", "heif", "webp", "gif",
            "mp4", "mov", "m4v", "avi", "mkv"
        )
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return emptyList()
    return chooser.selectedFiles.orEmpty().mapNotNull { file -> file.toPickedFile() }
}

private fun File.toPickedFile(): PickedFile? = runCatching {
    val bytes = readBytes()
    PickedFile(
        name = name,
        mimeType = guessMimeType(extension.lowercase()),
        sizeBytes = bytes.size.toLong(),
        bytes = bytes
    )
}.getOrNull()

private fun guessMimeType(ext: String): String = when (ext) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "heic", "heif" -> "image/heic"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "mp4", "m4v" -> "video/mp4"
    "mov" -> "video/quicktime"
    "avi" -> "video/x-msvideo"
    "mkv" -> "video/x-matroska"
    else -> "application/octet-stream"
}
