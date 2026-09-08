package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * Mismo motor vlcj que VideoPlayer, en modo "la foto cobra vida": silencioso y
 * sin controles. `finished` llega en un hilo nativo de libvlc, así que el aviso
 * de fin se reencola en el scope de composición antes de tocar estado Compose.
 */
@Composable
actual fun MotionPhotoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier,
    loop: Boolean,
    onPlaybackEnded: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val playbackEnded = rememberUpdatedState(onPlaybackEnded)
    val playback = remember(url, headers, loop) {
        DesktopVideoPlayback(
            url = authorizedMediaUrl(url, headers),
            autoPlay = true,
            muted = true,
            loop = loop,
            onFinished = {
                if (!loop) {
                    scope.launch { playbackEnded.value() }
                }
            }
        )
    }
    DisposableEffect(playback) {
        onDispose { playback.release() }
    }
    VideoSurface(playback, modifier)
}
