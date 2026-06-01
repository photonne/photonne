package com.photonne.app.ui.asset

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient

actual val isVideoPlaybackSupported: Boolean = true

@Composable
actual fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier,
    onControlsVisibilityChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    // Latest callback without re-creating the AndroidView when the lambda
    // identity changes between recompositions.
    val visibilityCallback = rememberUpdatedState(onControlsVisibilityChanged)
    val player = remember(url, headers) {
        val httpFactory = OkHttpDataSource.Factory(OkHttpClient())
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

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                // Aspect-fit so a portrait clip is letterboxed inside the page
                // instead of cropping or overflowing under the chrome bars.
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
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
