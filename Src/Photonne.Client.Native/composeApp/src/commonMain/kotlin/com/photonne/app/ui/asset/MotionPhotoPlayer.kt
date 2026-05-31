package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Plays the short motion clip paired with a Live Photo while the user
 * presses and holds the still. Unlike [VideoPlayer] this is a silent,
 * controls-less, auto-looping surface — the still's "come alive" effect,
 * not a media player.
 *
 * The caller mounts it only while held and unmounts on release, so each
 * platform actual just needs to autoplay muted and loop for as long as it
 * stays in composition; it releases its player in the dispose effect.
 *
 * Reuses the same per-platform engines as [VideoPlayer] (ExoPlayer on
 * Android, AVPlayer on iOS); desktop falls back to nothing, matching
 * [isVideoPlaybackSupported].
 */
@Composable
expect fun MotionPhotoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier
)
