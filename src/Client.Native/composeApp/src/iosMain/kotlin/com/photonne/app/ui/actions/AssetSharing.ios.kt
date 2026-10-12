package com.photonne.app.ui.actions

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * iOS bridge for the share/download glue.
 *
 * - [openDownload] streams into a private temp directory
 *   (`<tmp>/photonne-download/`). iOS has no shared "Downloads" folder, so
 *   once the file is whole the share sheet opens on it and the user picks the
 *   destination there ("Save to Files", "Save Image"). The temp file is
 *   deleted when the sheet closes.
 * - [openShareStage] streams into `<tmp>/photonne-share/` and [shareFiles]
 *   presents a `UIActivityViewController` over the current top view
 *   controller with the staged files as `NSURL`s, so the OS offers
 *   WhatsApp / Messages / Mail / Save to Photos / etc. They are deleted when
 *   the sheet closes too, and [clearShareCache] sweeps whatever is left.
 */
@OptIn(ExperimentalForeignApi::class)
actual class AssetSharing {

    /**
     * Downloading used to stop at the temp file: the snackbar said
     * "Descargado" but nothing outside the app could reach it. Committing now
     * hands the file to the sheet, where the user saves it.
     */
    actual suspend fun openDownload(
        fileName: String,
        mimeType: String
    ): DownloadSink = withContext(Dispatchers.Default) {
        val file = openTempFile(downloadDirPath(), fileName, mimeType)
        object : DownloadSink by file {
            override suspend fun commit(): SavedAssetFile {
                val saved = file.commit()
                try {
                    presentSheet(listOf(NSURL.fileURLWithPath(saved.path))) {
                        removeFile(saved.path)
                    }
                } catch (failure: Throwable) {
                    removeFile(saved.path)
                    throw failure
                }
                return saved
            }
        }
    }

    /** Private temp directory: iOS never writes to Photos unless the user
     *  picks "Save Image" in the sheet. */
    actual suspend fun openShareStage(
        fileName: String,
        mimeType: String
    ): DownloadSink = withContext(Dispatchers.Default) {
        openTempFile(shareDirPath(), fileName, mimeType)
    }

    actual suspend fun clearShareCache() = withContext(Dispatchers.Default) {
        val manager = NSFileManager.defaultManager
        val dir = shareDirPath()
        manager.contentsOfDirectoryAtPath(dir, error = null)?.forEach { name ->
            manager.removeItemAtPath("$dir/$name", error = null)
        }
        Unit
    }

    actual suspend fun shareFiles(files: List<SavedAssetFile>, mimeType: String) {
        if (files.isEmpty()) return
        // The receiving app has its copy once the sheet closes: the staged
        // files go then instead of waiting for the next share.
        presentSheet(files.map { NSURL.fileURLWithPath(it.path) }) {
            files.forEach { removeFile(it.path) }
        }
    }

    actual suspend fun shareText(text: String, subject: String?) {
        if (text.isBlank()) return
        presentSheet(listOf(text), onClosed = null)
    }

    /**
     * Presents the share sheet with [items]; [onClosed] runs when the user
     * finishes or dismisses it.
     */
    private suspend fun presentSheet(items: List<Any>, onClosed: (() -> Unit)?) {
        // The share sheet must be presented on the main thread, on top of
        // whatever Compose view controller is currently showing.
        withContext(Dispatchers.Main) {
            val host = topViewController()
                ?: throw AssetSharingUnavailable("No active window to present the share sheet")
            val activity = UIActivityViewController(
                activityItems = items,
                applicationActivities = null
            )
            if (onClosed != null) {
                activity.completionWithItemsHandler = { _, _, _, _ -> onClosed() }
            }
            // iPad presents this as a popover and crashes without an anchor;
            // anchor it to the centre of the host view.
            activity.popoverPresentationController?.let { popover ->
                popover.sourceView = host.view
                host.view.bounds.useContents {
                    popover.sourceRect = CGRectMake(
                        x = size.width / 2.0,
                        y = size.height / 2.0,
                        width = 0.0,
                        height = 0.0
                    )
                }
            }
            host.presentViewController(activity, animated = true, completion = null)
        }
    }

    private fun openTempFile(dir: String, fileName: String, mimeType: String): FileDownloadSink {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        val safeName = safeFileName(fileName)
        return FileDownloadSink(target = Path(dir, safeName), displayName = safeName, mimeType = mimeType)
    }

    private fun removeFile(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    private fun downloadDirPath(): String = NSTemporaryDirectory() + "photonne-download"

    private fun shareDirPath(): String = NSTemporaryDirectory() + "photonne-share"

    // Keep the suggested name (apps show it) but neutralise path separators.
    private fun safeFileName(fileName: String): String =
        fileName.replace('/', '_').ifBlank { "shared" }
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
