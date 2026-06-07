package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
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
 * iOS picker backed by **PhotosUI** `PHPickerViewController` — the native
 * out-of-process gallery (iOS 14+). Two reasons it's the right pick here
 * instead of a file browser:
 *
 *  - It needs **no Photo Library permission**: the picker runs in a
 *    separate process and only hands back the items the user chose.
 *  - Unlike Android's Photo Picker, iOS does **not** redact GPS/EXIF.
 *    Combined with `preferredAssetRepresentationMode = .current` (no
 *    transcoding) and `loadFileRepresentation` (a copy of the original
 *    file), the uploaded bytes keep the full metadata.
 *
 * We don't send a client-side timestamp from here: without Photo Library
 * access we can't read `PHAsset.creationDate`, but the server derives the
 * capture date from the embedded EXIF (photos) / QuickTime metadata
 * (videos) anyway, which is the authoritative source.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberMediaPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    // Strong reference holder: the delegate must outlive the call until the
    // out-of-process picker fires its callback, or it gets deallocated and
    // the selection is silently dropped.
    val delegateHolder = remember { DelegateHolder() }
    return remember {
        {
            val host = topViewController()
            if (host == null) {
                currentOnPicked.value(emptyList())
            } else {
                val config = PHPickerConfiguration().apply {
                    selectionLimit = MAX_SELECTION.toLong()
                    filter = PHPickerFilter.anyFilterMatchingSubfilters(
                        listOf(PHPickerFilter.imagesFilter(), PHPickerFilter.videosFilter())
                    )
                    // Avoid transcoding to a "compatible" format, which would
                    // re-encode the file and strip metadata.
                    preferredAssetRepresentationMode = PHPickerConfigurationAssetRepresentationModeCurrent
                }
                val picker = PHPickerViewController(configuration = config)
                val delegate = PhotoPickerDelegate { files ->
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
    private val onComplete: (List<PickedFile>) -> Unit
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    // PHPicker callbacks land on a background queue; hop back to Main for the
    // Compose state update the callback ultimately drives.
    private val scope: CoroutineScope = MainScope()

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val providers = didFinishPicking
            .mapNotNull { (it as? PHPickerResult)?.itemProvider }
        scope.launch {
            val files = withContext(Dispatchers.Default) {
                providers.mapNotNull { provider ->
                    runCatching { loadPickedFile(provider) }.getOrNull()
                }
            }
            onComplete(files)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun loadPickedFile(provider: NSItemProvider): PickedFile? {
    // The first registered type identifier is the item's native representation
    // (e.g. public.heic, com.apple.quicktime-movie) — what we want to upload
    // verbatim. Anything else risks a transcoded variant.
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
