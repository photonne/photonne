package com.photonne.app.ui.asset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Box

// Desktop has no native player yet (see VideoPlayer.desktop.kt). The controller
// is an inert stub so common code compiles; isVideoPlaybackSupported gates it
// off, so these are never reached in practice.
actual val videoSurfaceRendersOnTop: Boolean = false

private object NoopVideoPlayback : VideoPlayback {
    override val isReady = false
    override val isPlaying = false
    override val positionMs = 0L
    override val durationMs = 0L
    override fun play() {}
    override fun pause() {}
    override fun togglePlay() {}
    override fun seekTo(ms: Long) {}
}

@Composable
actual fun rememberVideoPlayback(
    url: String,
    headers: Map<String, String>,
    autoPlay: Boolean
): VideoPlayback = NoopVideoPlayback

@Composable
actual fun VideoSurface(
    playback: VideoPlayback,
    modifier: Modifier,
    fillCrop: Boolean
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(
            text = "Reproducción de vídeo no disponible en Desktop",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
