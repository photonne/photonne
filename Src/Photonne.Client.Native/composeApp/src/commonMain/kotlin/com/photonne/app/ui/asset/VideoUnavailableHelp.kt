package com.photonne.app.ui.asset

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.util.openExternalUrl

/**
 * Platform guidance for when [isVideoPlaybackSupported] is false: what the user
 * can install or change on THIS machine to get video playback, plus a link.
 * Android and iOS always play video, so their actual is null; desktop points at
 * VLC, whose libvlc is the playback engine there.
 */
data class VideoPlaybackUnavailableHelp(
    val message: String,
    val actionLabel: String,
    val actionUrl: String
)

expect val videoPlaybackUnavailableHelp: VideoPlaybackUnavailableHelp?

/**
 * Renders [videoPlaybackUnavailableHelp] under a "video unavailable" fallback
 * message; nothing when the platform has no actionable remedy.
 */
@Composable
fun VideoUnavailableHelpAction(modifier: Modifier = Modifier) {
    val help = videoPlaybackUnavailableHelp ?: return
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = help.message,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = { openExternalUrl(help.actionUrl) }) {
            Text(help.actionLabel)
        }
    }
}
