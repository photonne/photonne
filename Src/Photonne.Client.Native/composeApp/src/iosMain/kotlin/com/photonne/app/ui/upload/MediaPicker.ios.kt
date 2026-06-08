package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSItemProvider
import platform.Foundation.NSMutableData
import platform.Foundation.NSURL
import platform.Foundation.appendData
import platform.Foundation.dataWithContentsOfURL
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
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
import platform.posix.memcpy
import kotlin.coroutines.resume

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
                results.mapNotNull { result ->
                    // Prefer the original PHAsset resource (GPS intact); fall
                    // back to the file representation if that's unavailable.
                    val viaAsset = if (authorized) {
                        result.assetIdentifier?.let { id ->
                            runCatching { loadFromAsset(id) }.getOrNull()
                        }
                    } else null
                    viaAsset ?: runCatching { loadPickedFile(result.itemProvider) }.getOrNull()
                }
            }
            onComplete(files)
        }
    }
}

// ── Original-asset path (guarantees GPS) ────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private suspend fun loadFromAsset(localIdentifier: String): PickedFile? {
    val fetch = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localIdentifier), options = null)
    val asset = fetch.firstObject as? PHAsset ?: return null
    val resource = primaryResource(asset) ?: return null
    val data = readResourceData(resource) ?: return null
    val filename = resource.originalFilename ?: "upload"
    val uti = resource.uniformTypeIdentifier
    val mime = uti?.let { UTType.typeWithIdentifier(it)?.preferredMIMEType }
        ?: "application/octet-stream"
    return PickedFile(
        name = filename,
        mimeType = mime,
        sizeBytes = data.length.toLong(),
        bytes = data.toByteArray()
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

@OptIn(ExperimentalForeignApi::class)
private suspend fun readResourceData(resource: PHAssetResource): NSData? =
    suspendCancellableCoroutine { cont ->
        val options = PHAssetResourceRequestOptions().apply {
            // iCloud-optimised devices need a download for unsynced originals.
            networkAccessAllowed = true
        }
        val accumulator = NSMutableData()
        PHAssetResourceManager.defaultManager().requestDataForAssetResource(
            resource = resource,
            options = options,
            dataReceivedHandler = { data -> if (data != null) accumulator.appendData(data) },
            completionHandler = { error: NSError? ->
                cont.resume(if (error != null) null else accumulator)
            }
        )
    }

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
    val data = loadFileData(provider, typeId) ?: return null

    val utType = UTType.typeWithIdentifier(typeId)
    val ext = utType?.preferredFilenameExtension ?: "dat"
    val mime = utType?.preferredMIMEType ?: "application/octet-stream"
    val baseName = provider.suggestedName ?: "upload"

    return PickedFile(
        name = "$baseName.$ext",
        mimeType = mime,
        sizeBytes = data.length.toLong(),
        bytes = data.toByteArray()
    )
}

/**
 * `loadFileRepresentation` hands back a temporary URL that iOS deletes the
 * instant the completion handler returns, so we must read the bytes
 * synchronously inside the handler before resuming.
 */
@OptIn(ExperimentalForeignApi::class)
private suspend fun loadFileData(provider: NSItemProvider, typeId: String): NSData? =
    suspendCancellableCoroutine { cont ->
        provider.loadFileRepresentationForTypeIdentifier(typeId) { url: NSURL?, _: NSError? ->
            val data = url?.let { NSData.dataWithContentsOfURL(it) }
            cont.resume(data)
        }
    }

// ── Shared helpers ──────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}

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
