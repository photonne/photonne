package com.photonne.app.data.devicelibrary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSMutableArray
import platform.Photos.PHAsset
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val ASSET_URI_PREFIX = "photokit:"

@Composable
actual fun rememberDeviceMediaTrasher(
    onResult: (trashed: Boolean) -> Unit
): (uris: List<String>) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember { { uris -> deletePhotoKitAssets(uris) { currentOnResult.value(it) } } }
}

/** iOS has no permanent-delete API — "free up space" is the same
 *  Recently-Deleted flow as the trasher, one system sheet per batch. */
@Composable
actual fun rememberDeviceMediaDeleter(
    onResult: (deleted: Boolean) -> Unit
): (uris: List<String>) -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    return remember { { uris -> deletePhotoKitAssets(uris) { currentOnResult.value(it) } } }
}

private fun deletePhotoKitAssets(uris: List<String>, onResult: (Boolean) -> Unit) {
    val localIds = uris.mapNotNull { uri ->
        if (!uri.startsWith(ASSET_URI_PREFIX)) return@mapNotNull null
        uri.substring(ASSET_URI_PREFIX.length).takeIf { it.isNotEmpty() }
    }
    if (localIds.isEmpty()) {
        onResult(false)
        return
    }
    val fetched = PHAsset.fetchAssetsWithLocalIdentifiers(localIds, options = null)
    // `deleteAssets` is typed against NSFastEnumeration, which a
    // Kotlin List<*> doesn't statically implement — build an
    // NSMutableArray explicitly (same dance as DeviceGallery.ios).
    val targets = NSMutableArray(capacity = fetched.count)
    for (i in 0 until fetched.count.toInt()) {
        (fetched.objectAtIndex(i.toULong()) as? PHAsset)?.let { targets.addObject(it) }
    }
    if (targets.count.toInt() == 0) {
        onResult(false)
        return
    }
    // Photos shows its own confirmation sheet; assets land in
    // "Recently Deleted" for 30 days. The completion arrives on an
    // arbitrary queue — hop to main before touching Compose state.
    PHPhotoLibrary.sharedPhotoLibrary().performChanges(
        changeBlock = { PHAssetChangeRequest.deleteAssets(targets) },
        completionHandler = { success, _ ->
            dispatch_async(dispatch_get_main_queue()) {
                onResult(success)
            }
        }
    )
}
