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
import kotlinx.io.files.Path

actual class AssetSharing(private val context: Context) {

    /**
     * Opens `Downloads/Photonne/[fileName]`: a pending MediaStore row on
     * Android 10+ (invisible to other apps until committed, deleted if the
     * download fails or is cancelled) and the legacy public Downloads
     * directory on older OS versions.
     */
    actual suspend fun openDownload(
        fileName: String,
        mimeType: String
    ): DownloadSink = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openMediaStoreDownload(fileName = fileName, mimeType = mimeType)
        } else {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val target = File(File(downloads, "Photonne").apply { mkdirs() }, fileName)
            FileDownloadSink(
                target = Path(target.absolutePath),
                displayName = fileName,
                mimeType = mimeType
            )
        }
    }

    /**
     * Stages into `cacheDir/shared` — app-private, vended through the
     * FileProvider — and nowhere else: no MediaStore row, so sharing leaves
     * no copy in the gallery for the backup to pick up.
     */
    actual suspend fun openShareStage(
        fileName: String,
        mimeType: String
    ): DownloadSink = withContext(Dispatchers.IO) {
        val safeName = fileName.replace('/', '_').ifBlank { "shared" }
        val target = File(shareDir(), safeName)
        FileDownloadSink(target = Path(target.absolutePath), displayName = safeName, mimeType = mimeType)
    }

    /** The receiving app reads the files after we return, so the previous
     *  share's files are only dropped when a new share starts. */
    actual suspend fun clearShareCache() = withContext(Dispatchers.IO) {
        shareDir().listFiles()?.forEach { it.deleteRecursively() }
        Unit
    }

    private fun shareDir(): File = File(context.cacheDir, "shared").apply { mkdirs() }

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
            // `openShareStage` already stages the bytes inside `shared/`,
            // so source and target are usually the SAME file. Copying a file
            // onto itself with overwrite=true would delete it first (via
            // `target.delete()`) and then fail to read the now-missing source —
            // which is why the share sheet silently never appeared. Only copy
            // when the file genuinely lives elsewhere.
            if (source.absolutePath != target.absolutePath) {
                source.copyTo(target, overwrite = true)
            }
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

    actual suspend fun shareText(text: String, subject: String?) {
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun openMediaStoreDownload(fileName: String, mimeType: String): DownloadSink {
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
        val out = try {
            resolver.openOutputStream(uri)?.buffered()
                ?: error("Could not open MediaStore stream for $fileName")
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        }
        return object : DownloadSink {
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                out.write(buffer, offset, length)
            }

            override suspend fun commit(): SavedAssetFile = withContext(Dispatchers.IO) {
                out.close()
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
                // MediaStore renames on a clash ("x (1).zip"): say the real name.
                val name = resolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    ?: fileName
                SavedAssetFile(
                    path = "${Environment.DIRECTORY_DOWNLOADS}/Photonne/$name",
                    displayName = name,
                    mimeType = mimeType
                )
            }

            override suspend fun discard() {
                withContext(Dispatchers.IO) {
                    runCatching { out.close() }
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
        }
    }
}
