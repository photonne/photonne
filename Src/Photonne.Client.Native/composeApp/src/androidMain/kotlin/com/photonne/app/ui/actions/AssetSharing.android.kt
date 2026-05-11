package com.photonne.app.ui.actions

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class AssetSharing(private val context: Context) {

    /**
     * Writes [bytes] into `Downloads/Photonne/[fileName]` via the
     * MediaStore API on Android 10+ and the legacy public Downloads
     * directory on older OS versions. Returns the location for the
     * subsequent share-sheet call.
     */
    actual suspend fun saveAsset(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile = withContext(Dispatchers.IO) {
        writeToDownloads(bytes = bytes, fileName = fileName, mimeType = mimeType)
    }

    actual suspend fun saveZip(
        bytes: ByteArray,
        fileName: String
    ): SavedAssetFile = withContext(Dispatchers.IO) {
        writeToDownloads(bytes = bytes, fileName = fileName, mimeType = "application/zip")
    }

    actual suspend fun shareFiles(files: List<SavedAssetFile>, mimeType: String) {
        if (files.isEmpty()) return
        // FileProvider URIs survive `Intent.ACTION_SEND_MULTIPLE` better
        // than MediaStore URIs across OEMs; copy the saved file into the
        // app's `external-files-path` so the provider can vend it.
        val authority = "${context.packageName}.fileprovider"
        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val uris = ArrayList<Uri>(files.size)
        for (file in files) {
            val source = File(file.path)
            val target = File(sharedDir, file.displayName)
            source.copyTo(target, overwrite = true)
            uris += FileProvider.getUriForFile(context, authority, target)
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris.first())
                setDataAndType(uris.first(), mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun writeToDownloads(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Photonne"
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("MediaStore insert returned null for $fileName")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Could not open MediaStore stream for $fileName")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            // We still need a path-style address for the share-sheet copy
            // step. Mirror the bytes into a private cache file to use as
            // the source for FileProvider.
            val cacheCopy = File(File(context.cacheDir, "shared").apply { mkdirs() }, fileName)
            cacheCopy.writeBytes(bytes)
            SavedAssetFile(path = cacheCopy.absolutePath, displayName = fileName, mimeType = mimeType)
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val target = File(File(downloads, "Photonne").apply { mkdirs() }, fileName)
            target.writeBytes(bytes)
            SavedAssetFile(path = target.absolutePath, displayName = fileName, mimeType = mimeType)
        }
    }
}
