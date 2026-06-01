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
 */
@Composable
expect fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier,
    onControlsVisibilityChanged: (Boolean) -> Unit = {}
)

/**
 * Whether the current platform can actually play [VideoPlayer].
 * Used by the AssetDetail screen to decide if it shows the player or
 * a fallback poster + message.
 */
expect val isVideoPlaybackSupported: Boolean
