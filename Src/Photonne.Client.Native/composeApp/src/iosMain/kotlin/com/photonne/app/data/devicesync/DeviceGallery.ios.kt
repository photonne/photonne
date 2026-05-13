package com.photonne.app.data.devicesync

import androidx.compose.runtime.Composable

/**
 * iOS device sync is stubbed for v1, matching the existing stubs in
 * [com.photonne.app.ui.actions.AssetSharing] and
 * [com.photonne.app.ui.upload.MediaPicker]. A real implementation
 * requires:
 *
 * 1. `UIDocumentPickerViewController(forOpeningContentTypes:[UTTypeFolder])`
 *    bridged from Compose via the topmost `UIViewController`.
 * 2. Security-scoped URL bookmarks resolved with
 *    `NSURL.URLByResolvingBookmarkData(...)` for re-opening across
 *    launches — needs `NSError*` out-parameter handling.
 * 3. `NSFileManager.enumeratorAtURL(...)` for the recursive walk
 *    and `NSData.dataWithContentsOfURL` for the streamed read.
 *
 * Each piece is straightforward but requires iterative
 * compile-and-test on a real Mac toolchain, which the current build
 * harness can't run. Until that's wired up the screen surfaces a
 * localized "available in Android only for now" banner.
 */
actual class DeviceGallery {

    actual val isSupported: Boolean = false

    actual suspend fun restoreFolder(uri: String): DeviceFolderRef? = null

    actual suspend fun listMedia(folder: DeviceFolderRef): List<DeviceMedia> = emptyList()

    actual suspend fun computeSha256(media: DeviceMedia): String {
        throw DeviceGalleryUnavailable("Device sync is not implemented on iOS yet")
    }

    actual suspend fun readBytes(media: DeviceMedia): ByteArray {
        throw DeviceGalleryUnavailable("Device sync is not implemented on iOS yet")
    }

    actual fun thumbnailModel(media: DeviceMedia): String = media.uri
}

@Composable
actual fun rememberDeviceFolderPicker(
    gallery: DeviceGallery,
    onPicked: (DeviceFolderRef?) -> Unit
): () -> Unit = {
    // The UI gates the entry on `gallery.isSupported`, so this branch
    // is unreachable from a well-formed call site. Throw rather than
    // silently call back with null so an accidental wiring shows up
    // as a clear runtime error instead of a no-op.
    throw DeviceGalleryUnavailable("Device sync is not implemented on iOS yet")
}
