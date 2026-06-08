package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Desktop has no video engine wired up yet (see VideoPlayer.desktop.kt), so a
// Live Photo simply stays a still here — the caller keeps showing the image.
@Composable
actual fun MotionPhotoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier,
    loop: Boolean,
    onPlaybackEnded: () -> Unit
) {
    // No engine here; the Live Photo branch is gated on isVideoPlaybackSupported
    // (false on desktop) so this is never reached in practice. Params accepted
    // only to satisfy the expect signature.
}
