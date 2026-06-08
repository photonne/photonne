package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plays the short motion clip paired with a Live Photo. Unlike [VideoPlayer]
 * this is a silent, controls-less surface — the still's "come alive" effect,
 * not a media player.
 *
 * Two trigger styles share this one surface:
 *  - press-and-hold (the still comes alive while held): [loop] = true, the
 *    caller mounts it only while held and unmounts on release;
 *  - tap-to-play-once ("Ver foto en movimiento"): [loop] = false, the caller
 *    mounts it on tap and unmounts when [onPlaybackEnded] fires so the view
 *    reverts to the still — playing the clip through exactly once like a short
 *    video.
 *
 * Each platform actual autoplays muted; when [loop] it restarts on reaching the
 * end, otherwise it fires [onPlaybackEnded] once. It releases its player in the
 * dispose effect.
 *
 * Reuses the same per-platform engines as [VideoPlayer] (ExoPlayer on
 * Android, AVPlayer on iOS); desktop falls back to nothing, matching
 * [isVideoPlaybackSupported].
 */
@Composable
expect fun MotionPhotoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    onPlaybackEnded: () -> Unit = {}
)
