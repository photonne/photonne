package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Native video playback for the AssetDetail viewer. Each platform
 * supplies its own actual:
 *
 * * Android: Media3 ExoPlayer + PlayerView via AndroidView, with an
 *   OkHttp data source carrying the Authorization header.
 * * iOS: AVPlayer + AVPlayerViewController via UIKitViewController,
 *   with HTTP headers attached to the AVURLAsset options.
 * * Desktop: graceful "no soportado" placeholder while we evaluate
 *   the right cross-platform engine (VLCJ vs JavaFX).
 *
 * The composable owns the player lifecycle: start paused, release in
 * the dispose effect when the page leaves composition.
 */
/**
 * @param onControlsVisibilityChanged fires when the player's transport controls
 * show or hide (driven by a tap on the video). The detail screen syncs its own
 * chrome (top/bottom bars) to this so a single tap toggles everything at once,
 * matching the iOS Photos / Google Photos viewer. The player letterboxes the
 * video (aspect-fit) so a portrait clip never overflows behind the chrome.
 * @param fillCrop when true the video fills its box by cropping (zoom-to-fill)
 * instead of letterboxing — used for the 1:1 info header so the clip sits
 * snugly in the square, matching the photo crop.
 * @param controlsEnabled when false the transport controls are hidden and the
 * clip plays automatically (ambient playback for the 1:1 info header); they
 * reappear when it becomes true again (on leaving info mode).
 */
@Composable
expect fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier,
    onControlsVisibilityChanged: (Boolean) -> Unit = {},
    fillCrop: Boolean = false,
    controlsEnabled: Boolean = true
)

/**
 * Whether the current platform can actually play [VideoPlayer].
 * Used by the AssetDetail screen to decide if it shows the player or
 * a fallback poster + message.
 */
expect val isVideoPlaybackSupported: Boolean

/**
 * Whether [VideoPlayer] must be hosted as a full-screen overlay ABOVE the asset
 * pager rather than inside a pager page.
 *
 * iOS = true: Compose Multiplatform's UIKit interop mis-positions a native view
 * embedded in a scrolling/paging container — the AVPlayerViewController mounts
 * oversized with its controls off-screen and only re-syncs on a touch. Hosting
 * it at a fixed rect outside the pager makes it lay out correctly from the first
 * frame. The pager page renders the still poster in its place; the overlay mounts
 * only while everything is at rest (no paging/dragging/info/transition) so motion
 * still animates the poster cleanly.
 *
 * Android/Desktop = false: the in-page player lays out correctly, so it stays
 * inside the pager (where swipe-to-page and gestures compose naturally).
 */
expect val hostVideoOutsidePager: Boolean
