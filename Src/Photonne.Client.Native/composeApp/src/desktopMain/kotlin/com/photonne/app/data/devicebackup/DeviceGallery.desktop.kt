package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import javax.swing.JFileChooser
import kotlin.io.path.absolutePathString
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory

/**
 * Desktop implementation lets us exercise the rest of the device sync
 * flow without an Android device handy — useful while iterating on the
 * Compose UI and the upload pipeline. Backed by Swing `JFileChooser`
 * for folder picking and `Files.walk` for the recursive scan, with
 * the host JDK's `MessageDigest` doing the hashing.
 *
 * Image / video detection uses the file extension only; the desktop
 * has no equivalent to Android's `ContentResolver.getType`.
 */
actual class DeviceGallery {

    actual val isSupported: Boolean = true

    actual suspend fun restoreFolder(uri: String): DeviceFolderRef? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(URI(uri))
                if (!file.isDirectory) return@runCatching null
                DeviceFolderRef(uri = file.toURI().toString(), displayName = file.name)
            }.getOrNull()
        }

    actual suspend fun listMedia(folder: DeviceFolderRef): List<DeviceMedia> =
        withContext(Dispatchers.IO) {
            val root = File(URI(folder.uri)).toPath()
            val out = mutableListOf<DeviceMedia>()
            Files.walk(root).use { stream ->
                stream.forEach { path ->
                    if (path.isDirectory()) return@forEach
                    val type = mediaTypeFromExtension(path.extension) ?: return@forEach
                    val mime = mimeFromExtension(path.extension) ?: return@forEach
                    val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
                    val relative = root.relativize(path).parent?.toString().orEmpty()
                    out += DeviceMedia(
                        uri = path.toUri().toString(),
                        displayName = path.fileName.toString(),
                        relativePath = relative,
                        mimeType = mime,
                        sizeBytes = runCatching { path.fileSize() }.getOrDefault(0L),
                        dateModifiedMillis = attrs.lastModifiedTime().toMillis(),
                        type = type
                    )
                }
            }
            out.sortedByDescending { it.dateModifiedMillis }
        }

    actual suspend fun computeSha256(media: DeviceMedia): String =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            val file = File(URI(media.uri))
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    actual suspend fun <T> withUploadSource(
        media: DeviceMedia,
        block: suspend (source: Source, sizeBytes: Long) -> T
    ): T = withContext(Dispatchers.IO) {
        val file = File(URI(media.uri))
        file.inputStream().use { stream ->
            block(stream.asSource().buffered(), file.length())
        }
    }

    actual fun thumbnailModel(media: DeviceMedia): String = media.uri

    actual suspend fun deleteFile(media: DeviceMedia): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(URI(media.uri))
                !file.exists() || file.delete()
            }.getOrDefault(false)
        }
}

private fun mediaTypeFromExtension(ext: String): DeviceMediaType? =
    when (ext.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> DeviceMediaType.Image
        "mp4", "mov", "m4v", "mkv", "webm", "avi" -> DeviceMediaType.Video
        else -> null
    }

private fun mimeFromExtension(ext: String): String? = when (ext.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    "bmp" -> "image/bmp"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "m4v" -> "video/x-m4v"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "avi" -> "video/x-msvideo"
    else -> null
}

@Composable
actual fun rememberDeviceFolderPicker(
    gallery: DeviceGallery,
    onPicked: (DeviceFolderRef?) -> Unit
): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return {
        scope.launch {
            val ref = withContext(Dispatchers.IO) {
                val chooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Pick a folder to sync"
                }
                val result = chooser.showOpenDialog(null)
                if (result != JFileChooser.APPROVE_OPTION) return@withContext null
                val file = chooser.selectedFile ?: return@withContext null
                DeviceFolderRef(uri = file.toURI().toString(), displayName = file.name)
            }
            currentOnPicked.value(ref)
        }
    }
}
