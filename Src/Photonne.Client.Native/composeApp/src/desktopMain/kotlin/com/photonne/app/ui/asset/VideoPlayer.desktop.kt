package com.photonne.app.ui.asset

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Presente solo si NativeDiscovery encuentra un VLC instalado; el código común
// gatea con esto y muestra el fallback (poster + ayuda de instalación) si falta.
actual val isVideoPlaybackSupported: Boolean
    get() = VlcEngine.available

/**
 * Reproductor autocontenido para el preview de la carpeta de backup: superficie
 * vlcj + controles Compose propios (play/pausa central, seek inferior). El visor
 * principal no pasa por aquí — usa rememberVideoPlayback + VideoSurface con los
 * controles comunes del AssetDetail.
 */
@Composable
actual fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    fillCrop: Boolean,
    controlsEnabled: Boolean
) {
    val playback = rememberVideoPlayback(url, headers, autoPlay = !controlsEnabled)
    var controlsVisible by remember { mutableStateOf(controlsEnabled) }
    val visibilityCallback = rememberUpdatedState(onControlsVisibilityChanged)

    // Modo ambient (cabecera de info 1:1): sin controles y reproduciendo solo.
    LaunchedEffect(controlsEnabled) {
        if (!controlsEnabled) {
            controlsVisible = false
            playback.play()
        }
    }

    LaunchedEffect(controlsVisible, controlsEnabled) {
        visibilityCallback.value(controlsVisible && controlsEnabled)
    }

    // Autoocultado a los 3 s mientras reproduce, como los reproductores nativos.
    LaunchedEffect(controlsVisible, playback.isPlaying) {
        if (controlsVisible && playback.isPlaying) {
            delay(3_000)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(controlsEnabled) {
                if (controlsEnabled) {
                    detectTapGestures { controlsVisible = !controlsVisible }
                }
            }
    ) {
        VideoSurface(playback, Modifier.fillMaxSize(), fillCrop)

        if (controlsEnabled) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                IconButton(
                    onClick = { playback.togglePlay() },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = formatClipTime(playback.positionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = if (playback.durationMs > 0) {
                            playback.positionMs.toFloat() / playback.durationMs
                        } else {
                            0f
                        },
                        onValueChange = { fraction ->
                            if (playback.durationMs > 0) {
                                playback.seekTo((fraction * playback.durationMs).toLong())
                            }
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Text(
                        text = formatClipTime(playback.durationMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun formatClipTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
