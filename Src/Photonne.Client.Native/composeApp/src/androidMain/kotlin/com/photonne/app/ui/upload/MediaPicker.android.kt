package com.photonne.app.ui.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Uses the SAF documents picker (`OpenMultipleDocuments`) instead of the
 * system Photo Picker on purpose: the Photo Picker redacts GPS EXIF from
 * the returned bytes by design (its provider URIs don't support
 * `setRequireOriginal`), so photos uploaded through it silently lost
 * their location. The documents provider returns the file verbatim and
 * additionally exposes `COLUMN_LAST_MODIFIED`, which we forward so the
 * server can preserve the original file date.
 */
@Composable
actual fun rememberMediaPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
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
                        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: return@runCatching null
                        val (name, lastModified) = queryNameAndLastModified(resolver, uri)
                        val mime = resolver.getType(uri) ?: "application/octet-stream"
                        PickedFile(
                            name = name ?: "upload",
                            mimeType = mime,
                            sizeBytes = bytes.size.toLong(),
                            bytes = bytes,
                            lastModifiedMillis = lastModified
                        )
                    }.getOrNull()
                }
            }
            currentOnPicked.value(files)
        }
    }

    return { launcher.launch(arrayOf("image/*", "video/*")) }
}

private fun queryNameAndLastModified(
    resolver: android.content.ContentResolver,
    uri: android.net.Uri
): Pair<String?, Long?> {
    val projection = arrayOf(
        android.provider.OpenableColumns.DISPLAY_NAME,
        android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
    return resolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null to null
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        val modifiedIndex =
            cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
        val modified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) {
            cursor.getLong(modifiedIndex).takeIf { it > 0 }
        } else null
        name to modified
    } ?: (null to null)
}

private const val MAX_SELECTION = 50
