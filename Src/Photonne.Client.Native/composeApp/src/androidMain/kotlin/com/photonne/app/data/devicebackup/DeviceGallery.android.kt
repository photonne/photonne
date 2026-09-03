package com.photonne.app.data.devicebackup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.photonne.app.data.devicelibrary.DEVICE_BUCKET_URI_PREFIX
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
import kotlinx.io.Source
import kotlinx.io.asSource
import kotlinx.io.buffered
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

    actual suspend fun restoreFolder(uri: String, fallbackName: String?): DeviceFolderRef? =
        withContext(Dispatchers.IO) {
            // MediaStore-bucket refs (the D5 selection model) don't hold a SAF
            // grant — they ride the media-read permission instead.
            if (uri.startsWith(DEVICE_BUCKET_URI_PREFIX)) {
                return@withContext restoreBucket(uri, fallbackName)
            }
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

    actual suspend fun deleteFile(media: DeviceMedia): Boolean =
        withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(media.uri) }.getOrNull() ?: return@withContext false
            if (uri.authority == MediaStore.AUTHORITY) {
                // MediaStore items (bucket-based folders): a direct delete only
                // succeeds for media this app owns; on refusal the UI reports
                // it honestly. The consent-dialog flow (createTrashRequest)
                // needs an Activity, which this repository-level path lacks.
                return@withContext runCatching {
                    context.contentResolver.delete(uri, null, null) > 0
                }.getOrDefault(false)
            }
            // SAF files picked through a tree URI can be deleted via
            // DocumentsContract when the user granted write access; older
            // grants only have read, in which case this returns false and
            // the UI surfaces a re-pick prompt.
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }.getOrDefault(false)
        }

    actual suspend fun listMedia(folder: DeviceFolderRef): List<DeviceMedia> =
        withContext(Dispatchers.IO) {
            if (folder.uri.startsWith(DEVICE_BUCKET_URI_PREFIX)) {
                return@withContext listBucketMedia(folder)
            }
            val parsed = Uri.parse(folder.uri)
            val root = DocumentFile.fromTreeUri(context, parsed) ?: return@withContext emptyList()
            val collected = mutableListOf<DeviceMedia>()
            walk(root, relativePath = "", out = collected)
            collected.sortedByDescending { it.dateModifiedMillis }
        }

    // ─── MediaStore buckets ──────────────────────────────────────────────────

    private fun hasMediaReadAccess(): Boolean {
        fun granted(perm: String) =
            context.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(android.Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(android.Manifest.permission.READ_MEDIA_VIDEO) ||
                // Android 14+ partial access ("select photos") arrives as this
                // grant ALONE, with the full ones denied. It still reads the
                // user-selected subset through MediaStore, and — critically —
                // restoreFolder() treats "no access" as a revoked source and
                // permanently forgets the bucket plus its ledger. A partial
                // grant must never trigger that.
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
        } else {
            granted(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun restoreBucket(uri: String, fallbackName: String?): DeviceFolderRef? {
        val bucketId = uri.removePrefix(DEVICE_BUCKET_URI_PREFIX)
        if (bucketId.isEmpty()) return null
        // A bucket source rides the APP-WIDE media permission, not a
        // per-folder grant, so there is no real "revoked" state to report:
        // a missing permission is transient (the user can re-grant), and an
        // emptied bucket (the app's own free-space flow can drain it) will
        // refill with the next photo. Returning null for either would make
        // the caller permanently forget the source plus its ledger — so a
        // bucket ref ALWAYS restores; enumeration simply yields nothing
        // until the bucket has media and the permission is back.
        val name = if (hasMediaReadAccess()) bucketDisplayName(bucketId) else null
        return DeviceFolderRef(uri = uri, displayName = name ?: fallbackName ?: bucketId)
    }

    /** Current display name of the bucket, or null when no media claims the
     *  id right now (emptied bucket, permission missing). */
    private fun bucketDisplayName(bucketId: String): String? {
        val projection = arrayOf(COLUMN_BUCKET_DISPLAY_NAME)
        val selection = "$COLUMN_BUCKET_ID = ?"
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).forEach { collection ->
            runCatching {
                context.contentResolver
                    .query(collection, projection, selection, arrayOf(bucketId), null)
                    ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
            }
        }
        return null
    }

    /** One indexed MediaStore query per collection — same speed class as the
     *  timeline's DeviceLibrary, replacing the recursive SAF walk. */
    private fun listBucketMedia(folder: DeviceFolderRef): List<DeviceMedia> {
        if (!hasMediaReadAccess()) return emptyList()
        val bucketId = folder.uri.removePrefix(DEVICE_BUCKET_URI_PREFIX)
        val out = ArrayList<DeviceMedia>(512)
        queryBucket(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            DeviceMediaType.Image, bucketId, folder.displayName, out
        )
        queryBucket(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            DeviceMediaType.Video, bucketId, folder.displayName, out
        )
        out.sortByDescending { it.dateModifiedMillis }
        return out
    }

    private fun queryBucket(
        collection: Uri,
        type: DeviceMediaType,
        bucketId: String,
        bucketName: String,
        into: MutableList<DeviceMedia>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            COLUMN_DATE_TAKEN
        )
        runCatching {
            context.contentResolver.query(
                collection, projection, "$COLUMN_BUCKET_ID = ?", arrayOf(bucketId), null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modifiedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val takenCol = c.getColumnIndexOrThrow(COLUMN_DATE_TAKEN)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    val mime = c.getString(mimeCol) ?: continue
                    into += DeviceMedia(
                        uri = android.content.ContentUris
                            .withAppendedId(collection, c.getLong(idCol)).toString(),
                        displayName = name,
                        relativePath = bucketName,
                        mimeType = mime,
                        sizeBytes = c.getLong(sizeCol),
                        // DATE_MODIFIED is epoch SECONDS — normalized to ms
                        // like every other fingerprint in the app.
                        dateModifiedMillis = c.getLong(modifiedCol) * 1000L,
                        type = type,
                        dateCreatedMillis = c.getLong(takenCol).takeIf { it > 0L }
                    )
                }
            }
        }
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

    actual suspend fun computeSha256(media: DeviceMedia, allowNetwork: Boolean): String =
        withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            val isVideo = media.type == DeviceMediaType.Video
            // Hash the EXACT bytes [withUploadSource] sends: same original
            // (un-redacted) source AND the same byte count it declares as the
            // upload's Content-Length. Reading the full stream here while the
            // upload is bounded by `sizeBytes` made the two diverge for some
            // files, so the server's stored checksum never matched our hash and
            // the file looked "not synced" forever. Mirror the resolution and
            // the size bound so the dedup/verification key always matches.
            val original = MediaOriginalReader.resolveOriginal(
                context, media.displayName, isVideo, media.sizeBytes
            )
            if (original != null) {
                val stream = context.contentResolver.openInputStream(original.uri)
                if (stream != null) {
                    stream.use { hashUpTo(it, original.sizeBytes, digest) }
                    return@withContext digest.digest().toHexLower()
                }
            }
            // Fallback (API < 29, or file not in MediaStore): plain SAF read,
            // bounded by the same statSize the upload fallback declares.
            val uri = Uri.parse(media.uri)
            val sizeBytes = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull()?.takeIf { it >= 0 } ?: media.sizeBytes
            val input = context.contentResolver.openInputStream(uri)
                ?: throw DeviceGalleryUnavailable("Cannot open ${media.displayName} for hashing")
            input.use { hashUpTo(it, sizeBytes, digest) }
            digest.digest().toHexLower()
        }

    private companion object {
        // Plain column names, stable since API 1 on both collections; the
        // typed constants only reached MediaStore.MediaColumns at API 29.
        const val COLUMN_BUCKET_ID = "bucket_id"
        const val COLUMN_BUCKET_DISPLAY_NAME = "bucket_display_name"
        const val COLUMN_DATE_TAKEN = "datetaken"
    }

    /** Feeds at most [limit] bytes of [stream] into [digest] (or the whole
     *  stream when [limit] <= 0), matching the upload's Content-Length bound. */
    private fun hashUpTo(stream: java.io.InputStream, limit: Long, digest: MessageDigest) {
        val buf = ByteArray(64 * 1024)
        var remaining = limit
        while (limit <= 0 || remaining > 0) {
            val toRead = if (limit <= 0) buf.size
                else minOf(buf.size.toLong(), remaining).toInt()
            val n = stream.read(buf, 0, toRead)
            if (n <= 0) break
            digest.update(buf, 0, n)
            if (limit > 0) remaining -= n
        }
    }

    actual suspend fun <T> withUploadSource(
        media: DeviceMedia,
        block: suspend (source: Source, sizeBytes: Long) -> T
    ): T = withContext(Dispatchers.IO) {
        val isVideo = media.type == DeviceMediaType.Video
        // Prefer the original MediaStore bytes (location intact). Its SIZE is
        // the authoritative content length for the upload.
        val original = MediaOriginalReader.resolveOriginal(
            context, media.displayName, isVideo, media.sizeBytes
        )
        if (original != null) {
            val stream = context.contentResolver.openInputStream(original.uri)
            if (stream != null) {
                return@withContext stream.use { block(it.asSource().buffered(), original.sizeBytes) }
            }
        }

        // Fallback (API < 29, or file not in MediaStore): plain SAF read. GPS
        // may be redacted here, but it's the only way to reach the bytes.
        val uri = Uri.parse(media.uri)
        // statSize gives the authoritative byte count even when the scan
        // metadata went stale; fall back to the scanned size for providers
        // that can't stat.
        val sizeBytes = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 } ?: media.sizeBytes
        val input = context.contentResolver.openInputStream(uri)
            ?: throw DeviceGalleryUnavailable("Cannot open ${media.displayName} for upload")
        input.use { stream ->
            block(stream.asSource().buffered(), sizeBytes)
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
        // tree without re-prompting. Write access is required for the
        // "Free up space" flow to delete synced files in-place.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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

    // Request media-read + ACCESS_MEDIA_LOCATION before picking the folder, so
    // the background backup worker can later read the originals (un-redacted
    // GPS). We proceed to the folder picker regardless of the grant result —
    // without it the upload simply falls back to redacted SAF reads.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { launcher.launch(null) }

    return {
        if (MediaOriginalReader.hasMediaLocationAccess(context)) {
            launcher.launch(null)
        } else {
            val needed = MediaPermissions.requestSet()
            if (needed.isEmpty()) launcher.launch(null) else permissionLauncher.launch(needed)
        }
    }
}
