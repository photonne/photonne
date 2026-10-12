package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
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
    return chooser.selectedFiles.orEmpty().map { file -> file.toPickedFile() }
}

/**
 * Only the file's reference and metadata: its bytes are streamed from disk
 * when the queue uploads it. One that can't be read is reported, not dropped.
 */
private fun File.toPickedFile(): PickedFile = runCatching {
    if (!isFile || !canRead()) return@runCatching unreadablePickedFile(name)
    PickedFile(
        name = name,
        mimeType = guessMimeType(extension.lowercase()),
        sizeBytes = length(),
        content = FileContent(this),
        lastModifiedMillis = lastModified().takeIf { it > 0 }
    )
}.getOrElse { unreadablePickedFile(name) }

/** Streams [file] from disk; its size is re-read on open in case it changed since picking. */
private class FileContent(private val file: File) : PickedContent {
    override suspend fun <T> open(block: suspend (source: Source, sizeBytes: Long) -> T): T {
        val path = Path(file.absolutePath)
        val size = SystemFileSystem.metadataOrNull(path)?.size
            ?: throw java.io.FileNotFoundException(file.absolutePath)
        return SystemFileSystem.source(path).buffered().use { block(it, size) }
    }

    override val thumbnailModel: Any get() = file
}

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
