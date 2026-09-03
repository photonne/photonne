package com.photonne.app.data.devicelibrary

import androidx.compose.runtime.Composable
import com.photonne.app.data.devicebackup.DeviceMedia
import kotlinx.coroutines.flow.Flow

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

    /** Current access level; cheap enough to call on every screen entry. */
    fun accessState(): DeviceLibraryAccess

    /**
     * Enumerates every image/video the system index exposes, newest
     * first by capture date. Metadata only — no hashing, no byte reads,
     * no network. Returns empty when access isn't granted.
     */
    suspend fun loadAll(): List<DeviceMedia>

    /**
     * Emits whenever the platform media index changes (new photo taken,
     * file deleted, edit saved). Bursty — collectors coalesce before
     * reloading.
     */
    fun changes(): Flow<Unit>
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
