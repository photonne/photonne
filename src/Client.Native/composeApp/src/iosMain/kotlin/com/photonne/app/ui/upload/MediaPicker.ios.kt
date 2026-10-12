package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSItemProvider
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.writeData
import com.photonne.app.data.devicebackup.pairedVideoResource
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaSubtypePhotoLive
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAssetResource
import platform.Photos.PHAssetResourceManager
import platform.Photos.PHAssetResourceRequestOptions
import platform.Photos.PHAssetResourceTypeFullSizePhoto
import platform.Photos.PHAssetResourceTypeFullSizeVideo
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAssetResourceTypeVideo
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCurrent
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS picker backed by **PhotosUI** `PHPickerViewController`.
 *
 * Unlike Android, iOS doesn't redact GPS at the metadata level — it scopes
 * *which* photos an app can see. To guarantee location survives we take the
 * same route as the backup: when the user grants Photo Library access we
 * build the picker with a `photoLibrary` so each result carries an
 * `assetIdentifier`, fetch the `PHAsset`, and read its **original resource**
 * via `PHAssetResourceManager` (full metadata, GPS included).
 *
 * If access is denied (or an asset can't be read) we fall back to the
 * permission-free path: `loadFileRepresentation` with the original
 * representation mode, which preserves metadata for the picked item in
 * practice but isn't guaranteed across every iOS version.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberMediaPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    // Strong reference holder: the delegate must outlive the call until the
    // out-of-process picker fires its callback, or it gets deallocated and
    // the selection is silently dropped.
    val delegateHolder = remember { DelegateHolder() }
    return remember {
        {
            scope.launch {
                val authorized = ensurePhotoAuthorization()
                val host = topViewController()
                if (host == null) {
                    currentOnPicked.value(emptyList())
                    return@launch
                }
                val config = if (authorized) {
                    // photoLibrary makes results carry assetIdentifier so we can
                    // read the original PHAsset resource (GPS intact).
                    PHPickerConfiguration(photoLibrary = PHPhotoLibrary.sharedPhotoLibrary())
                } else {
                    PHPickerConfiguration()
                }.apply {
                    selectionLimit = MAX_SELECTION.toLong()
                    filter = PHPickerFilter.anyFilterMatchingSubfilters(
                        listOf(PHPickerFilter.imagesFilter(), PHPickerFilter.videosFilter())
                    )
                    // Avoid transcoding to a "compatible" format, which would
                    // re-encode the file and strip metadata on the fallback path.
                    preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
                }
                val picker = PHPickerViewController(configuration = config)
                val delegate = PhotoPickerDelegate(authorized) { files ->
                    delegateHolder.delegate = null
                    currentOnPicked.value(files)
                }
                delegateHolder.delegate = delegate
                picker.delegate = delegate
                host.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

/** Plain (non-observable) box so reassigning the delegate doesn't recompose. */
private class DelegateHolder {
    var delegate: PhotoPickerDelegate? = null
}

@OptIn(ExperimentalForeignApi::class)
private class PhotoPickerDelegate(
    private val authorized: Boolean,
    private val onComplete: (List<PickedFile>) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    // PHPicker callbacks land on a background queue; hop back to Main for the
    // Compose state update the callback ultimately drives.
    private val scope: CoroutineScope = kotlinx.coroutines.MainScope()

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.mapNotNull { it as? PHPickerResult }
        scope.launch {
            val files = withContext(Dispatchers.Default) {
                results.map { result ->
                    // Prefer the original PHAsset resource (GPS intact); fall
                    // back to the file representation if that's unavailable.
                    val viaAsset = if (authorized) {
                        result.assetIdentifier?.let { id ->
                            runCatching { describeAsset(id) }.getOrNull()
                        }
                    } else null
                    // Neither path could read it: reported, not silently dropped.
                    viaAsset ?: runCatching { loadPickedFile(result.itemProvider) }.getOrNull()
                        ?: unreadablePickedFile(result.itemProvider.suggestedName ?: "upload")
                }
            }
            onComplete(files)
        }
    }
}

// ── Original-asset path (guarantees GPS) ────────────────────────────────────

/**
 * The picked asset's original resource, not read yet: PhotoKit hands its bytes
 * out only when the queue uploads it (see [PhotoKitContent]). PhotoKit doesn't
 * expose the size up front, so it stays 0 until then.
 */
@OptIn(ExperimentalForeignApi::class)
private fun describeAsset(localIdentifier: String): PickedFile? {
    val fetch = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localIdentifier), options = null)
    val asset = fetch.firstObject as? PHAsset ?: return null
    val resource = primaryResource(asset) ?: return null
    val uti = resource.uniformTypeIdentifier
    val mime = uti?.let { UTType.typeWithIdentifier(it)?.preferredMIMEType }
        ?: "application/octet-stream"
    return PickedFile(
        name = resource.originalFilename ?: "upload",
        mimeType = mime,
        sizeBytes = 0L,
        content = PhotoKitContent(resource, thumbnailModel = "$PHOTOKIT_URI_PREFIX$localIdentifier"),
        motionClip = motionClipOf(asset)
    )
}

/**
 * The paired video of a Live Photo, or null when [asset] isn't one: the still
 * alone still uploads. The permission-free fallback has no asset to ask, so
 * picks without Photos access stay stills.
 */
@OptIn(ExperimentalForeignApi::class)
private fun motionClipOf(asset: PHAsset): PickedMotionClip? {
    if (asset.mediaType != PHAssetMediaTypeImage ||
        (asset.mediaSubtypes and PHAssetMediaSubtypePhotoLive) == 0uL
    ) return null
    val resource = pairedVideoResource(asset) ?: return null
    return PickedMotionClip(
        name = resource.originalFilename,
        mimeType = UTType.typeWithIdentifier(resource.uniformTypeIdentifier)?.preferredMIMEType
            ?: "video/quicktime",
        content = PhotoKitContent(resource, thumbnailModel = null)
    )
}

/**
 * Pick the resource that represents the original photo or video file —
 * mirrors the backup's selection so edited/Live-Photo extras are ignored.
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

/**
 * A PhotoKit resource read at upload time. PhotoKit only hands data out in
 * push-style chunks (downloading from iCloud if needed), so each [open]
 * spills them to a temp file, streams the upload from it and deletes it:
 * memory stays flat however big the video is.
 */
@OptIn(ExperimentalForeignApi::class)
private class PhotoKitContent(
    private val resource: PHAssetResource,
    override val thumbnailModel: Any?
) : PickedContent {
    override suspend fun <T> open(block: suspend (source: Source, sizeBytes: Long) -> T): T {
        val path = Path(newTempPath())
        try {
            spillResource(resource, path.toString())
            val size = SystemFileSystem.metadataOrNull(path)?.size
                ?: throw IllegalStateException("Cannot read the copy of ${resource.originalFilename}")
            return SystemFileSystem.source(path).buffered().use { block(it, size) }
        } finally {
            runCatching { SystemFileSystem.delete(path, mustExist = false) }
        }
    }
}

/**
 * A temp copy the picker made because iOS deletes the original as soon as
 * `loadFileRepresentation` returns. Streamed on each [open]; [release] deletes it.
 */
private class TempFileContent(private val path: String) : PickedContent {
    override suspend fun <T> open(block: suspend (source: Source, sizeBytes: Long) -> T): T {
        val file = Path(path)
        val size = SystemFileSystem.metadataOrNull(file)?.size
            ?: throw IllegalStateException("The picked file is no longer available")
        return SystemFileSystem.source(file).buffered().use { block(it, size) }
    }

    override val thumbnailModel: Any get() = path

    override fun release() {
        runCatching { SystemFileSystem.delete(Path(path), mustExist = false) }
    }
}

/**
 * Writes [resource]'s chunks to [path] as PhotoKit delivers them. Cancelling
 * the caller cancels the PhotoKit request; a failure (iCloud, I/O) throws.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun spillResource(resource: PHAssetResource, path: String) {
    NSFileManager.defaultManager.createFileAtPath(path, contents = null, attributes = null)
    val handle = NSFileHandle.fileHandleForWritingAtPath(path)
        ?: throw IllegalStateException("Cannot create a temp file for ${resource.originalFilename}")
    // PhotoKit keeps calling the data handler on its own queue until a
    // cancelled request winds down, and writing to a closed NSFileHandle
    // raises an NSException Kotlin can't catch. Writes and the close are
    // serialised so no chunk lands after close.
    val handleLock = NSLock()
    var handleClosed = false
    try {
        suspendCancellableCoroutine { cont ->
            val options = PHAssetResourceRequestOptions().apply {
                // iCloud-optimised devices need a download for unsynced originals.
                networkAccessAllowed = true
            }
            val manager = PHAssetResourceManager.defaultManager()
            val requestId = manager.requestDataForAssetResource(
                resource = resource,
                options = options,
                dataReceivedHandler = { data ->
                    if (data != null) {
                        handleLock.lock()
                        try {
                            if (!handleClosed) handle.writeData(data)
                        } finally {
                            handleLock.unlock()
                        }
                    }
                },
                completionHandler = { error: NSError? ->
                    if (error != null) {
                        cont.resumeWithException(IllegalStateException(error.localizedDescription))
                    } else {
                        cont.resume(Unit)
                    }
                }
            )
            cont.invokeOnCancellation { manager.cancelDataRequest(requestId) }
        }
    } finally {
        handleLock.lock()
        try {
            handleClosed = true
            handle.closeFile()
        } finally {
            handleLock.unlock()
        }
    }
}

private fun newTempPath(): String = NSTemporaryDirectory() + "photonne-pick-" + NSUUID().UUIDString

// ── Authorization ───────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private suspend fun ensurePhotoAuthorization(): Boolean {
    val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
    return when {
        isAuthorized(status) -> true
        status == PHAuthorizationStatusNotDetermined -> requestAuthorization()
        else -> false
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun requestAuthorization(): Boolean =
    suspendCancellableCoroutine { cont ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(
            accessLevel = PHAccessLevelReadWrite,
            handler = { status -> cont.resume(isAuthorized(status)) }
        )
    }

private fun isAuthorized(status: PHAuthorizationStatus): Boolean =
    status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited

// ── File-representation fallback (no permission) ────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private suspend fun loadPickedFile(provider: NSItemProvider): PickedFile? {
    // The first registered type identifier is the item's native representation
    // (e.g. public.heic, com.apple.quicktime-movie) — what we want verbatim.
    val typeId = provider.registeredTypeIdentifiers.firstOrNull() as? String ?: return null

    val utType = UTType.typeWithIdentifier(typeId)
    val ext = utType?.preferredFilenameExtension ?: "dat"
    val mime = utType?.preferredMIMEType ?: "application/octet-stream"
    val baseName = provider.suggestedName ?: "upload"
    val copy = copyFileRepresentation(provider, typeId) ?: return null

    return PickedFile(
        name = "$baseName.$ext",
        mimeType = mime,
        sizeBytes = copy.second,
        content = TempFileContent(copy.first)
    )
}

/**
 * `loadFileRepresentation` hands back a temporary URL that iOS deletes the
 * instant the completion handler returns, so the file is copied (on disk,
 * never into memory) to a temp path of ours inside the handler. Returns that
 * path and its size; the upload streams from it and then deletes it.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun copyFileRepresentation(provider: NSItemProvider, typeId: String): Pair<String, Long>? =
    suspendCancellableCoroutine { cont ->
        provider.loadFileRepresentationForTypeIdentifier(typeId) { url: NSURL?, _: NSError? ->
            val source = url?.path
            val target = newTempPath()
            val copied = source != null &&
                NSFileManager.defaultManager.copyItemAtPath(source, toPath = target, error = null)
            val size = if (copied) {
                (NSFileManager.defaultManager.attributesOfItemAtPath(target, error = null)
                    ?.get(NSFileSize) as? NSNumber)?.longLongValue
            } else null
            cont.resume(size?.let { target to it })
        }
    }

// ── Shared helpers ──────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private fun topViewController(): UIViewController? {
    val scene = UIApplication.sharedApplication.connectedScenes
        .firstOrNull { it is UIWindowScene } as? UIWindowScene
    var vc = scene?.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) {
        vc = vc.presentedViewController
    }
    return vc
}

private const val MAX_SELECTION = 50

/** Same scheme the image loader's PhotoKit fetcher reads (thumbnails of picked assets). */
private const val PHOTOKIT_URI_PREFIX = "photokit:"
