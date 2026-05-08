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
@Composable
expect fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier
)

/**
 * Whether the current platform can actually play [VideoPlayer].
 * Used by the AssetDetail screen to decide if it shows the player or
 * a fallback poster + message.
 */
expect val isVideoPlaybackSupported: Boolean
