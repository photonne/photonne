package com.photonne.app.data.devicelibrary

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.photonne.app.data.devicebackup.DeviceMedia
import com.photonne.app.data.devicebackup.DeviceMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Android implementation backed by **MediaStore** — the system's own
 * SQLite media index, the same one the stock gallery reads. One cursor
 * per collection (images, videos) with a plain projection returns tens
 * of thousands of rows in milliseconds; contrast with the SAF
 * `DocumentFile.walk()` the backup flow uses, which pays ~5 Binder
 * round-trips per file.
 *
 * Emitted URIs are typed `content://media/...` item URIs, which Coil's
 * built-in fetcher (plus the VideoFrameDecoder) resolves directly, so
 * thumbnails ride the system's already-generated thumbnail cache.
 */
actual class DeviceLibrary(private val context: Context) {

    actual val isSupported: Boolean = true

    actual val supportsBuckets: Boolean = true

    actual fun accessState(): DeviceLibraryAccess {
        fun granted(perm: String) =
            context.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> when {
                granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                    granted(Manifest.permission.READ_MEDIA_VIDEO) -> DeviceLibraryAccess.Full
                // Android 14+ "select photos": the visual-user-selected
                // grant arrives alone, full grants stay denied.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    granted(READ_MEDIA_VISUAL_USER_SELECTED) -> DeviceLibraryAccess.Partial
                else -> DeviceLibraryAccess.NotDetermined
            }
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> DeviceLibraryAccess.Full
            else -> DeviceLibraryAccess.NotDetermined
        }
    }

    actual suspend fun loadAll(scope: DeviceLibraryScope): List<DeviceMedia> =
        withContext(Dispatchers.IO) {
            if (!accessState().canRead) return@withContext emptyList()
            // An explicit empty pick shows nothing — `IN ()` isn't valid SQL.
            if (scope is DeviceLibraryScope.Buckets && scope.bucketIds.isEmpty()) {
                return@withContext emptyList()
            }
            // Normally short-circuited upstream (DeviceLibraryStore); honored
            // here too so the contract holds for any direct caller.
            if (scope == DeviceLibraryScope.SyncedOnly) return@withContext emptyList()
            val filter = scopeFilter(scope)
            val out = ArrayList<DeviceMedia>(4096)
            queryCollection(
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                type = DeviceMediaType.Image,
                filter = filter,
                into = out
            )
            queryCollection(
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                type = DeviceMediaType.Video,
                filter = filter,
                into = out
            )
            // Two independent cursors — interleave them into one newest-first
            // stream by the same capture instant the timeline buckets on.
            out.sortByDescending { it.dateCreatedMillis ?: it.dateModifiedMillis }
            out
        }

    /**
     * Translates the timeline scope into a MediaStore WHERE clause, so a
     * narrowed timeline never materializes the excluded rows (a WhatsApp
     * folder alone can be tens of thousands).
     */
    private fun scopeFilter(scope: DeviceLibraryScope): Pair<String, Array<String>>? =
        when (scope) {
            DeviceLibraryScope.All -> null
            // Filtered out before the query ever runs (see loadAll).
            DeviceLibraryScope.SyncedOnly -> null
            DeviceLibraryScope.CameraOnly ->
                // The DCIM tree is where Android's spec sends camera output.
                // RELATIVE_PATH only exists from API 29; before scoped storage
                // the absolute DATA path is still reliable.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "$COLUMN_RELATIVE_PATH LIKE ?" to arrayOf("DCIM/%")
                } else {
                    "$COLUMN_DATA LIKE ?" to arrayOf("%/DCIM/%")
                }
            is DeviceLibraryScope.Buckets -> {
                val ids = scope.bucketIds.toTypedArray()
                val placeholders = ids.joinToString(",") { "?" }
                "$COLUMN_BUCKET_ID IN ($placeholders)" to ids
            }
        }

    private fun queryCollection(
        collection: Uri,
        type: DeviceMediaType,
        filter: Pair<String, Array<String>>?,
        into: MutableList<DeviceMedia>
    ) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            COLUMN_DATE_TAKEN,
            COLUMN_BUCKET_DISPLAY_NAME
        )
        runCatching {
            context.contentResolver.query(
                collection, projection, filter?.first, filter?.second, null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val modifiedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val takenCol = c.getColumnIndexOrThrow(COLUMN_DATE_TAKEN)
                val bucketCol = c.getColumnIndexOrThrow(COLUMN_BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    // DATE_MODIFIED is epoch SECONDS, DATE_TAKEN epoch millis.
                    val modifiedMillis = c.getLong(modifiedCol) * 1000L
                    val takenMillis = c.getLong(takenCol).takeIf { it > 0L }
                    into += DeviceMedia(
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        displayName = c.getString(nameCol) ?: "$id",
                        relativePath = c.getString(bucketCol).orEmpty(),
                        mimeType = c.getString(mimeCol) ?: defaultMimeFor(type),
                        sizeBytes = c.getLong(sizeCol),
                        dateModifiedMillis = modifiedMillis,
                        type = type,
                        dateCreatedMillis = takenMillis ?: modifiedMillis
                    )
                }
            }
        }
    }

    actual suspend fun listBuckets(): List<DeviceBucket> = withContext(Dispatchers.IO) {
        if (!accessState().canRead) return@withContext emptyList()
        // No portable GROUP BY through ContentResolver — a plain sweep over
        // both collections aggregates client-side in one pass each. The same
        // pass tracks each bucket's newest item, so folder-style listings get
        // a cover thumbnail without a per-bucket LIMIT-1 query.
        class Agg(val name: String) {
            var count = 0
            var latestUri: String? = null
            var latestMillis = Long.MIN_VALUE
        }

        val buckets = LinkedHashMap<String, Agg>()
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).forEach { collection ->
            runCatching {
                context.contentResolver.query(
                    collection,
                    arrayOf(
                        COLUMN_BUCKET_ID,
                        COLUMN_BUCKET_DISPLAY_NAME,
                        MediaStore.MediaColumns._ID,
                        COLUMN_DATE_TAKEN,
                        MediaStore.MediaColumns.DATE_MODIFIED
                    ),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val agg = buckets.getOrPut(id) { Agg(name) }
                        agg.count++
                        // Same capture instant loadAll sorts by: DATE_TAKEN is
                        // epoch millis, DATE_MODIFIED epoch seconds.
                        val takenMillis = c.getLong(3).takeIf { it > 0L }
                            ?: (c.getLong(4) * 1000L)
                        if (takenMillis > agg.latestMillis) {
                            agg.latestMillis = takenMillis
                            agg.latestUri = ContentUris
                                .withAppendedId(collection, c.getLong(2)).toString()
                        }
                    }
                }
            }
        }
        buckets.map { (id, agg) ->
            DeviceBucket(
                id = id,
                displayName = agg.name,
                itemCount = agg.count,
                latestUri = agg.latestUri
            )
        }.sortedByDescending { it.itemCount }
    }

    actual fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        resolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        awaitClose { resolver.unregisterContentObserver(observer) }
    }
}

