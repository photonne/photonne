package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.writeData
import platform.Foundation.NSMutableArray
import platform.Foundation.NSSortDescriptor
import platform.Foundation.timeIntervalSince1970
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetMediaTypeVideo
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypeFullSizePhoto
import platform.Photos.PHAssetResourceTypeFullSizeVideo
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAssetResourceTypeVideo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHFetchOptions
import platform.Photos.PHPhotoLibrary
import platform.UniformTypeIdentifiers.UTType
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS implementation backed by **PhotoKit**. The user's Camera Roll
 * lives in the Photos library — a private CoreData database the file
 * system picker cannot see — so we surface it as a single virtual
 * folder (`photokit:userLibrary`) and enumerate it via `PHAsset`.
 *
 * Photo bytes come back asynchronously in chunks from
 * `PHAssetResourceManager.requestData`: streamed straight into the
 * pure-Kotlin [Sha256] for the dedup hash, and accumulated for the
 * upload payload. Deletion goes through
 * `PHPhotoLibrary.performChanges`, which surfaces iOS's own
 * confirmation dialog before removing assets.
 *
 * Thumbnails are not yet wired into Coil — the grid will render empty
 * cells on iOS until a `photokit:` Coil fetcher lands. Upload and
 * sync detection still work end-to-end without them.
 */
@OptIn(ExperimentalForeignApi::class)
actual class DeviceGallery {

    actual val isSupported: Boolean = true

    actual suspend fun restoreFolder(uri: String): DeviceFolderRef? =
        withContext(Dispatchers.Default) {
            if (uri != USER_LIBRARY_URI) return@withContext null
            val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
            if (!isAuthorized(status)) return@withContext null
            cameraRollRef()
        }

    actual suspend fun listMedia(folder: DeviceFolderRef): List<DeviceMedia> {
        if (folder.uri != USER_LIBRARY_URI) return emptyList()
        val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
        if (!isAuthorized(status)) return emptyList()
        return withContext(Dispatchers.Default) {
            val options = PHFetchOptions().apply {
                sortDescriptors = listOf(
                    NSSortDescriptor.sortDescriptorWithKey("creationDate", ascending = false)
                )
            }
            val result = PHAsset.fetchAssetsWithOptions(options)
            val count = result.count.toInt()
            val out = ArrayList<DeviceMedia>(count)
            // PhotoKit's NSPredicate "mediaType == %d" formatter is finicky
            // across cinterop bridges — filtering in Kotlin keeps the type
            // surface small and matches the Android walk's behaviour: only
            // images and videos, no audio or "unknown" rows.
            for (i in 0 until count) {
                val asset = result.objectAtIndex(i.toULong()) as? PHAsset ?: continue
                if (asset.mediaType != PHAssetMediaTypeImage &&
                    asset.mediaType != PHAssetMediaTypeVideo
                ) continue
                out += assetToMedia(asset) ?: continue
            }
            out
        }
    }

    actual suspend fun computeSha256(media: DeviceMedia): String {
        val asset = resolveAsset(media.uri)
            ?: throw DeviceGalleryUnavailable("Asset not found for ${media.displayName}")
        val resource = primaryResource(asset)
            ?: throw DeviceGalleryUnavailable("No data resource for ${media.displayName}")
        val digest = Sha256()
        streamResourceData(resource) { data ->
            val len = data.length.toInt()
            if (len == 0) return@streamResourceData
            val src = data.bytes ?: return@streamResourceData
            val buf = ByteArray(len)
            buf.usePinned { pinned ->
                memcpy(pinned.addressOf(0), src, len.toULong())
            }
            digest.update(buf, 0, len)
        }
        return digest.digest().toLowerHex()
    }

    actual suspend fun <T> withUploadSource(
        media: DeviceMedia,
        block: suspend (source: Source, sizeBytes: Long) -> T
    ): T {
        val asset = resolveAsset(media.uri)
            ?: throw DeviceGalleryUnavailable("Asset not found for ${media.displayName}")
        val resource = primaryResource(asset)
            ?: throw DeviceGalleryUnavailable("No data resource for ${media.displayName}")

        // PhotoKit only hands data out in push-style chunks, so spill them
        // to a temp file and serve the upload from a plain seekable source —
        // RAM usage stays flat no matter how big the video is.
        val tmpPath = NSTemporaryDirectory() + "photonne-upload-" + NSUUID().UUIDString
        NSFileManager.defaultManager.createFileAtPath(tmpPath, contents = null, attributes = null)
        val handle = NSFileHandle.fileHandleForWritingAtPath(tmpPath)
            ?: throw DeviceGalleryUnavailable("Cannot create temp file for ${media.displayName}")
        try {
            streamResourceData(resource) { data -> handle.writeData(data) }
        } finally {
            handle.closeFile()
        }

        val path = Path(tmpPath)
        try {
            val sizeBytes = SystemFileSystem.metadataOrNull(path)?.size ?: media.sizeBytes
            return SystemFileSystem.source(path).buffered().use { source ->
                block(source, sizeBytes)
            }
        } finally {
            runCatching { SystemFileSystem.delete(path, mustExist = false) }
        }
    }

    actual fun thumbnailModel(media: DeviceMedia): String = media.uri

    actual suspend fun deleteFile(media: DeviceMedia): Boolean {
        val asset = resolveAsset(media.uri) ?: return false
        return suspendCancellableCoroutine { cont ->
            // `deleteAssets` is typed against the NSFastEnumeration
            // protocol, which a Kotlin `List<*>` does not statically
            // implement. NSMutableArray does, so build a one-element
            // array explicitly rather than relying on the bridge.
            val targets = NSMutableArray(capacity = 1uL)
            targets.addObject(asset)
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = { PHAssetChangeRequest.deleteAssets(targets) },
                completionHandler = { success, _ -> cont.resume(success) }
            )
        }
    }
}

