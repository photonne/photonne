package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable

/**
 * Lightweight value type produced by the platform-specific picker and
 * fed to the upload pipeline. `bytes` holds the entire payload in memory
 * — fine for typical photos (a few MB), but we cap the per-file size
 * client-side in the view-model before queuing.
 */
data class PickedFile(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val bytes: ByteArray,
    /**
     * Original last-modified timestamp (epoch millis UTC) when the
     * platform picker exposes it; sent with the upload so the server
     * preserves the file's real date instead of the upload time.
     */
    val lastModifiedMillis: Long? = null
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = (name.hashCode() * 31 + mimeType.hashCode()) * 31 + sizeBytes.hashCode()
}

/**
 * Returns a function that, when called, opens the platform's native
 * media picker. The callback receives every file the user selected
 * (possibly empty if they cancelled).
 *
 * Concretely:
 * - Android: `ActivityResultContracts.OpenMultipleDocuments` (SAF — the
 *   Photo Picker strips GPS EXIF by design, so we avoid it)
 * - Desktop (JVM): Swing `JFileChooser` with multi-select
 * - iOS: PhotosUI `PHPickerViewController` with the original asset
 *   representation (no transcoding, so GPS/EXIF survive)
 */
@Composable
expect fun rememberMediaPicker(onPicked: (List<PickedFile>) -> Unit): () -> Unit

/** Thrown by platforms that haven't shipped a real picker yet. */
class MediaPickerUnavailable(message: String) : RuntimeException(message)
