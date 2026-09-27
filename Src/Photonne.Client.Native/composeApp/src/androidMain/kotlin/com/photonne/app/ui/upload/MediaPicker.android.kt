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
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                // OpenMultipleDocuments has no OS-imposed selection cap like
                // the Photo Picker did; keep our own so a whole-folder
                // selection can't queue an unbounded in-memory batch.
                uris.take(MAX_SELECTION).mapNotNull { uri -> readPickedFile(context, uri) }
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

/**
 * Lee [uri] entero como [PickedFile]: los bytes ORIGINALES (GPS intacto) si el
 * archivo está en MediaStore y hay permiso; si no, el flujo tal cual. Lo usan el
 * selector y "Compartir con Photonne" (MainActivity), que tiene que leer en el
 * momento, mientras dura el permiso temporal del intent.
 *
 * Con [maxBytes], un archivo que ya se sabe más grande no se lee: vuelve sin
 * bytes y con su tamaño real, y la cola de subida lo descarta y lo cuenta en su
 * aviso de "superan el límite" (leerlo entero en memoria para eso no tiene
 * sentido).
 */
internal fun readPickedFile(
    context: android.content.Context,
    uri: android.net.Uri,
    maxBytes: Long? = null
): PickedFile? = runCatching {
    val resolver = context.contentResolver
    val meta = queryMeta(resolver, uri)
    val name = meta.name ?: "upload"
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val isVideo = mime.startsWith("video/")
    val knownSize = meta.size
    if (maxBytes != null && knownSize != null && knownSize > maxBytes) {
        return@runCatching PickedFile(
            name = name,
            mimeType = mime,
            sizeBytes = knownSize,
            bytes = ByteArray(0),
            lastModifiedMillis = meta.lastModified
        )
    }
    // Original bytes (GPS intact) when the file is in MediaStore and the
    // permission is held; otherwise the plain SAF stream.
    val bytes = MediaOriginalReader.openOriginalStream(
        context, uri, name, isVideo, knownSize ?: 0L
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

private data class DocMeta(val name: String?, val size: Long?, val lastModified: Long?)

private fun queryMeta(resolver: android.content.ContentResolver, uri: android.net.Uri): DocMeta {
    val projection = arrayOf(
        android.provider.OpenableColumns.DISPLAY_NAME,
        android.provider.OpenableColumns.SIZE,
        android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
    )
    // Una URI compartida desde otra app no es siempre un documento SAF: un
    // proveedor que no conoce COLUMN_LAST_MODIFIED rechaza la proyección entera.
    // Entonces se vuelve a pedir solo lo que todo proveedor abrible garantiza.
    val cursor = runCatching { resolver.query(uri, projection, null, null, null) }.getOrNull()
        ?: runCatching {
            resolver.query(
                uri,
                arrayOf(
                    android.provider.OpenableColumns.DISPLAY_NAME,
                    android.provider.OpenableColumns.SIZE
                ),
                null, null, null
            )
        }.getOrNull()
    return cursor?.use { cursor ->
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

/** Tope de archivos por tanda, también para lo compartido desde otra app. */
internal const val MAX_SELECTION = 50
