package com.photonne.app.ui.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.photonne.app.data.devicebackup.MediaOriginalReader
import com.photonne.app.data.devicebackup.MediaPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Uses the SAF documents picker (`OpenMultipleDocuments`) for selection,
 * then reads the ORIGINAL bytes via [MediaOriginalReader] (MediaStore +
 * setRequireOriginal) so GPS/EXIF survive. The system Photo Picker can't be
 * used here: its URIs are always location-redacted with no opt-out. We
 * request the media + ACCESS_MEDIA_LOCATION permissions before picking so the
 * original read can succeed; if denied we fall back to the (redacted) SAF
 * stream.
 */
@Composable
actual fun rememberMediaPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked = rememberUpdatedState(onPicked)

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                // OpenMultipleDocuments has no OS-imposed selection cap like
                // the Photo Picker did; keep our own so a whole-folder
                // selection can't queue an unbounded in-memory batch.
                uris.take(MAX_SELECTION).mapNotNull { uri ->
                    runCatching {
                        val meta = queryMeta(resolver, uri)
                        val name = meta.name ?: "upload"
                        val mime = resolver.getType(uri) ?: "application/octet-stream"
                        val isVideo = mime.startsWith("video/")
                        // Original bytes (GPS intact) when the file is in
                        // MediaStore and the permission is held; otherwise the
                        // plain SAF stream.
                        val bytes = MediaOriginalReader.openOriginalStream(
                            context, name, isVideo, meta.size ?: 0L
                        )?.use { it.readBytes() }
                            ?: resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@runCatching null
                        PickedFile(
                            name = name,
                            mimeType = mime,
                            sizeBytes = bytes.size.toLong(),
                            bytes = bytes,
                            lastModifiedMillis = meta.lastModified
                        )
                    }.getOrNull()
                }
            }
            currentOnPicked.value(files)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { docLauncher.launch(arrayOf("image/*", "video/*")) }

    return {
        if (MediaOriginalReader.hasMediaLocationAccess(context)) {
            docLauncher.launch(arrayOf("image/*", "video/*"))
        } else {
            val needed = MediaPermissions.requestSet()
            if (needed.isEmpty()) docLauncher.launch(arrayOf("image/*", "video/*"))
            else permissionLauncher.launch(needed)
        }
    }
}

private data class DocMeta(val name: String?, val size: Long?, val lastModified: Long?)

private fun queryMeta(resolver: android.content.ContentResolver, uri: android.net.Uri): DocMeta {
    val projection = arrayOf(
        android.provider.OpenableColumns.DISPLAY_NAME,
        android.provider.OpenableColumns.SIZE,
        android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
    return resolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use DocMeta(null, null, null)
        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        val modIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        DocMeta(
            name = if (nameIdx >= 0) cursor.getString(nameIdx) else null,
            size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else null,
            lastModified = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx).takeIf { it > 0 } else null
        )
    } ?: DocMeta(null, null, null)
}

private const val MAX_SELECTION = 50
