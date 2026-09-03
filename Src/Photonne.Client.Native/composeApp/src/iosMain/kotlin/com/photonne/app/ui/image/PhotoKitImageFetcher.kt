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
import platform.Photos.PHCachingImageManager
import platform.Photos.PHImageContentModeAspectFill
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeFast
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.coroutines.resume

private const val PHOTOKIT_SCHEME = "photokit"
private const val USER_LIBRARY_AUTHORITY = "userLibrary"
private const val JPEG_QUALITY = 0.85

/**
 * The single [PHCachingImageManager] behind every PhotoKit thumbnail:
 * the grid's fetches and the viewport prefetcher must share one
 * instance, or the prefetcher would warm a cache the fetcher never
 * reads. One fixed target size keeps every request a cache hit against
 * the prefetched entries.
 */
@OptIn(ExperimentalForeignApi::class)
internal object PhotoKitThumbnails {
    val manager = PHCachingImageManager()

    const val TARGET_SIZE_PX = 512.0

    fun requestOptions(allowNetwork: Boolean) = PHImageRequestOptions().apply {
        // Exactly one callback — no degraded previews to filter, no
        // follow-up that never arrives.
        deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
        resizeMode = PHImageRequestOptionsResizeModeFast
        networkAccessAllowed = allowNetwork
    }
}

/**
 * Coil fetcher that resolves `photokit:<localIdentifier>` URIs to
 * sized JPEG thumbnails via PhotoKit. The timeline's device-library
 * items and the Backup grid both hand us these URIs; without this
 * fetcher the cells render empty because Coil's built-in fetchers only
 * know how to handle `file://` and `http(s)://` data.
 *
 * Coil registers a default `String → Uri` mapper, so anything pulled
 * into `AsyncImage` as a String is already a `coil3.Uri` by the time
 * fetchers run — we type the factory against [Uri] and gate on the
 * scheme rather than trying to intercept the raw String.
 *
 * Requests go through the shared [PhotoKitThumbnails] caching manager
 * and are LOCAL-FIRST: the common path never touches the network, so a
 * cell is as fast as Photos' own thumbnail cache. Only when the local
 * attempt returns nothing (iCloud-offloaded original with "optimise
 * storage") does a second request retry with network access — off the
 * scrolling hot path by construction, since it only ever runs for that
 * minority of assets.
 */
@OptIn(ExperimentalForeignApi::class)
internal class PhotoKitImageFetcher(
    private val localIdentifier: String
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        if (localIdentifier.isEmpty()) return null
        val asset = resolveAsset(localIdentifier) ?: return null
        val image = requestThumbnail(asset, allowNetwork = false)
            ?: requestThumbnail(asset, allowNetwork = true)
            ?: return null
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

    private suspend fun requestThumbnail(asset: PHAsset, allowNetwork: Boolean): UIImage? =
        suspendCancellableCoroutine { cont ->
            val requestId = PhotoKitThumbnails.manager.requestImageForAsset(
                asset = asset,
                targetSize = CGSizeMake(
                    PhotoKitThumbnails.TARGET_SIZE_PX,
                    PhotoKitThumbnails.TARGET_SIZE_PX
                ),
                contentMode = PHImageContentModeAspectFill,
                options = PhotoKitThumbnails.requestOptions(allowNetwork),
                resultHandler = { image, _ ->
                    if (cont.isActive) cont.resume(image)
                }
            )
            cont.invokeOnCancellation {
                PhotoKitThumbnails.manager.cancelImageRequest(requestId)
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
