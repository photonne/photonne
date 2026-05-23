package com.photonne.app.ui.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Buffer
import okio.FileSystem
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Photos.PHAsset
import platform.Photos.PHImageContentModeAspectFill
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeFast
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.coroutines.resume

private const val PHOTOKIT_SCHEME = "photokit"
private const val USER_LIBRARY_AUTHORITY = "userLibrary"
private const val TARGET_SIZE_PX = 512.0
private const val JPEG_QUALITY = 0.85

/**
 * Coil fetcher that resolves `photokit:<localIdentifier>` URIs to
 * sized JPEG thumbnails via PhotoKit. The Backup grid hands us these
 * URIs from the iOS `DeviceGallery`; without this fetcher the cells
 * render empty because Coil's built-in fetchers only know how to
 * handle `file://` and `http(s)://` data.
 *
 * Coil registers a default `String → Uri` mapper, so anything pulled
 * into `AsyncImage` as a String is already a `coil3.Uri` by the time
 * fetchers run — we type the factory against [Uri] and gate on the
 * scheme rather than trying to intercept the raw String.
 *
 * Uses `requestImageForAsset` with `aspectFill` at a single fixed
 * thumbnail size — large enough for both the grid cell and the
 * detail preview while still much cheaper than the full-resolution
 * image. `HighQualityFormat` delivery gives us exactly one callback
 * so we don't have to filter degraded previews or worry about a
 * follow-up that never arrives when an iCloud download fails.
 */
@OptIn(ExperimentalForeignApi::class)
internal class PhotoKitImageFetcher(
    private val localIdentifier: String
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (localIdentifier.isEmpty()) return null
        val asset = resolveAsset(localIdentifier) ?: return null
        val image = requestThumbnail(asset) ?: return null
        val jpeg = UIImageJPEGRepresentation(image, JPEG_QUALITY) ?: return null
        val bytes = jpeg.toByteArray()
        if (bytes.isEmpty()) return null
        val buffer = Buffer().apply { write(bytes) }
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            // The asset lives on local storage (or iCloud, transparently
            // streamed); MEMORY would imply we already had it decoded.
            dataSource = DataSource.DISK
        )
    }

    private fun resolveAsset(localId: String): PHAsset? {
        val result = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localId), options = null)
        return result.firstObject as? PHAsset
    }

    private suspend fun requestThumbnail(asset: PHAsset): UIImage? =
        suspendCancellableCoroutine { cont ->
            val opts = PHImageRequestOptions().apply {
                deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
                resizeMode = PHImageRequestOptionsResizeModeFast
                // Photos backed by iCloud may need a download; without
                // this flag the request fails for unsynced assets on
                // devices with optimised storage enabled.
                networkAccessAllowed = true
            }
            val requestId = PHImageManager.defaultManager().requestImageForAsset(
                asset = asset,
                targetSize = CGSizeMake(TARGET_SIZE_PX, TARGET_SIZE_PX),
                contentMode = PHImageContentModeAspectFill,
                options = opts,
                resultHandler = { image, _ ->
                    if (cont.isActive) cont.resume(image)
                }
            )
            cont.invokeOnCancellation {
                PHImageManager.defaultManager().cancelImageRequest(requestId)
            }
        }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (data.scheme != PHOTOKIT_SCHEME) return null
            // Local identifiers contain `/` separators (e.g.
            // `1234ABCD-…/L0/001`), which different URI parsers split
            // between authority and path in inconsistent ways. Pull the
            // raw scheme-specific portion straight from the URI's
            // string form so PhotoKit recognises it verbatim.
            val localId = data.toString().substringAfter(':', "")
            if (localId.isEmpty() || localId == USER_LIBRARY_AUTHORITY) return null
            return PhotoKitImageFetcher(localId)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val src = bytes ?: return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), src, len.toULong())
    }
    return out
}
