package com.photonne.app.data.devicebackup

import kotlinx.serialization.Serializable

/**
 * A user-picked folder on the local device. The `uri` is opaque and
 * platform-specific — on Android it's the persisted SAF tree URI, on
 * iOS it's a bookmark-resolved file URL, on desktop it's a regular
 * absolute path. Treat it as a black box outside the platform layer.
 *
 * `displayName` is the human-readable label we surface in the UI; it
 * may be the basename of the path or the SAF "DISPLAY_NAME" column.
 */
@Serializable
data class DeviceFolderRef(
    val uri: String,
    val displayName: String
)

enum class DeviceMediaType { Image, Video }

/**
 * One media file located underneath a [DeviceFolderRef]. `uri` is the
 * full opaque resource identifier the platform layer needs to read
 * the bytes back later; `sha256` is filled in lazily once we hash the
 * file to check it against the server.
 */
@Serializable
data class DeviceMedia(
    val uri: String,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateModifiedMillis: Long,
    val type: DeviceMediaType,
    val sha256: String? = null,
    /**
     * Original creation/capture timestamp where the platform exposes one
     * (iOS `PHAsset.creationDate`, desktop file birth time). Null on
     * Android — SAF only surfaces last-modified. Sent with the upload so
     * the server can preserve the device-side dates.
     */
    val dateCreatedMillis: Long? = null
)

/**
 * The sync verdict for a single [DeviceMedia] item against the
 * currently logged-in server account.
 *
 * - [Unknown] before we've computed a hash and asked the server.
 * - [NotSynced] hash computed; server doesn't have a matching asset.
 * - [Synced] hash matches an existing server asset.
 * - [Uploading] the upload queue is actively processing this file.
 * - [Failed] last upload attempt failed; surface the message in the UI.
 * - [Ignored] user chose to skip this file; it no longer counts as pending
 *   and is never re-queued until they un-skip it.
 */
sealed class DeviceMediaSyncState {
    data object Unknown : DeviceMediaSyncState()
    data object NotSynced : DeviceMediaSyncState()
    data class Synced(val assetId: String) : DeviceMediaSyncState()
    data object Uploading : DeviceMediaSyncState()
    /**
     * The last upload attempt for this item failed. [reason] is the typed
     * category the UI uses to pick a localized message; [detail] holds the
     * raw server/exception text for tooltips or debug.
     */
    data class Failed(
        val reason: UploadFailureReason,
        val detail: String? = null
    ) : DeviceMediaSyncState()
    data object Ignored : DeviceMediaSyncState()
}
