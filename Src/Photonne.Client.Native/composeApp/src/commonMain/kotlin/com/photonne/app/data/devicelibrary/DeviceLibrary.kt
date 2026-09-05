package com.photonne.app.data.devicelibrary

import androidx.compose.runtime.Composable
import com.photonne.app.data.devicebackup.DeviceFolderRef
import com.photonne.app.data.devicebackup.DeviceMedia
import kotlinx.coroutines.flow.Flow

/**
 * Scheme of the backup-folder refs that denote a MediaStore bucket
 * (Camera, WhatsApp Images…) instead of a SAF tree grant:
 * `mediastore:bucket:<bucketId>`. Android's DeviceGallery resolves
 * both kinds — legacy SAF folders keep backing up untouched.
 */
const val DEVICE_BUCKET_URI_PREFIX = "mediastore:bucket:"

/**
 * One system-index folder ("bucket") of the device library, as offered
 * by the backup-source picker. Android-only in practice: iOS models
 * the whole Camera Roll as a single virtual folder and desktop has no
 * device library, so both list no buckets.
 */
data class DeviceBucket(
    val id: String,
    val displayName: String,
    val itemCount: Int,
    /** Item URI of the bucket's newest asset, as a row-thumbnail model
     *  for folder-style listings; null where tracking it isn't cheap. */
    val latestUri: String? = null
) {
    /** The backup-folder ref this bucket maps to. */
    fun toFolderRef(): DeviceFolderRef =
        DeviceFolderRef(uri = "$DEVICE_BUCKET_URI_PREFIX$id", displayName = displayName)
}

/**
 * Access level to the device's media library. Android can't reliably
 * distinguish "never asked" from "denied" without an Activity, so it
 * reports [NotDetermined] for both and [DeviceLibraryStore] tracks
 * whether the prompt was already shown; iOS maps its native
 * authorization statuses one-to-one.
 */
enum class DeviceLibraryAccess {
    /** Platform has no media library concept (desktop). */
    Unsupported,
    NotDetermined,
    Denied,
    /** Limited/partial grant: only the user-selected subset is visible. */
    Partial,
    Full;

    val canRead: Boolean get() = this == Partial || this == Full
}

/**
 * Platform gateway to the WHOLE device media library, as the system
 * indexes it — the primary, instant source of the timeline's local
 * side. Unlike [com.photonne.app.data.devicebackup.DeviceGallery]
 * (user-picked SAF folders, per-file IPC, byte access for uploads),
 * this reads the platform's own media index in bulk and never touches
 * file contents:
 *
 * - **Android** queries MediaStore's Images and Video collections with
 *   a plain projection — one cursor per collection, no per-file Binder
 *   round-trips — and observes the store via `ContentObserver`.
 * - **iOS** enumerates `PHAsset.fetchAssets` reading only cheap asset
 *   properties (never `PHAssetResource`, which is a per-asset Photos-DB
 *   query) and observes via `PHPhotoLibraryChangeObserver`.
 * - **Desktop** reports [DeviceLibraryAccess.Unsupported].
 *
 * Byte-level work (hashing, uploads) stays on [DeviceGallery]; the
 * URIs both emit are compatible (`content://media/...` resolves
 * through MediaStore, `photokit:<localIdentifier>` through PhotoKit).
 */
expect class DeviceLibrary {
    val isSupported: Boolean

    /**
     * Whether the platform indexes media into folders the user can scope
     * the timeline (and the backup picker) by. Only Android: iOS models
     * the library as one Camera Roll and desktop has no library, so a
     * [DeviceLibraryScope] is meaningless there and its UI stays hidden.
     */
    val supportsBuckets: Boolean

    /** Current access level; cheap enough to call on every screen entry. */
    fun accessState(): DeviceLibraryAccess

    /**
     * Enumerates the image/video slice of the system index that [scope]
     * selects (everything, on platforms without buckets), newest first
     * by capture date. Metadata only — no hashing, no byte reads, no
     * network. Returns empty when access isn't granted.
     */
    suspend fun loadAll(scope: DeviceLibraryScope): List<DeviceMedia>

    /**
     * Emits whenever the platform media index changes (new photo taken,
     * file deleted, edit saved). Bursty — collectors coalesce before
     * reloading.
     */
    fun changes(): Flow<Unit>

    /**
     * The library's folders as the system indexes them, largest first —
     * the backup-source picker's data. Empty when access isn't granted
     * or the platform has no bucket concept (iOS, desktop).
     */
    suspend fun listBuckets(): List<DeviceBucket>
}

/**
 * Composable wrapper around the platform's media-permission prompt.
 * Returns a lambda to call from a click handler (or an auto-prompt
 * effect); [onResult] receives the post-prompt access level. On a
 * platform without a prompt it reports the current state immediately.
 */
@Composable
expect fun rememberDeviceLibraryAccessRequester(
    onResult: (DeviceLibraryAccess) -> Unit
): () -> Unit
