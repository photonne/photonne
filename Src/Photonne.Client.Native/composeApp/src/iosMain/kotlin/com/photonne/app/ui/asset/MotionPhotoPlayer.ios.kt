package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setMuted
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

// Mirrors VideoPlayer.ios.kt: the header-fields key isn't exposed by the
// Kotlin/Native AVFoundation bindings, so fall back to the documented string.
private const val AVURLAssetHTTPHeaderFieldsKey = "AVURLAssetHTTPHeaderFieldsKey"

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MotionPhotoPlayer(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier
) {
    val nsUrl = remember(url) { NSURL.URLWithString(url) }
    // Keep a handle on the item so the loop observer can scope itself to this
    // clip's end notification — the ObjC custom getter (isMuted) means the
    // bindings expose setMuted() rather than a `muted` property.
    val item = remember(url, headers) {
        val asset = if (nsUrl != null) {
            val options = if (headers.isNotEmpty()) {
                mapOf<Any?, Any?>(AVURLAssetHTTPHeaderFieldsKey to headers)
            } else null
            AVURLAsset(uRL = nsUrl, options = options)
        } else null
        asset?.let { AVPlayerItem(asset = it) }
    }
    val player = remember(item) {
        AVPlayer(playerItem = item).apply {
            // Motion clips are silent affordances; never honour the ring/silent
            // switch the way the full VideoPlayer deliberately does.
            setMuted(true)
        }
    }

    // Loop by seeking back to the start whenever the item reaches the end.
    val loopObserver = remember(player, item) {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
            usingBlock = { _ ->
                player.seekToTime(CMTimeMake(value = 0, timescale = 1))
                player.play()
            }
        )
    }

    val container = remember(player) {
        val playerVC = AVPlayerViewController().apply {
            this.player = player
            this.showsPlaybackControls = false
        }
        MotionPlayerContainerViewController(playerVC)
    }

    DisposableEffect(player) {
        player.play()
        onDispose {
            player.pause()
            NSNotificationCenter.defaultCenter.removeObserver(loopObserver)
            container.playerViewController.player = null
        }
    }

    UIKitViewController(
        modifier = modifier,
        factory = { container }
    )
}

// Same defensive layout wrapper rationale as VideoPlayer.ios.kt: seed the
// initial frame and opt into autoresizing so AVKit doesn't leave the view
// offset when mounted before Compose hands it final bounds.
@OptIn(ExperimentalForeignApi::class)
private class MotionPlayerContainerViewController(
    val playerViewController: AVPlayerViewController
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
    }

    override fun viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        playerViewController.view.setFrame(view.bounds)
    }
}
