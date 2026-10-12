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
 *   (`<tmp>/photonne-share/`) and hand back the file path. iOS has no shared
 *   "Downloads" folder, so for the standalone *download* action the user picks
 *   the destination through the share sheet ("Save to Files", "Save Image"),
 *   which the same temp file feeds.
 * - [shareFiles] presents a `UIActivityViewController` over the current top
 *   view controller with the saved files as `NSURL`s, so the OS offers
 *   WhatsApp / Messages / Mail / Save to Photos / etc.
 */
@OptIn(ExperimentalForeignApi::class)
actual class AssetSharing {

    actual suspend fun openDownload(
        fileName: String,
        mimeType: String
    ): DownloadSink = withContext(Dispatchers.Default) {
        ensureShareDir()
        val safeName = safeFileName(fileName)
        FileDownloadSink(
            target = Path(shareDirPath(), safeName),
            displayName = safeName,
            mimeType = mimeType
        )
    }

    /** Same private temp directory the share sheet already fed from: iOS
     *  never writes to Photos unless the user picks "Save Image" there. */
    actual suspend fun openShareStage(
        fileName: String,
        mimeType: String
    ): DownloadSink = openDownload(fileName, mimeType)

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
        // The share sheet must be presented on the main thread, on top of
        // whatever Compose view controller is currently showing.
        withContext(Dispatchers.Main) {
            val host = topViewController()
                ?: throw AssetSharingUnavailable("No active window to present the share sheet")
            val urls = files.map { NSURL.fileURLWithPath(it.path) }
            val activity = UIActivityViewController(
                activityItems = urls,
                applicationActivities = null
            )
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

    actual suspend fun shareText(text: String, subject: String?) {
        if (text.isBlank()) return
        withContext(Dispatchers.Main) {
            val host = topViewController()
                ?: throw AssetSharingUnavailable("No active window to present the share sheet")
            val activity = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null
            )
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

    private fun shareDirPath(): String = NSTemporaryDirectory() + "photonne-share"

    private fun ensureShareDir() {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = shareDirPath(),
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

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
