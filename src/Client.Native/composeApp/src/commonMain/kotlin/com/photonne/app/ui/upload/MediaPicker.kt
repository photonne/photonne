package com.photonne.app.ui.upload

import androidx.compose.runtime.Composable
import kotlinx.io.Source

/**
 * Lightweight value type produced by the platform-specific picker and
 * fed to the upload pipeline. It holds a reference to the file, never its
 * payload: [content] is opened when the queue uploads it and streamed to the
 * server, so a batch of large videos doesn't sit in memory. One that can't be
 * read comes back flagged [unreadable], so the view-model can tell the user
 * what was left out instead of dropping it.
 */
data class PickedFile(
    val name: String,
    val mimeType: String,
    /** Best size known when picking; 0 when the platform only learns it on read (PhotoKit). */
    val sizeBytes: Long,
    /** Null only for an [unreadable] file. */
    val content: PickedContent?,
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

/**
 * Where a picked file's bytes live (a content URI, a path, a PhotoKit
 * resource…). [open] can run more than once: a retry reopens it from scratch.
 */
interface PickedContent {
    /**
     * Opens the content for one upload and runs [block] over it with its
     * exact byte count (the upload's Content-Length); the source is closed
     * afterwards. Throws when the file can no longer be read.
     */
    suspend fun <T> open(block: suspend (source: Source, sizeBytes: Long) -> T): T

    /** What the upload list hands to the image loader as a thumbnail; null shows an icon. */
    val thumbnailModel: Any? get() = null

    /** Frees anything kept on disk for this file (iOS temp copies). Safe to call twice. */
    fun release() {}
}

/** A picked file the platform couldn't read; the queue skips and reports it. */
fun unreadablePickedFile(name: String) =
    PickedFile(name = name, mimeType = "", sizeBytes = 0L, content = null, unreadable = true)

/** The motion clip of a picked Live Photo, streamed like its still. */
class PickedMotionClip(
    val name: String,
    val mimeType: String,
    val content: PickedContent
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
