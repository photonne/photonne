package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSError
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsDirectoryKey
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.readDataOfLength
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIModalPresentationFormSheet
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * iOS implementation backed by `UIDocumentPickerViewController` for
 * folder selection and `NSFileManager` for the recursive walk.
 *
 * The picked folder's URL is *security-scoped*: iOS only grants access
 * while we hold an active scope on the URL. We persist the grant
 * across launches by storing a bookmark (base64-encoded `NSData`)
 * inside `DeviceFolderRef.uri`, then re-resolving it at restore time.
 * One scope is held open at a time for the active folder so individual
 * `readBytes` / `computeSha256` calls — and Coil's file:// thumbnail
 * loads — can operate without per-call start/stop dances.
 *
 * SHA-256 uses the pure-Kotlin [Sha256] implementation rather than
 * CommonCrypto because Kotlin/Native doesn't ship cinterop bindings
 * for `<CommonCrypto/CommonDigest.h>` out of the box.
 */
@OptIn(ExperimentalForeignApi::class)
actual class DeviceGallery {

    actual val isSupported: Boolean = true

    // Holds the resolved URL while its security scope is open. We balance
    // every `startAccessingSecurityScopedResource` with a `stop` either
    // when a new folder replaces it or when the resolve fails partway.
    private var activeUrl: NSURL? = null
    private var activeBookmark: String? = null

    actual suspend fun restoreFolder(uri: String): DeviceFolderRef? =
        withContext(Dispatchers.Default) {
            val url = acquireUrl(uri) ?: return@withContext null
            DeviceFolderRef(
                uri = uri,
                displayName = url.lastPathComponent ?: "Folder"
            )
        }

    actual suspend fun listMedia(folder: DeviceFolderRef): List<DeviceMedia> =
        withContext(Dispatchers.Default) {
            val baseUrl = acquireUrl(folder.uri) ?: return@withContext emptyList()
            val fileManager = NSFileManager.defaultManager
            val enumerator = fileManager.enumeratorAtURL(
                url = baseUrl,
                includingPropertiesForKeys = listOf(NSURLIsDirectoryKey),
                options = NSDirectoryEnumerationSkipsHiddenFiles,
                errorHandler = null
            ) ?: return@withContext emptyList()

            val results = mutableListOf<DeviceMedia>()
            val basePath = baseUrl.path?.let { if (it.endsWith("/")) it else "$it/" } ?: ""
            while (true) {
                val obj = enumerator.nextObject() ?: break
                val fileUrl = obj as? NSURL ?: continue
                val ext = fileUrl.pathExtension ?: continue
                val type = mediaTypeFromExtension(ext) ?: continue
                val mime = mimeFromExtension(ext) ?: continue
                val name = fileUrl.lastPathComponent ?: continue
                val path = fileUrl.path ?: continue

                val attrs = fileManager.attributesOfItemAtPath(path, error = null)
                val size = (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
                val modDate = attrs?.get(NSFileModificationDate) as? NSDate
                val modMs = modDate?.let { (it.timeIntervalSince1970 * 1000.0).toLong() } ?: 0L
                val absolute = fileUrl.absoluteString ?: continue
                val relDir = relativeDirectory(basePath, path, name)

                results += DeviceMedia(
                    uri = absolute,
                    displayName = name,
                    relativePath = relDir,
                    mimeType = mime,
                    sizeBytes = size,
                    dateModifiedMillis = modMs,
                    type = type
                )
            }
            results.sortedByDescending { it.dateModifiedMillis }
        }

    actual suspend fun computeSha256(media: DeviceMedia): String =
        withContext(Dispatchers.Default) {
            val url = NSURL.URLWithString(media.uri)
                ?: throw DeviceGalleryUnavailable("Invalid uri for ${media.displayName}")
            val path = url.path
                ?: throw DeviceGalleryUnavailable("No path for ${media.displayName}")
            val handle = NSFileHandle.fileHandleForReadingAtPath(path)
                ?: throw DeviceGalleryUnavailable("Cannot open ${media.displayName}")
            try {
                val digest = Sha256()
                val chunkSize: ULong = (64uL * 1024uL)
                while (true) {
                    val data = handle.readDataOfLength(chunkSize)
                    val n = data.length.toInt()
                    if (n == 0) break
                    val src = data.bytes ?: break
                    val buf = ByteArray(n)
                    buf.usePinned { pinned ->
                        memcpy(pinned.addressOf(0), src, n.toULong())
                    }
                    digest.update(buf, 0, n)
                }
                digest.digest().toLowerHex()
            } finally {
                handle.closeFile()
            }
        }

    actual suspend fun readBytes(media: DeviceMedia): ByteArray =
        withContext(Dispatchers.Default) {
            val url = NSURL.URLWithString(media.uri)
                ?: throw DeviceGalleryUnavailable("Invalid uri for ${media.displayName}")
            val data = NSData.dataWithContentsOfURL(url)
                ?: throw DeviceGalleryUnavailable("Cannot read ${media.displayName}")
            val len = data.length.toInt()
            if (len == 0) return@withContext ByteArray(0)
            val src = data.bytes ?: return@withContext ByteArray(0)
            val out = ByteArray(len)
            out.usePinned { pinned ->
                memcpy(pinned.addressOf(0), src, len.toULong())
            }
            out
        }

    actual fun thumbnailModel(media: DeviceMedia): String = media.uri

    actual suspend fun deleteFile(media: DeviceMedia): Boolean =
        withContext(Dispatchers.Default) {
            val url = NSURL.URLWithString(media.uri) ?: return@withContext false
            val ok = NSFileManager.defaultManager.removeItemAtURL(url, error = null)
            ok || (url.path?.let { !NSFileManager.defaultManager.fileExistsAtPath(it) } == true)
        }

    /**
     * Resolve [bookmarkB64] to a usable URL and hold its security scope.
     * Re-using the cached [activeUrl] avoids the bookmark round-trip on
     * subsequent calls within the same folder session.
     */
    private fun acquireUrl(bookmarkB64: String): NSURL? {
        activeUrl?.let { existing ->
            if (activeBookmark == bookmarkB64) return existing
        }
        val data = decodeBase64(bookmarkB64) ?: return null
        val resolved = memScoped {
            val isStale = alloc<BooleanVar>()
            val errVar = alloc<ObjCObjectVar<NSError?>>()
            NSURL.URLByResolvingBookmarkData(
                bookmarkData = data,
                options = 0uL,
                relativeToURL = null,
                bookmarkDataIsStale = isStale.ptr,
                error = errVar.ptr
            )
        } ?: return null
        if (!resolved.startAccessingSecurityScopedResource()) return null
        // Release the previous folder's scope before swapping it in;
        // iOS counts these calls so leaving the old one open would
        // leak the grant until the process exits.
        releaseActive()
        activeUrl = resolved
        activeBookmark = bookmarkB64
        return resolved
    }

    private fun releaseActive() {
        activeUrl?.stopAccessingSecurityScopedResource()
        activeUrl = null
        activeBookmark = null
    }

    internal fun adoptActive(url: NSURL, bookmark: String) {
        releaseActive()
        if (url.startAccessingSecurityScopedResource()) {
            activeUrl = url
            activeBookmark = bookmark
        }
    }
}

/**
 * Recursive walks via `enumeratorAtURL` return absolute paths; derive
 * the "directory under the picked folder" portion the same way the
 * Android SAF walk does so the timeline merge surfaces matching
 * `relativePath` values across platforms.
 */
private fun relativeDirectory(basePath: String, filePath: String, fileName: String): String {
    if (basePath.isEmpty() || !filePath.startsWith(basePath)) return ""
    val rel = filePath.removePrefix(basePath)
    val trimmed = rel.removeSuffix(fileName).trimEnd('/')
    return trimmed
}

@OptIn(BetaInteropApi::class)
private fun decodeBase64(input: String): NSData? =
    NSData.create(base64EncodedString = input, options = 0uL)

private fun encodeBase64(data: NSData): String =
    data.base64EncodedStringWithOptions(0uL)

/** Same extension map as the desktop fallback; iOS has no cheap MIME lookup. */
private fun mediaTypeFromExtension(ext: String): DeviceMediaType? =
    when (ext.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp" -> DeviceMediaType.Image
        "mp4", "mov", "m4v", "mkv", "webm", "avi" -> DeviceMediaType.Video
        else -> null
    }

private fun mimeFromExtension(ext: String): String? = when (ext.lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    "bmp" -> "image/bmp"
    "mp4" -> "video/mp4"
    "mov" -> "video/quicktime"
    "m4v" -> "video/x-m4v"
    "mkv" -> "video/x-matroska"
    "webm" -> "video/webm"
    "avi" -> "video/x-msvideo"
    else -> null
}

/**
 * Walks the UIApplication scene/window graph for the controller a modal
 * picker should be presented from. `keyWindow` is deprecated since iOS
 * 13 but stays a useful fallback for app structures with a single
 * window — Compose Multiplatform's iOS host fits that shape.
 */
@Suppress("DEPRECATION")
private fun topmostViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val window: UIWindow? = app.keyWindow
        ?: app.windows.firstOrNull { (it as? UIWindow)?.keyWindow == true } as? UIWindow
    var top: UIViewController = window?.rootViewController ?: return null
    while (true) {
        val presented = top.presentedViewController ?: return top
        top = presented
    }
}

