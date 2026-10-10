package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreGraphics.CGRectMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView

// Mirrors VideoPlayer.ios.kt: the header-fields key isn't exposed by the
// Kotlin/Native AVFoundation bindings, so fall back to the documented string.
private const val AVURLAssetHTTPHeaderFieldsKey = "AVURLAssetHTTPHeaderFieldsKey"

actual val videoSurfaceRendersOnTop: Boolean = true

@OptIn(ExperimentalForeignApi::class)
private class IosVideoPlayback(
    val player: AVPlayer,
    val playerLayer: AVPlayerLayer
) : VideoPlayback {
    override var isReady by mutableStateOf(false)
        private set
    override var isPlaying by mutableStateOf(false)
        private set
    override var positionMs by mutableStateOf(0L)
        private set
    override var durationMs by mutableStateOf(0L)
        private set

    /** Pull live time/state into snapshot state; called on a ticker. */
    fun sample() {
        val pos = CMTimeGetSeconds(player.currentTime())
        if (!pos.isNaN() && !pos.isInfinite()) {
            positionMs = (pos * 1000).toLong().coerceAtLeast(0L)
        }
        player.currentItem?.let { item ->
            val dur = CMTimeGetSeconds(item.duration)
            if (!dur.isNaN() && !dur.isInfinite() && dur > 0.0) durationMs = (dur * 1000).toLong()
            if (item.status == AVPlayerItemStatusReadyToPlay) isReady = true
        }
        isPlaying = player.timeControlStatus == AVPlayerTimeControlStatusPlaying
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun togglePlay() {
        if (player.timeControlStatus == AVPlayerTimeControlStatusPlaying) pause() else play()
    }

    override fun seekTo(ms: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(ms / 1000.0, preferredTimescale = 1000))
        positionMs = ms.coerceAtLeast(0L)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun configurePlaybackAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryPlayback, error = null)
    session.setActive(true, error = null)
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberVideoPlayback(
    url: String,
    headers: Map<String, String>,
    autoPlay: Boolean
): VideoPlayback {
    val nsUrl = remember(url) { NSURL.URLWithString(url) }
    val playback = remember(url, headers) {
        // Switch to the Playback category so audio is audible even with the
        // ring/silent switch on, matching a foreground video viewer.
        configurePlaybackAudioSession()
        val asset = if (nsUrl != null) {
            val options = if (headers.isNotEmpty()) {
                mapOf<Any?, Any?>(AVURLAssetHTTPHeaderFieldsKey to headers)
            } else null
            AVURLAsset(uRL = nsUrl, options = options)
        } else null
        val item = asset?.let { AVPlayerItem(asset = it) }
        val player = AVPlayer(playerItem = item)
        val layer = AVPlayerLayer.playerLayerWithPlayer(player)
        layer.setVideoGravity(AVLayerVideoGravityResizeAspect)
        IosVideoPlayback(player, layer)
    }

    DisposableEffect(playback) {
        if (autoPlay) playback.play()
        onDispose {
            playback.pause()
            playback.player.replaceCurrentItemWithPlayerItem(null)
        }
    }

    // Poll the playhead a few times a second to advance the scrubber/time.
    LaunchedEffect(playback) {
        while (true) {
            playback.sample()
            delay(250)
        }
    }

    // Pause when backgrounded so the clip doesn't keep playing audio off-screen.
    DisposableEffect(playback) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null
        ) { _ -> playback.pause() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }

    return playback
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoSurface(
    playback: VideoPlayback,
    modifier: Modifier,
    fillCrop: Boolean
) {
    val ios = playback as IosVideoPlayback
    // Zoom-fill in the 1:1 info header, aspect-fit otherwise; updating the live
    // layer (no remount) keeps the clip playing.
    LaunchedEffect(ios, fillCrop) {
        ios.playerLayer.setVideoGravity(
            if (fillCrop) AVLayerVideoGravityResizeAspectFill else AVLayerVideoGravityResizeAspect
        )
    }
    // The surface must render on top (default interop compositing): AVPlayerLayer
    // is a hardware layer and isn't captured if CMP rasterises the view offscreen
    // (interactionMode = null), which would show blank. Because it draws over all
    // Compose content, the caller insets it so it never covers the chrome — see
    // videoSurfaceRendersOnTop. A clear background keeps the letterbox showing the
    // black page behind instead of an opaque interop fill.
    UIKitView(
        modifier = modifier,
        factory = { PlayerSurfaceView(ios.playerLayer) }
    )
}

/**
 * A bare UIView that hosts an [AVPlayerLayer] and keeps it pinned to its bounds
 * on every layout pass — so it resizes correctly even when CMP hands it its
 * final bounds late. Touch is disabled so taps and horizontal swipes fall
 * through to the Compose viewer (chrome toggle and pager) instead of the layer
 * swallowing them.
 */
@OptIn(ExperimentalForeignApi::class)
private class PlayerSurfaceView(
    private val playerLayer: AVPlayerLayer
) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {

    init {
        setUserInteractionEnabled(false)
        // Opaque BLACK, not clear: a surface rendered on top of Compose punches a
        // transparent hole in the Compose canvas, so a clear background would show
        // the white root window through the letterbox, not the black page. An
        // opaque black fill makes the letterbox black, like a normal video player.
        setBackgroundColor(UIColor.blackColor)
        playerLayer.setBackgroundColor(UIColor.blackColor.CGColor)
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        // Disable the implicit CALayer animation so the layer snaps to the new
        // bounds instead of sliding/scaling into them on a bounds change.
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer.setFrame(bounds)
        CATransaction.commit()
    }
}
