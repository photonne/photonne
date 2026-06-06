package com.photonne.app.data.devicebackup

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
     * Opens [media] as a streaming [kotlinx.io.Source] and runs [block]
     * with it plus the exact byte count. The platform owns the resource
     * lifecycle (stream close, security scope, temp-file cleanup), which
     * is why this is scoped instead of returning the source: large videos
     * must never be loaded into memory — a 500 MB allocation OOMs the
     * Android heap outright. The source is single-use; callers retrying
     * an upload call this again per attempt.
     */
    suspend fun <T> withUploadSource(
        media: DeviceMedia,
        block: suspend (source: kotlinx.io.Source, sizeBytes: Long) -> T
    ): T

    /**
     * Returns an opaque value that Coil can resolve to a thumbnail.
     * On Android this is the original `content://` URI string (Coil
     * 3's Android fetcher handles content URIs); on iOS/desktop it's
     * a `file://` URI.
     */
    fun thumbnailModel(media: DeviceMedia): String

    /**
     * Delete [media] from the device. Returns true if the file is no
     * longer present afterwards. Used by the "Free up space" flow
     * after we've confirmed the file is safely synced to the server.
     */
    suspend fun deleteFile(media: DeviceMedia): Boolean

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
