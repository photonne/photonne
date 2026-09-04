package com.photonne.app.data.devicelibrary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.photonne.app.data.devicebackup.DeviceMedia
import com.photonne.app.data.devicebackup.DeviceMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSSortDescriptor
import platform.Foundation.timeIntervalSince1970
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetMediaTypeVideo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHChange
import platform.Photos.PHFetchOptions
import platform.Photos.PHPhotoLibrary
import platform.Photos.PHPhotoLibraryChangeObserverProtocol
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS implementation over **PhotoKit**, reading only the cheap
 * properties `PHAsset` itself carries (localIdentifier, mediaType,
 * dates). The expensive part of the old backup enumeration —
 * `PHAssetResource.assetResourcesForAsset`, one Photos-DB query per
 * asset just for the original filename and UTI — is deliberately NOT
 * done here: filenames are synthesized from the identifier and the
 * real name/bytes are only resolved when the backup flow hashes or
 * uploads. Timeline dedup against the server therefore relies on the
 * backup ledger's checksums (the `photokit:` URIs match), never on
 * (fileName, fileSize) — PhotoKit doesn't expose file size cheaply
 * either, so it stays 0 exactly like `DeviceGallery.ios` emits.
 */
actual class DeviceLibrary {

    actual val isSupported: Boolean = true

    /** PhotoKit has no per-asset folder concept — no buckets, no scoping. */
    actual val supportsBuckets: Boolean = false

    actual fun accessState(): DeviceLibraryAccess =
        when (PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)) {
            PHAuthorizationStatusAuthorized -> DeviceLibraryAccess.Full
            PHAuthorizationStatusLimited -> DeviceLibraryAccess.Partial
            PHAuthorizationStatusNotDetermined -> DeviceLibraryAccess.NotDetermined
            else -> DeviceLibraryAccess.Denied
        }

    actual suspend fun loadAll(scope: DeviceLibraryScope): List<DeviceMedia> {
        // The scope is ignored on purpose (supportsBuckets = false): there
        // are no folders to scope by, so the whole Camera Roll always shows.
        if (!accessState().canRead) return emptyList()
        return withContext(Dispatchers.Default) {
            val options = PHFetchOptions().apply {
                sortDescriptors = listOf(
                    NSSortDescriptor.sortDescriptorWithKey("creationDate", ascending = false)
                )
            }
            val result = PHAsset.fetchAssetsWithOptions(options)
            val count = result.count.toInt()
            val out = ArrayList<DeviceMedia>(count)
            for (i in 0 until count) {
                val asset = result.objectAtIndex(i.toULong()) as? PHAsset ?: continue
                val type = when (asset.mediaType) {
                    PHAssetMediaTypeImage -> DeviceMediaType.Image
                    PHAssetMediaTypeVideo -> DeviceMediaType.Video
                    else -> continue
                }
                val created = asset.creationDate
                    ?.let { (it.timeIntervalSince1970 * 1000.0).toLong() }
                val modified = asset.modificationDate
                    ?.let { (it.timeIntervalSince1970 * 1000.0).toLong() }
                out += DeviceMedia(
                    uri = "photokit:${asset.localIdentifier}",
                    displayName = syntheticFilename(asset, type),
                    relativePath = "",
                    mimeType = defaultMimeFor(type),
                    sizeBytes = 0L,
                    dateModifiedMillis = modified ?: created ?: 0L,
                    type = type,
                    dateCreatedMillis = created,
                    width = asset.pixelWidth.toInt().takeIf { it > 0 },
                    height = asset.pixelHeight.toInt().takeIf { it > 0 }
                )
            }
            out
        }
    }

    /** iOS models the whole Camera Roll as one virtual folder — the
     *  backup picker keeps its authorize-then-add flow, no buckets. */
    actual suspend fun listBuckets(): List<DeviceBucket> = emptyList()

    actual fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : NSObject(), PHPhotoLibraryChangeObserverProtocol {
            override fun photoLibraryDidChange(changeInstance: PHChange) {
                trySend(Unit)
            }
        }
        PHPhotoLibrary.sharedPhotoLibrary().registerChangeObserver(observer)
        awaitClose { PHPhotoLibrary.sharedPhotoLibrary().unregisterChangeObserver(observer) }
    }
}

private fun syntheticFilename(asset: PHAsset, type: DeviceMediaType): String {
    val ext = if (type == DeviceMediaType.Video) "mov" else "jpg"
    return "${asset.localIdentifier.substringBefore('/')}.$ext"
}

private fun defaultMimeFor(type: DeviceMediaType): String =
    if (type == DeviceMediaType.Video) "video/quicktime" else "image/jpeg"

@Composable
actual fun rememberDeviceLibraryAccessRequester(
    onResult: (DeviceLibraryAccess) -> Unit
): () -> Unit {
    val currentOnResult = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember {
        {
            scope.launch {
                val status =
                    PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
                if (status == PHAuthorizationStatusNotDetermined) {
                    suspendCancellableCoroutine { cont ->
                        PHPhotoLibrary.requestAuthorizationForAccessLevel(
                            accessLevel = PHAccessLevelReadWrite,
                            handler = { _ -> cont.resume(Unit) }
                        )
                    }
                }
                currentOnResult.value(DeviceLibrary().accessState())
            }
        }
    }
}
