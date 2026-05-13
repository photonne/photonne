package com.photonne.app.data.devicesync

import androidx.compose.runtime.Composable

/**
 * Platform glue for browsing a single folder of the user's device
 * (subfolders included) like a native gallery app. Each platform
 * surfaces its own concept of "folder access":
 *
 * - **Android** uses the Storage Access Framework. `pickFolder()` opens
 *   `ACTION_OPEN_DOCUMENT_TREE`, persists the resulting tree URI via
 *   `takePersistableUriPermission` so subsequent app launches can
 *   resume without re-prompting, and walks the tree with `DocumentFile`.
 * - **iOS** uses `UIDocumentPickerViewController` configured for folder
 *   selection. The returned URL is a security-scoped resource; the
 *   actual class keeps a bookmark in NSUserDefaults so we can re-open
 *   it later, and balances every `startAccessingSecurityScopedResource`
 *   with the matching `stop`.
 * - **Desktop** falls back to Swing `JFileChooser` for testing.
 *
 * The composable [rememberFolderPicker] hides the activity-result /
 * delegate dance behind a callback so screens can ask for a new folder
 * without holding a platform reference themselves.
 */
expect class DeviceGallery {
    /**
     * Reload a folder previously picked by the user from its persisted
     * `uri`. Returns null when the saved bookmark can no longer be
     * resolved (e.g. the SAF permission was revoked or the iOS
     * security-scoped bookmark went stale).
     */
    suspend fun restoreFolder(uri: String): DeviceFolderRef?

    /**
     * Walk [folder] recursively and return every image/video found.
     * The returned list contains only metadata — bytes are read on
     * demand later via [readBytes].
     */
    suspend fun listMedia(folder: DeviceFolderRef): List<DeviceMedia>

    /**
     * Stream the file's bytes through SHA-256 in fixed-size chunks
     * without holding the full payload in memory. Required for files
     * up to the server's 200 MB cap.
     */
    suspend fun computeSha256(media: DeviceMedia): String

    /**
     * Load the entire payload into memory ready to hand to
     * `UploadRepository.upload`. Caller is expected to release the
     * `ByteArray` reference quickly so the GC can reclaim it before
     * the next file's bytes are loaded.
     */
    suspend fun readBytes(media: DeviceMedia): ByteArray

    /**
     * Returns an opaque value that Coil can resolve to a thumbnail.
     * On Android this is the original `content://` URI string (Coil
     * 3's Android fetcher handles content URIs); on iOS/desktop it's
     * a `file://` URI.
     */
    fun thumbnailModel(media: DeviceMedia): String

    /**
     * True when this platform is capable of folder picking + walking.
     * Used to gate the Device Sync entry point in the Más menu.
     */
    val isSupported: Boolean
}

/** Thrown by platforms that don't implement device folder access. */
class DeviceGalleryUnavailable(message: String) : RuntimeException(message)

/**
 * Composable wrapper around the platform's folder picker. Returns a
 * lambda you can call from a click handler; the picker reports back
 * via [onPicked] with the new persisted folder, or null when the
 * user cancelled.
 */
@Composable
expect fun rememberDeviceFolderPicker(
    gallery: DeviceGallery,
    onPicked: (DeviceFolderRef?) -> Unit
): () -> Unit
