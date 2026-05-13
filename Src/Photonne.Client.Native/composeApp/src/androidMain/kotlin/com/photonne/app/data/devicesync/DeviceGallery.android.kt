package com.photonne.app.data.devicesync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Android implementation backed by the Storage Access Framework.
 *
 * - `pickFolder()` (composable below) launches `ACTION_OPEN_DOCUMENT_TREE`,
 *   takes a persistable read permission so we can re-open the same
 *   tree on a later app launch, and returns the tree URI.
 * - `listMedia()` walks the picked tree with [DocumentFile.listFiles]
 *   recursively, filtering for image and video MIME types. SAF child
 *   queries are slow on big trees, so the walk happens on `IO`.
 * - `computeSha256()` streams the file in 64 KiB chunks through
 *   `MessageDigest` so we never hold the full payload to compute the
 *   server-side dedup key.
 * - `readBytes()` does load the full payload — required because the
 *   existing upload pipeline takes a `ByteArray`. The caller releases
 *   the reference as soon as the upload returns.
 */
actual class DeviceGallery(private val context: Context) {

    actual val isSupported: Boolean = true

    actual suspend fun restoreFolder(uri: String): DeviceFolderRef? =
        withContext(Dispatchers.IO) {
            val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
            val granted = context.contentResolver.persistedUriPermissions.any {
                it.uri == parsed && it.isReadPermission
            }
            if (!granted) return@withContext null
            val doc = DocumentFile.fromTreeUri(context, parsed) ?: return@withContext null
            if (!doc.isDirectory) return@withContext null
            DeviceFolderRef(
                uri = parsed.toString(),
                displayName = doc.name ?: parsed.lastPathSegment ?: "Folder"
            )
        }

    actual suspend fun listMedia(folder: DeviceFolderRef): List<DeviceMedia> =
        withContext(Dispatchers.IO) {
            val parsed = Uri.parse(folder.uri)
            val root = DocumentFile.fromTreeUri(context, parsed) ?: return@withContext emptyList()
            val collected = mutableListOf<DeviceMedia>()
            walk(root, relativePath = "", out = collected)
            collected.sortedByDescending { it.dateModifiedMillis }
        }

    private fun walk(
        node: DocumentFile,
        relativePath: String,
        out: MutableList<DeviceMedia>
    ) {
        val children = runCatching { node.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isDirectory) {
                val name = child.name ?: continue
                val nextPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                walk(child, nextPath, out)
                continue
            }
            val mime = child.type ?: continue
            val type = when {
                mime.startsWith("image/") -> DeviceMediaType.Image
                mime.startsWith("video/") -> DeviceMediaType.Video
                else -> null
            } ?: continue
            val displayName = child.name ?: continue
            out += DeviceMedia(
                uri = child.uri.toString(),
                displayName = displayName,
                relativePath = relativePath,
                mimeType = mime,
                sizeBytes = child.length(),
                dateModifiedMillis = child.lastModified(),
                type = type
            )
        }
    }

    actual suspend fun computeSha256(media: DeviceMedia): String =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            val uri = Uri.parse(media.uri)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open ${media.displayName} for hashing" }
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                }
            }
            digest.digest().toHexLower()
        }

    actual suspend fun readBytes(media: DeviceMedia): ByteArray =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(media.uri)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open ${media.displayName} for upload" }
                input.readBytes()
            }
        }

    actual fun thumbnailModel(media: DeviceMedia): String = media.uri
}

private fun ByteArray.toHexLower(): String = buildString(size * 2) {
    for (b in this@toHexLower) {
        val v = b.toInt() and 0xff
        append(HEX[v ushr 4])
        append(HEX[v and 0x0f])
    }
}

private val HEX = "0123456789abcdef".toCharArray()

@Composable
actual fun rememberDeviceFolderPicker(
    gallery: DeviceGallery,
    onPicked: (DeviceFolderRef?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnPicked = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) {
            currentOnPicked.value(null)
            return@rememberLauncherForActivityResult
        }
        // Persist the grant so the next app launch can reopen the
        // tree without re-prompting.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        scope.launch {
            val ref = withContext(Dispatchers.IO) {
                val doc = DocumentFile.fromTreeUri(context, uri)
                DeviceFolderRef(
                    uri = uri.toString(),
                    displayName = doc?.name
                        ?: DocumentsContract.getTreeDocumentId(uri)
                        ?: uri.lastPathSegment
                        ?: "Folder"
                )
            }
            currentOnPicked.value(ref)
        }
    }

    return { launcher.launch(null) }
}
