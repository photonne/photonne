package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

@Composable
actual fun MotionPhotoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier,
    loop: Boolean,
    onPlaybackEnded: () -> Unit
) {
    val context = LocalContext.current
    // Keep the end callback fresh without rebuilding the player/listener.
    val currentOnEnded by rememberUpdatedState(onPlaybackEnded)
    // Shared client (same pool + timeouts as the API); see VideoPlayer.android.kt.
    val httpClient = koinInject<OkHttpClient>()
    val player = remember(url, headers, loop) {
        val httpFactory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(headers)
        val baseFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(baseFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                volume = 0f
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player, loop) {
        // Play-once: signal the caller when the clip reaches its end so it can
        // unmount us and reveal the still again. Looping playback never ends.
        val listener = if (!loop) {
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) currentOnEnded()
                }
            }.also { player.addListener(it) }
        } else null
        onDispose {
            listener?.let { player.removeListener(it) }
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
            }
        }
    )
}
