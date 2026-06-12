package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

actual val videoSurfaceRendersOnTop: Boolean = false

private class AndroidVideoPlayback(val player: ExoPlayer) : VideoPlayback {
    override var isReady by mutableStateOf(false)
        private set
    override var isPlaying by mutableStateOf(false)
        private set
    override var positionMs by mutableStateOf(0L)
        private set
    override var durationMs by mutableStateOf(0L)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                isReady = true
                // duration is C.TIME_UNSET until the media is read.
                player.duration.takeIf { it > 0 }?.let { durationMs = it }
            }
        }
    }

    init {
        player.addListener(listener)
    }

    /** Pull the live position/duration into snapshot state; called on a ticker. */
    fun sample() {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        if (durationMs <= 0L) player.duration.takeIf { it > 0 }?.let { durationMs = it }
    }

    override fun play() {
        player.playWhenReady = true
    }

    override fun pause() {
        player.playWhenReady = false
    }

    override fun togglePlay() {
        if (player.isPlaying) pause() else play()
    }

    override fun seekTo(ms: Long) {
        val target = if (durationMs > 0) ms.coerceIn(0L, durationMs) else ms.coerceAtLeast(0L)
        player.seekTo(target)
        positionMs = target
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }
}

@Composable
actual fun rememberVideoPlayback(
    url: String,
    headers: Map<String, String>,
    autoPlay: Boolean
): VideoPlayback {
    val context = LocalContext.current
    // Shared client: rides the API's connection pool, so it ages out idle
    // sockets and carries finite timeouts (a dead socket fails, never hangs).
    val httpClient = koinInject<OkHttpClient>()
    val playback = remember(url, headers) {
        val httpFactory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(headers)
        val baseFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(baseFactory)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = autoPlay
            }
        AndroidVideoPlayback(player)
    }

    DisposableEffect(playback) {
        onDispose { playback.release() }
    }

    // Poll the playhead a few times a second to advance the scrubber/time.
    LaunchedEffect(playback) {
        while (true) {
            playback.sample()
            delay(250)
        }
    }

    // Pause the instant the app leaves the foreground so audio doesn't play on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playback) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) playback.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return playback
}

@Composable
actual fun VideoSurface(
    playback: VideoPlayback,
    modifier: Modifier,
    fillCrop: Boolean
) {
    val player = (playback as AndroidVideoPlayback).player
    AndroidView(
        modifier = modifier,
        update = { view ->
            view.resizeMode = if (fillCrop) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                // No transport chrome — the viewer paints its own controls.
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                keepScreenOn = true
                // Let taps and horizontal swipes reach the Compose viewer (chrome
                // toggle and pager) instead of being swallowed by the surface.
                isClickable = false
                isFocusable = false
            }
        }
    )
}
