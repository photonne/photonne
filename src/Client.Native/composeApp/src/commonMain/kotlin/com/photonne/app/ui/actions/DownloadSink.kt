package com.photonne.app.ui.actions

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * A file written while its bytes arrive from the server, so a big download
 * (a ZIP of hundreds of photos, a video original) never sits whole in memory.
 * The platform decides where it lives ([AssetSharing.openDownload]) and
 * [drainInto] pumps the response into it.
 *
 * [write] blocks on disk I/O and is only called off the main thread. Exactly
 * one of [commit] or [discard] ends the sink.
 */
interface DownloadSink {
    fun write(buffer: ByteArray, offset: Int, length: Int)

    /** Closes the file and leaves it where it belongs. */
    suspend fun commit(): SavedAssetFile

    /** Closes the file and deletes what was written. Never throws. */
    suspend fun discard()
}

/**
 * Copies this body into [sink] chunk by chunk and commits it. Any failure,
 * a short body or a cancellation (the pill's Cancelar) discards the half
 * written file before rethrowing. [onProgress] gets the bytes written so far
 * and [contentLength], null when the server didn't say.
 */
suspend fun ByteReadChannel.drainInto(
    sink: DownloadSink,
    contentLength: Long?,
    onProgress: (written: Long, total: Long?) -> Unit = { _, _ -> }
): SavedAssetFile {
    try {
        // Default, not the caller's dispatcher: sink writes block on disk I/O
        // and the caller is usually on Main (same reasoning as the upload pump).
        withContext(Dispatchers.Default) {
            val buffer = ByteArray(DOWNLOAD_COPY_CHUNK)
            var written = 0L
            while (true) {
                val read = readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read == 0) continue
                sink.write(buffer, 0, read)
                written += read
                onProgress(written, contentLength)
            }
            // A dropped connection can look like a clean end of body: without
            // this a truncated video would be kept as if it were whole.
            if (contentLength != null && written != contentLength) {
                throw IOException("Download ended after $written of $contentLength bytes")
            }
        }
        return sink.commit()
    } catch (failure: Throwable) {
        withContext(NonCancellable) { sink.discard() }
        throw failure
    }
}

/** Writes an in-memory payload through the same sink a streamed one uses. */
suspend fun DownloadSink.writeAll(bytes: ByteArray): SavedAssetFile {
    try {
        withContext(Dispatchers.Default) { write(bytes, 0, bytes.size) }
        return commit()
    } catch (failure: Throwable) {
        withContext(NonCancellable) { discard() }
        throw failure
    }
}

/**
 * Saves a payload that is already in memory (an admin backup, an unsupported
 * file) where downloads go. The selection actions stream instead.
 */
suspend fun AssetSharing.saveAsset(
    bytes: ByteArray,
    fileName: String,
    mimeType: String
): SavedAssetFile = openDownload(fileName, mimeType).writeAll(bytes)

/**
 * A plain file on disk: bytes go to `<target>.part`, which only becomes
 * [target] on [commit]. A failed or cancelled download never leaves a
 * half file under the real name, nor clobbers one that was already there.
 */
class FileDownloadSink(
    private val target: Path,
    private val displayName: String,
    private val mimeType: String
) : DownloadSink {
    private val partial = Path("$target.part")
    private val out = SystemFileSystem.sink(partial).buffered()

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        out.write(buffer, offset, offset + length)
    }

    override suspend fun commit(): SavedAssetFile {
        withContext(Dispatchers.Default) {
            out.close()
            SystemFileSystem.delete(target, mustExist = false)
            SystemFileSystem.atomicMove(partial, target)
        }
        return SavedAssetFile(path = target.toString(), displayName = displayName, mimeType = mimeType)
    }

    override suspend fun discard() {
        withContext(Dispatchers.Default) {
            runCatching { out.close() }
            runCatching { SystemFileSystem.delete(partial, mustExist = false) }
        }
    }
}

private const val DOWNLOAD_COPY_CHUNK = 64 * 1024
