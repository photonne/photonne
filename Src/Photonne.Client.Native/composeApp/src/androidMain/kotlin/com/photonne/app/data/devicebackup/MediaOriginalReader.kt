package com.photonne.app.data.devicebackup

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.InputStream

/**
 * The runtime permissions needed to read the original (location-bearing)
 * bytes of gallery media, varying by Android version. Empty below API 29,
 * where the platform never redacts GPS in the first place.
 */
internal object MediaPermissions {

    fun requestSet(): Array<String> = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> emptyArray()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
    }
}

/**
 * Reads the UNREDACTED original bytes of a gallery media file.
 *
 * Since Android 10 (API 29) the platform strips GPS EXIF from media handed
 * to apps that lack ACCESS_MEDIA_LOCATION — whether the bytes are read
 * through MediaStore or SAF. The only documented way to get the original
 * (location intact) is a MediaStore content URI wrapped with
 * [MediaStore.setRequireOriginal], which requires the permission.
 *
 * We resolve the picked SAF/document file to its MediaStore entry by display
 * name (cameras use unique timestamped names) and, when present, prefer an
 * exact size match. Callers fall back to the plain (redacted) SAF stream when
 * this returns null: API < 29 never redacts, and files outside MediaStore can
 * only be read through SAF anyway.
 */
internal object MediaOriginalReader {

    data class Original(val uri: Uri, val sizeBytes: Long)

    /** True when we can request original (un-redacted) media on this device. */
    fun hasMediaLocationAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        fun granted(perm: String) =
            context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        if (!granted(Manifest.permission.ACCESS_MEDIA_LOCATION)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Resolves [displayName] to a MediaStore content URI wrapped so the read
     * returns the original (location-bearing) bytes, plus that original's
     * size. Returns null when the file isn't in MediaStore or the permission
     * isn't held.
     */
    fun resolveOriginal(
        context: Context,
        displayName: String,
        isVideo: Boolean,
        expectedSize: Long,
    ): Original? {
        if (!hasMediaLocationAccess(context)) return null

        val collection = if (isVideo)
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf(displayName)

        var firstUri: Uri? = null
        var firstSize = 0L
        var exactUri: Uri? = null
        var exactSize = 0L
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val size = c.getLong(sizeCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    if (firstUri == null) {
                        firstUri = uri
                        firstSize = size
                    }
                    // Redaction shrinks the on-read payload but not the stored
                    // SIZE, so an exact match is the strongest disambiguator
                    // when several files share a name.
                    if (expectedSize > 0 && size == expectedSize && exactUri == null) {
                        exactUri = uri
                        exactSize = size
                    }
                }
            }
        }.getOrNull()

        val chosen = exactUri ?: firstUri ?: return null
        val chosenSize = if (exactUri != null) exactSize else firstSize
        val original = MediaStore.setRequireOriginal(chosen)
        return Original(original, chosenSize)
    }

    /** Convenience: opens an original-bytes stream, or null to fall back to SAF. */
    fun openOriginalStream(
        context: Context,
        displayName: String,
        isVideo: Boolean,
        expectedSize: Long,
    ): InputStream? {
        val ref = resolveOriginal(context, displayName, isVideo, expectedSize) ?: return null
        return runCatching { context.contentResolver.openInputStream(ref.uri) }.getOrNull()
    }
}
