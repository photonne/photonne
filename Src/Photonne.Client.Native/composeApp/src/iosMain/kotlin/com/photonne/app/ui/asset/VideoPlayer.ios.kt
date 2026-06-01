package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.pause
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.darwin.NSObject

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
    modifier: Modifier,
    onControlsVisibilityChanged: (Boolean) -> Unit
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

    // AVPlayerViewController owns its own tap-to-toggle for the transport
    // controls; we can't observe that, so we attach a non-cancelling,
    // simultaneous tap recognizer and track an assumed visibility to mirror
    // it into the host chrome. Starts visible, matching the controller.
    val callback = rememberUpdatedState(onControlsVisibilityChanged)
    val tapHandler = remember { VideoTapHandler { visible -> callback.value(visible) } }

    val container = remember(player) {
        val playerVC = AVPlayerViewController().apply {
            this.player = player
            this.showsPlaybackControls = true
        }
        VideoPlayerContainerViewController(playerVC, tapHandler)
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

/**
 * NSObject target for the tap recognizer. Holds the assumed controls-visible
 * state, flips it on each tap and reports the new value. Also the recognizer's
 * delegate so it fires alongside AVPlayerViewController's own gestures.
 */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class VideoTapHandler(
    private val onVisibility: (Boolean) -> Unit
) : NSObject(), UIGestureRecognizerDelegateProtocol {
    private var visible = true

    @ObjCAction
    fun handleTap() {
        visible = !visible
        onVisibility(visible)
    }

    override fun gestureRecognizer(
        gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer
    ): Boolean = true
}

// AVPlayerViewController hosted directly through UIKitViewController doesn't
// always relay layout changes from its parent — when entering the detail
// screen from a shared-element transition (e.g. the home memories story
// card) the controller's view mounts before the Compose slot has its final
// bounds, and AVKit then leaves it offset and oversized. Wrapping it in a
// parent controller lets us seed the initial frame, opt into UIKit's
// autoresizing so subsequent bounds changes propagate automatically, and
// re-pin on every layout pass as a defensive backstop.
@OptIn(ExperimentalForeignApi::class)
private class VideoPlayerContainerViewController(
    val playerViewController: AVPlayerViewController,
    private val tapHandler: VideoTapHandler
) : UIViewController(nibName = null, bundle = null) {

    override fun viewDidLoad() {
        super.viewDidLoad()
        addChildViewController(playerViewController)
        val playerView = playerViewController.view
        playerView.setFrame(view.bounds)
        playerView.setAutoresizingMask(
            UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        )
        view.addSubview(playerView)
        playerViewController.didMoveToParentViewController(this)

        // Observe taps without consuming them so the player's own controls
        // still toggle; we use it only to mirror visibility into host chrome.
        val tap = UITapGestureRecognizer(
            target = tapHandler,
            action = NSSelectorFromString("handleTap")
        )
        tap.setCancelsTouchesInView(false)
        tap.delegate = tapHandler
        view.addGestureRecognizer(tap)
    }

    override fun viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        playerViewController.view.setFrame(view.bounds)
    }
}
