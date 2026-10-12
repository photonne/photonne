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
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Uses the SAF documents picker (`OpenMultipleDocuments`) for selection,
 * then streams the ORIGINAL bytes at upload time via [MediaOriginalReader] (MediaStore +
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
                // selection can't queue an unbounded batch. Nothing is read
                // here: each file is streamed when the queue uploads it (the
                // SAF grant lasts until reboot), and one that can't even be
                // opened is reported now.
                uris.take(MAX_SELECTION).map { uri ->
                    describePickedFile(context, uri)
                        ?: unreadablePickedFile(uri.lastPathSegment ?: "upload")
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

/**
 * Describe [uri] como [PickedFile] sin leerlo: nombre, tipo, tamaño y fecha, y
 * una referencia que se abre en streaming cuando la cola lo sube (ver
 * [UriContent]). Lo usan el selector y "Compartir con Photonne" (MainActivity).
 * Abrir el archivo no lo lee, así que se prueba aquí para avisar ya de lo que no
 * se puede leer. Null si no se ha podido abrir.
 */
internal fun describePickedFile(
    context: android.content.Context,
    uri: android.net.Uri
): PickedFile? = runCatching {
    val resolver = context.contentResolver
    val meta = queryMeta(resolver, uri)
    val name = meta.name ?: "upload"
    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val statSize = statSize(resolver, uri)
    if (statSize == null) {
        // Providers that can't stat: at least make sure it opens.
        resolver.openInputStream(uri)?.close() ?: return@runCatching null
    }
    val knownSize = statSize ?: meta.size
    PickedFile(
        name = name,
        mimeType = mime,
        sizeBytes = knownSize ?: 0L,
        content = UriContent(context.applicationContext, uri, name, mime.startsWith("video/"), knownSize),
        lastModifiedMillis = meta.lastModified
    )
}.getOrNull()

/**
 * Streams a picked content URI when the queue uploads it: the ORIGINAL bytes
 * (GPS intact) when the file is in MediaStore and the permission is held,
 * otherwise the plain SAF stream — the same resolution the device backup uses,
 * with each one's own size as the upload's length.
 */
private class UriContent(
    private val context: android.content.Context,
    private val uri: android.net.Uri,
    private val name: String,
    private val isVideo: Boolean,
    private val knownSize: Long?
) : PickedContent {
    override suspend fun <T> open(block: suspend (source: Source, sizeBytes: Long) -> T): T =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val original = MediaOriginalReader.resolveOriginal(context, uri, name, isVideo, knownSize ?: 0L)
            val originalStream = original?.let { runCatching { resolver.openInputStream(it.uri) }.getOrNull() }
            if (original != null && originalStream != null) {
                return@withContext originalStream.use { block(it.asSource().buffered(), original.sizeBytes) }
            }
            // The upload declares its length up front: a provider that can't
            // stat gets one counting pass (nothing kept) before the real read.
            val size = statSize(resolver, uri) ?: knownSize ?: countBytes(resolver, uri)
            val input = resolver.openInputStream(uri)
                ?: throw java.io.FileNotFoundException("Cannot open $name for upload")
            input.use { block(it.asSource().buffered(), size) }
        }

    override val thumbnailModel: Any get() = uri
}

private fun statSize(resolver: android.content.ContentResolver, uri: android.net.Uri): Long? =
    runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }
        .getOrNull()?.takeIf { it >= 0 }

private fun countBytes(resolver: android.content.ContentResolver, uri: android.net.Uri): Long {
    val input = resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString())
    return input.use { stream ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
        }
        total
    }
}

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
