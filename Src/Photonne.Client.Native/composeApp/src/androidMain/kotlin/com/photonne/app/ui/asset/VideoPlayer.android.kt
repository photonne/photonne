package com.photonne.app.ui.asset

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import okhttp3.OkHttpClient
import org.koin.compose.koinInject

actual val isVideoPlaybackSupported: Boolean = true

// Android's PlayerView lays out correctly inside the pager, so keep it in-page.
actual val hostVideoOutsidePager: Boolean = false

@Composable
actual fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    fillCrop: Boolean,
    controlsEnabled: Boolean
) {
    val context = LocalContext.current
    // Latest callback without re-creating the AndroidView when the lambda
    // identity changes between recompositions.
    val visibilityCallback = rememberUpdatedState(onControlsVisibilityChanged)
    // Shared client: it rides the same connection pool the API uses, so it gets
    // evicted on network change and ages out idle sockets, and it carries finite
    // read/connect timeouts so a dead socket fails instead of hanging the clip.
    val httpClient = koinInject<OkHttpClient>()
    val player = remember(url, headers) {
        val httpFactory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(headers)
        val baseFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(baseFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = false
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Pause as soon as the app leaves the foreground (home button / app
    // switcher / another app on top) so the clip doesn't keep playing audio
    // off-screen. ON_PAUSE fires before ON_STOP, so the sound stops the moment
    // the viewer is no longer in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player.playWhenReady = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        update = { playerView ->
            // Zoom-to-fill in the 1:1 info header, aspect-fit otherwise.
            playerView.resizeMode = if (fillCrop) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            // Info mode: hide the transport controls and play automatically
            // (ambient playback). Leave the play state untouched otherwise.
            playerView.useController = controlsEnabled
            if (!controlsEnabled) {
                playerView.hideController()
                player.playWhenReady = true
            }
        },
        factory = { ctx ->
            PlayerView(ctx).apply {
                val view = this
                this.player = player
                useController = true
                // Aspect-fit so a portrait clip is letterboxed inside the page
                // instead of cropping or overflowing under the chrome bars.
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                // Hold the screen awake only while the clip is actually playing
                // so the device's inactivity timeout doesn't dim or lock the
                // display mid-video; released automatically on pause/end.
                keepScreenOn = player.isPlaying
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        view.keepScreenOn = isPlaying
                    }
                })
                // The native controller toggles on tap; mirror that into the
                // host so its chrome appears/disappears together with the
                // transport controls. Also hide the settings (speed/audio) gear:
                // in landscape it slid under the system bars and couldn't be
                // tapped, and we don't expose those options anyway.
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        if (visibility == View.VISIBLE) hideSettingsButton()
                        visibilityCallback.value(visibility == View.VISIBLE)
                    }
                )
                hideSettingsButton()
            }
        }
    )
}

/** Hides ExoPlayer's settings (gear) button from the default control view. */
private fun PlayerView.hideSettingsButton() {
    findViewById<View?>(androidx.media3.ui.R.id.exo_settings)?.visibility = View.GONE
}
