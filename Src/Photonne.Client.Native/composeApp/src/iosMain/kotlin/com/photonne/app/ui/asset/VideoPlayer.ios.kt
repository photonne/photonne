package com.photonne.app.ui.asset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetHTTPHeaderFieldsKey
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

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
        val asset = if (nsUrl != null) {
            val options = if (headers.isNotEmpty()) {
                mapOf<Any?, Any?>(AVURLAssetHTTPHeaderFieldsKey to headers)
            } else null
            AVURLAsset(uRL = nsUrl, options = options)
        } else null
        val item = asset?.let { AVPlayerItem(asset = it) }
        AVPlayer(playerItem = item)
    }

    val controller = remember(player) {
        AVPlayerViewController().apply {
            this.player = player
            this.showsPlaybackControls = true
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.pause()
            controller.player = null
        }
    }

    UIKitViewController(
        modifier = modifier,
        factory = { controller }
    )
}