private fun defaultMimeFor(type: DeviceMediaType): String =
    if (type == DeviceMediaType.Video) "video/mp4" else "image/jpeg"

// Plain column names, stable since API 1 on both collections; the typed
// constants only reached MediaStore.MediaColumns at API 29 and this
// module still supports minSdk 26.
private const val COLUMN_DATE_TAKEN = "datetaken"
private const val COLUMN_BUCKET_ID = "bucket_id"
private const val COLUMN_BUCKET_DISPLAY_NAME = "bucket_display_name"
// Scope-filter columns: RELATIVE_PATH is API 29+, DATA is the pre-scoped-
// storage absolute path (deprecated there, but this branch only runs 26-28).
private const val COLUMN_RELATIVE_PATH = "relative_path"
private const val COLUMN_DATA = "_data"

// Manifest.permission constant exists from compileSdk 34; inlined so the
// reference doesn't trip older lint configs.
private const val READ_MEDIA_VISUAL_USER_SELECTED =
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

/** The permission set to request for full-library access, per SDK level. */
private fun libraryPermissionSet(): Array<String> = when {
    // Android 14+: the visual-user-selected permission must ride in the
    // same request or the system dialog omits the "select photos" option.
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.ACCESS_MEDIA_LOCATION
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.ACCESS_MEDIA_LOCATION
    )
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
actual fun rememberDeviceLibraryAccessRequester(
    onResult: (DeviceLibraryAccess) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Re-derive from checkSelfPermission rather than the result map:
        // an Android 14 "select photos" choice grants a permission we
        // didn't list, and re-prompts return an empty map when the
        // dialog is suppressed.
        currentOnResult.value(DeviceLibrary(context).accessState())
    }
    return remember(launcher) { { launcher.launch(libraryPermissionSet()) } }
}
