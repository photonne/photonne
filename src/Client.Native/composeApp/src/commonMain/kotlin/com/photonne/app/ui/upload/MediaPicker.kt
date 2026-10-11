package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable

/**
 * Lightweight value type produced by the platform-specific picker and
 * fed to the upload pipeline. `bytes` holds the entire payload in memory
 * — fine for typical photos (a few MB). Pickers check the size BEFORE
 * reading: a file over [UploadViewModel.MAX_BYTES_PER_FILE] comes back
 * with empty `bytes` and its real `sizeBytes` (see [oversizePickedFile]),
 * and one that can't be read comes back flagged [unreadable], so the
 * view-model can tell the user what was left out instead of dropping it.
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
    val lastModifiedMillis: Long? = null,
    /**
     * iOS Live Photo: the paired video PhotoKit keeps as a second resource of
     * the asset. Uploaded right after the still so the server can show it as
     * a motion photo. Null everywhere else (Android motion photos carry the
     * clip inside the JPEG).
     */
    val motionClip: PickedMotionClip? = null,
    /** The picker couldn't read it (permission, iCloud download, I/O…): not queued, only reported. */
    val unreadable: Boolean = false
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = (name.hashCode() * 31 + mimeType.hashCode()) * 31 + sizeBytes.hashCode()
}

/** A picked file left unread because it's over the upload cap; the queue skips and reports it. */
fun oversizePickedFile(name: String, mimeType: String, sizeBytes: Long, lastModifiedMillis: Long? = null) =
    PickedFile(
        name = name,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        bytes = ByteArray(0),
        lastModifiedMillis = lastModifiedMillis
    )

/** A picked file the platform couldn't read; the queue skips and reports it. */
fun unreadablePickedFile(name: String) =
    PickedFile(name = name, mimeType = "", sizeBytes = 0L, bytes = ByteArray(0), unreadable = true)

/** The motion clip of a picked Live Photo, read whole like its still. */
class PickedMotionClip(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
)

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
