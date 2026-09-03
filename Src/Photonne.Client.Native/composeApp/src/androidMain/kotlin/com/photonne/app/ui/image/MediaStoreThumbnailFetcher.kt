package com.photonne.app.ui.image

import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import okio.FileSystem
import okio.buffer
import okio.source

/**
 * Coil fetcher for `content://media/...` URIs that serves the SYSTEM's
 * precomputed thumbnail cache via [android.content.ContentResolver.loadThumbnail]
 * (API 29+) — the same store the stock gallery scrolls on. Without it,
 * Coil's generic content fetcher streams and decodes the ORIGINAL
 * bytes per cell; for videos that meant `VideoFrameDecoder` extracting
 * a frame from the full file on every cold cell.
 *
 * When `loadThumbnail` can't produce one (unsupported format, missing
 * file), it falls back to streaming the original through Coil's normal
 * decoder chain — same behaviour the built-in fetcher would have had.
 */
internal class MediaStoreThumbnailFetcher(
    private val data: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val resolver = options.context.contentResolver
        val uri = android.net.Uri.parse(data.toString())

        val thumbnail = runCatching {
            resolver.loadThumbnail(uri, Size(targetPx(), targetPx()), null)
        }.getOrNull()
        if (thumbnail != null) {
            return ImageFetchResult(
                image = thumbnail.asImage(),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        }

        val stream = resolver.openInputStream(uri)
            ?: error("No stream for $uri")
        return SourceFetchResult(
            source = ImageSource(
                source = stream.source().buffer(),
                fileSystem = FileSystem.SYSTEM
            ),
            mimeType = resolver.getType(uri),
            dataSource = DataSource.DISK
        )
    }

    /**
     * The composable's measured size when Coil knows it (grid cells,
     * the detail poster), clamped so the system never upscales a tiny
     * cache entry into a big view nor decodes beyond-screen pixels.
     */
    private fun targetPx(): Int {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: 0
        val h = (options.size.height as? Dimension.Pixels)?.px ?: 0
        val requested = maxOf(w, h)
        return if (requested <= 0) DEFAULT_SIZE_PX else requested.coerceIn(MIN_SIZE_PX, MAX_SIZE_PX)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
            if (data.scheme != "content") return null
            if (data.authority != MediaStore.AUTHORITY) return null
            return MediaStoreThumbnailFetcher(data, options)
        }
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 512
        const val MIN_SIZE_PX = 64
        const val MAX_SIZE_PX = 2048
    }
}
