package com.photonne.app.data.devicelibrary

import androidx.compose.runtime.Composable

/**
 * Composable wrapper around the platform's "move device media to the
 * system trash" flow. Returns a lambda taking device-library URIs;
 * [onResult] reports whether the items were actually trashed.
 *
 * The CONFIRMATION belongs to the platform, not the app — both flows
 * surface the OS's own dialog, so callers must not stack another one:
 *
 * - **Android** (11+) launches [android.provider.MediaStore.createTrashRequest]
 *   through the activity-result IntentSender dance; items land in
 *   MediaStore's 30-day trash. Pre-11 has neither system trash nor a
 *   consent dialog, so the request is refused (false) — a silent
 *   permanent delete on a single tap is worse than a dead button.
 * - **iOS** runs `PHAssetChangeRequest.deleteAssets`, which moves the
 *   assets to Photos' "Recently Deleted" (30 days) behind the system
 *   confirmation sheet.
 * - **Desktop** always reports false (no device library).
 *
 * The library's change observer refreshes the timeline afterwards on
 * its own; callers may also force an immediate refresh for snappier UI.
 */
@Composable
expect fun rememberDeviceMediaTrasher(
    onResult: (trashed: Boolean) -> Unit
): (uris: List<String>) -> Unit