/**
 * Delegate target for `UIDocumentPickerViewController`. The picker
 * keeps only a weak reference to its delegate, so the instance holds
 * a self-reference for the duration of the modal — released the first
 * time the user either confirms a folder or cancels.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class FolderPickerDelegate(
    private val gallery: DeviceGallery,
    private val onPicked: (DeviceFolderRef?) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    private var selfRef: FolderPickerDelegate? = null

    fun retain() { selfRef = this }
    private fun release() { selfRef = null }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onPicked(null)
            release()
            return
        }
        val ref = bookmarkAndAdopt(gallery, url)
        onPicked(ref)
        release()
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
        release()
    }
}

/**
 * Convert the user-picked URL into a [DeviceFolderRef] whose `uri` is
 * a base64 bookmark we can re-open across launches. Must be called
 * while the picker still holds an implicit scope on [pickedUrl] — we
 * then transfer that scope into the gallery's `activeUrl` slot.
 */
@OptIn(ExperimentalForeignApi::class)
private fun bookmarkAndAdopt(gallery: DeviceGallery, pickedUrl: NSURL): DeviceFolderRef? {
    val started = pickedUrl.startAccessingSecurityScopedResource()
    try {
        val data = memScoped {
            val errVar = alloc<ObjCObjectVar<NSError?>>()
            pickedUrl.bookmarkDataWithOptions(
                options = 0uL,
                includingResourceValuesForKeys = null,
                relativeToURL = null,
                error = errVar.ptr
            )
        } ?: return null
        val bookmark = encodeBase64(data)
        // Hand the live scope to the gallery so subsequent listMedia /
        // read calls don't have to re-resolve and re-acquire.
        gallery.adoptActive(pickedUrl, bookmark)
        return DeviceFolderRef(
            uri = bookmark,
            displayName = pickedUrl.lastPathComponent ?: "Folder"
        )
    } finally {
        // `adoptActive` took its own scope; balance the one we opened
        // above. If we failed before reaching it, this still cleans up
        // the implicit picker scope.
        if (started) pickedUrl.stopAccessingSecurityScopedResource()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberDeviceFolderPicker(
    gallery: DeviceGallery,
    onPicked: (DeviceFolderRef?) -> Unit
): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    return remember(gallery) {
        {
            val top = topmostViewController()
            if (top == null) {
                currentOnPicked.value(null)
            } else {
                val delegate = FolderPickerDelegate(gallery) { ref ->
                    currentOnPicked.value(ref)
                }
                delegate.retain()
                val folderType = UTType.typeWithIdentifier("public.folder")
                val picker = if (folderType != null) {
                    UIDocumentPickerViewController(
                        forOpeningContentTypes = listOf(folderType)
                    )
                } else {
                    // Should never fire — "public.folder" is a built-in UTI —
                    // but fall back cleanly rather than crashing if the
                    // identifier resolver returns nil on some OS revision.
                    currentOnPicked.value(null)
                    return@remember
                }
                picker.delegate = delegate
                picker.modalPresentationStyle = UIModalPresentationFormSheet
                top.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}
