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

    /** Android 14's "selected photos" grant; the constant needs compileSdk 34. */
    const val READ_MEDIA_VISUAL_USER_SELECTED =
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    fun requestSet(): Array<String> = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> emptyArray()
        // Android 14+: the partial grant must ride in the same request, or the
        // dialog drops the "select photos" option and the app lands in the
        // compatibility mode whose grant is temporary — the background worker
        // then loses access. Same set as the device library requests.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
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
 * MediaStore items are used as-is; SAF/document files are resolved to their
 * MediaStore entry by display name and only accepted on an unambiguous match
 * (see [resolveOriginal]). Callers fall back to the plain (redacted) SAF stream when
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
            // A partial (Android 14 "selected photos") grant still reads the
            // chosen items' originals, so it counts too.
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VIDEO) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    granted(READ_MEDIA_VISUAL_USER_SELECTED))
        } else {
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Resolves the media to a MediaStore content URI wrapped so the read
     * returns the original (location-bearing) bytes, plus that original's
     * size. Returns null when the file can't be pinned to exactly one
     * MediaStore row or the permission isn't held.
     *
     * When [sourceUri] already is a MediaStore item (bucket-based folders),
     * that exact row is used: resolving by name there could pick another
     * file that happens to share it (`IMG_0001.jpg` in Camera and in
     * WhatsApp) and upload the wrong bytes under this media's identity.
     * SAF documents fall back to a display-name lookup, but only a candidate
     * whose stored SIZE matches [expectedSize] (or the sole candidate when
     * the size is unknown) is accepted; anything ambiguous returns null so
     * the caller reads the SAF stream, which is always the right file.
     */
    fun resolveOriginal(
        context: Context,
        sourceUri: Uri?,
        displayName: String,
        isVideo: Boolean,
        expectedSize: Long,
    ): Original? {
        if (!hasMediaLocationAccess(context)) return null

        if (sourceUri != null && isMediaStoreItem(sourceUri)) {
            val size = runCatching {
                context.contentResolver.query(
                    sourceUri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null
                )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            }.getOrNull() ?: return null
            return Original(MediaStore.setRequireOriginal(sourceUri), size)
        }

        val collection = if (isVideo)
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf(displayName)

        var candidates = 0
        var onlyUri: Uri? = null
        var onlySize = 0L
        var exactUri: Uri? = null
        var exactMatches = 0
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val size = c.getLong(sizeCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    candidates++
                    onlyUri = uri
                    onlySize = size
                    // Redaction shrinks the on-read payload but not the stored
                    // SIZE, so an exact match is the strongest disambiguator
                    // when several files share a name.
                    if (expectedSize > 0 && size == expectedSize) {
                        exactMatches++
                        exactUri = uri
                    }
                }
            }
        }.getOrNull()

        return when {
            // Unique name + size match: the same file.
            exactMatches == 1 -> Original(MediaStore.setRequireOriginal(exactUri!!), expectedSize)
            // No size to compare against: only trust a unique name.
            expectedSize <= 0 && candidates == 1 ->
                Original(MediaStore.setRequireOriginal(onlyUri!!), onlySize)
            else -> null
        }
    }

    /** A MediaStore item row (`content://media/<volume>/<images|video|file>/<id>`),
     *  excluding Photo Picker URIs, which can't be opened as originals. */
    private fun isMediaStoreItem(uri: Uri): Boolean =
        uri.authority == MediaStore.AUTHORITY &&
            uri.pathSegments.none { it == "picker" || it == "picker_get_content" } &&
            uri.lastPathSegment?.toLongOrNull() != null

    /** Convenience: opens an original-bytes stream, or null to fall back to SAF. */
    fun openOriginalStream(
        context: Context,
        sourceUri: Uri?,
        displayName: String,
        isVideo: Boolean,
        expectedSize: Long,
    ): InputStream? {
        val ref = resolveOriginal(context, sourceUri, displayName, isVideo, expectedSize) ?: return null
        return runCatching { context.contentResolver.openInputStream(ref.uri) }.getOrNull()
    }
}
