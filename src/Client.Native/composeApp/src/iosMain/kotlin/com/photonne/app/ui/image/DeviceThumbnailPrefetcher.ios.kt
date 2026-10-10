package com.photonne.app.ui.image

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGSizeMake
import platform.Photos.PHAsset
import platform.Photos.PHImageContentModeAspectFill

private const val ASSET_URI_PREFIX = "photokit:"

/**
 * Bridges the viewport window to the shared [PhotoKitThumbnails]
 * caching manager. Start/stop use the SAME target size, content mode
 * and (local-only) options as the fetcher's requests — PhotoKit's
 * cache is keyed on all three, so any mismatch would warm entries the
 * fetcher never reads.
 */
@OptIn(ExperimentalForeignApi::class)
actual object DeviceThumbnailPrefetcher {

    /** Currently-warm assets, keyed by localIdentifier. Only mutated from
     *  the single settle-debounced caller, so a plain map suffices. */
    private var warm = mapOf<String, PHAsset>()

    actual fun setWindow(uris: List<String>) {
        val wanted = uris.mapNotNullTo(LinkedHashSet()) { uri ->
            if (!uri.startsWith(ASSET_URI_PREFIX)) return@mapNotNullTo null
            uri.substring(ASSET_URI_PREFIX.length).takeIf { it.isNotEmpty() }
        }
        val added = wanted.filterNot { it in warm }
        val removedAssets = warm.filterKeys { it !in wanted }.values.toList()
        if (added.isEmpty() && removedAssets.isEmpty()) return

        val addedAssets = ArrayList<PHAsset>(added.size)
        if (added.isNotEmpty()) {
            val fetched = PHAsset.fetchAssetsWithLocalIdentifiers(added, options = null)
            for (i in 0 until fetched.count.toInt()) {
                (fetched.objectAtIndex(i.toULong()) as? PHAsset)?.let { addedAssets += it }
            }
        }

        val size = CGSizeMake(
            PhotoKitThumbnails.TARGET_SIZE_PX,
            PhotoKitThumbnails.TARGET_SIZE_PX
        )
        if (addedAssets.isNotEmpty()) {
            PhotoKitThumbnails.manager.startCachingImagesForAssets(
                assets = addedAssets,
                targetSize = size,
                contentMode = PHImageContentModeAspectFill,
                options = PhotoKitThumbnails.requestOptions(allowNetwork = false)
            )
        }
        if (removedAssets.isNotEmpty()) {
            PhotoKitThumbnails.manager.stopCachingImagesForAssets(
                assets = removedAssets,
                targetSize = size,
                contentMode = PHImageContentModeAspectFill,
                options = PhotoKitThumbnails.requestOptions(allowNetwork = false)
            )
        }

        warm = buildMap {
            warm.forEach { (id, asset) -> if (id in wanted) put(id, asset) }
            addedAssets.forEach { put(it.localIdentifier, it) }
        }
    }
}