internal const val USER_LIBRARY_URI = "photokit:userLibrary"
private const val ASSET_URI_PREFIX = "photokit:"

internal fun cameraRollRef(): DeviceFolderRef =
    DeviceFolderRef(uri = USER_LIBRARY_URI, displayName = "Camera Roll")

private fun isAuthorized(status: Long): Boolean =
    status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited

/**
 * PhotoKit returns most asset metadata directly off `PHAsset`; the
 * filename and concrete MIME type only live on the resource side, so
 * we read them once and cache them into the [DeviceMedia] alongside
 * the asset id (encoded as `photokit:<localIdentifier>`).
 */
@OptIn(ExperimentalForeignApi::class)
private fun assetToMedia(asset: PHAsset): DeviceMedia? {
    val type = when (asset.mediaType) {
        PHAssetMediaTypeImage -> DeviceMediaType.Image
        PHAssetMediaTypeVideo -> DeviceMediaType.Video
        else -> return null
    }
    val resource = primaryResource(asset)
    val filename = resource?.originalFilename ?: fallbackFilename(asset, type)
    val uti = resource?.uniformTypeIdentifier
    val mime = uti?.let { UTType.typeWithIdentifier(it)?.preferredMIMEType() }
        ?: defaultMimeFor(type)
    val created = asset.creationDate?.let { (it.timeIntervalSince1970 * 1000.0).toLong() }
    val modified = asset.modificationDate?.let { (it.timeIntervalSince1970 * 1000.0).toLong() }
    return DeviceMedia(
        uri = "$ASSET_URI_PREFIX${asset.localIdentifier}",
        displayName = filename,
        relativePath = "",
        mimeType = mime,
        // PhotoKit doesn't expose file size on the public API; the
        // server-side dedup pipeline gets the real number once we
        // upload, so 0 here is a placeholder that the rest of the UI
        // tolerates (it shows "0 B" in the cell, just like an empty
        // Android tree URI does).
        sizeBytes = 0L,
        dateModifiedMillis = modified ?: created ?: 0L,
        type = type
    )
}

/**
 * Pick the resource that represents the original photo or video file.
 * Live Photos and edited HEICs come with extra resources (paired
 * videos, adjustment data); for backup we only want the primary
 * payload — prefer the `Photo` / `Video` types, and fall back to the
 * "FullSize" variants if the user has only the edited copy locally.
 */
@OptIn(ExperimentalForeignApi::class)
private fun primaryResource(asset: PHAsset): PHAssetResource? {
    val resources = PHAssetResource.assetResourcesForAsset(asset)
    var fullSize: PHAssetResource? = null
    for (any in resources) {
        val resource = any as? PHAssetResource ?: continue
        when (resource.type) {
            PHAssetResourceTypePhoto, PHAssetResourceTypeVideo -> return resource
            PHAssetResourceTypeFullSizePhoto, PHAssetResourceTypeFullSizeVideo -> fullSize = resource
        }
    }
    return fullSize
}

@OptIn(ExperimentalForeignApi::class)
private fun resolveAsset(uri: String): PHAsset? {
    if (!uri.startsWith(ASSET_URI_PREFIX)) return null
    val localId = uri.removePrefix(ASSET_URI_PREFIX)
    if (localId.isEmpty() || localId == "userLibrary") return null
    val result = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localId), options = null)
    return result.firstObject as? PHAsset
}

private fun fallbackFilename(asset: PHAsset, type: DeviceMediaType): String {
    val ext = if (type == DeviceMediaType.Video) "mov" else "jpg"
    val id = asset.localIdentifier.substringBefore('/')
    return "$id.$ext"
}

private fun defaultMimeFor(type: DeviceMediaType): String =
    if (type == DeviceMediaType.Video) "video/quicktime" else "image/jpeg"

/**
 * Bridge `PHAssetResourceManager.requestData`'s callback API into a
 * suspending function. The data handler fires zero or more times with
 * sequential NSData chunks; completion fires once with either nil or
 * the failure.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun streamResourceData(
    resource: PHAssetResource,
    onChunk: (NSData) -> Unit
) = suspendCancellableCoroutine { cont ->
    val options = PHAssetResourceRequestOptions().apply {
        // Photos backed by iCloud may need a download; without this
        // flag the request fails immediately for unsynced assets on
        // devices with optimised storage enabled.
        networkAccessAllowed = true
    }
    PHAssetResourceManager.defaultManager().requestDataForAssetResource(
        resource = resource,
        options = options,
        dataReceivedHandler = { data ->
            if (data != null) onChunk(data)
        },
        completionHandler = { error: NSError? ->
            if (error != null) {
                cont.resumeWithException(
                    DeviceGalleryUnavailable(
                        error.localizedDescription
                    )
                )
            } else {
                cont.resume(Unit)
            }
        }
    )
}

/**
 * Bridge `PHPhotoLibrary.requestAuthorization` into a suspending
 * function. Treats both full and limited access as success — the
 * Photos picker handles the limited-library nuance for the user.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun requestAuthorization(): Boolean =
    suspendCancellableCoroutine { cont ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(
            accessLevel = PHAccessLevelReadWrite,
            handler = { status -> cont.resume(isAuthorized(status)) }
        )
    }

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberDeviceFolderPicker(
    gallery: DeviceGallery,
    onPicked: (DeviceFolderRef?) -> Unit
): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    return remember(gallery) {
        {
            scope.launch {
                val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
                val granted = when {
                    isAuthorized(status) -> true
                    status == PHAuthorizationStatusNotDetermined -> requestAuthorization()
                    else -> false
                }
                currentOnPicked.value(if (granted) cameraRollRef() else null)
            }
        }
    }
}
