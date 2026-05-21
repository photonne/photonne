package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.pause
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

// AVURLAssetHTTPHeaderFieldsKey is not exposed by Kotlin/Native's
// AVFoundation cinterop bindings (unlike sibling keys such as
// AVURLAssetHTTPCookiesKey); fall back to the documented string value.
private const val AVURLAssetHTTPHeaderFieldsKey = "AVURLAssetHTTPHeaderFieldsKey"

actual val isVideoPlaybackSupported: Boolean = true

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier
) {
    val nsUrl = remember(url) { NSURL.URLWithString(url) }
    val player = remember(url, headers) {
        // Default category (soloAmbient) honours the ring/silent switch, so
        // the video plays muted when the phone is silenced. Switch to
        // playback so audio is always audible while a video is on screen.
        configurePlaybackAudioSession()

        val asset = if (nsUrl != null) {
            val options = if (headers.isNotEmpty()) {
                mapOf<Any?, Any?>(AVURLAssetHTTPHeaderFieldsKey to headers)
            } else null
            AVURLAsset(uRL = nsUrl, options = options)
        } else null
        val item = asset?.let { AVPlayerItem(asset = it) }
        AVPlayer(playerItem = item)
    }

    val container = remember(player) {
        val playerVC = AVPlayerViewController().apply {
            this.player = player
            this.showsPlaybackControls = true
        }
        VideoPlayerContainerViewController(playerVC)
    }

    DisposableEffect(player) {
        onDispose {
            player.pause()
            container.playerViewController.player = null
        }
    }

    UIKitViewController(
        modifier = modifier,
        factory = { container }
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun configurePlaybackAudioSession() {
    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryPlayback, error = null)
    session.setActive(true, error = null)
}

// AVPlayerViewController hosted directly through UIKitViewController doesn't
// always relay layout changes from its parent (e.g. when the asset pager
// swaps pages or the device rotates), leaving the video frame stuck at the
// initial size and visibly offset. Wrapping it in a parent controller that
// pins the child view to its own bounds on every layout pass keeps the
// playback surface aligned with the Compose slot.
@OptIn(ExperimentalForeignApi::class)
private class VideoPlayerContainerViewController(
    val playerViewController: AVPlayerViewController
) : UIViewController(nibName = null, bundle = null) {

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(playerViewController)
        view.addSubview(playerViewController.view)
        playerViewController.didMoveToParentViewController(this)
    }

    override fun viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        playerViewController.view.setFrame(view.bounds)
    }
}
