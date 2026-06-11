package com.photonne.app.ui.actions

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * iOS bridge for the share/download glue.
 *
 * - [saveAsset] / [saveZip] write the bytes into a private temp directory
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

    actual suspend fun saveAsset(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile = withContext(Dispatchers.Default) {
        writeToShareDir(bytes = bytes, fileName = fileName, mimeType = mimeType)
    }

    actual suspend fun saveZip(
        bytes: ByteArray,
        fileName: String
    ): SavedAssetFile = withContext(Dispatchers.Default) {
        writeToShareDir(bytes = bytes, fileName = fileName, mimeType = "application/zip")
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

    private fun writeToShareDir(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): SavedAssetFile {
        val dir = NSTemporaryDirectory() + "photonne-share"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = dir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
        // Keep the suggested name (apps show it) but neutralise path separators.
        val safeName = fileName.replace('/', '_').ifBlank { "shared" }
        val path = "$dir/$safeName"
        val data = bytes.toNSData()
        if (!data.writeToFile(path, atomically = true)) {
            throw AssetSharingUnavailable("Could not stage $safeName for sharing")
        }
        return SavedAssetFile(path = path, displayName = safeName, mimeType = mimeType)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }
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
