package com.photonne.app.ui.charts

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A gold "this is live" indicator: a solid core dot surrounded by an
 * outward-rippling halo. Use it to signal that something is actively
 * being processed (e.g., a backfill queue is non-empty). Renders nothing
 * when [active] is false so callers can use it unconditionally.
 */
@Composable
fun LiveDot(
    active: Boolean,
    modifier: Modifier = Modifier,
    coreSize: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (!active) return
    val infinite = rememberInfiniteTransition()
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    // [pulse] drives one halo cycle: scale lerps 1 → 2.5 while alpha
    // fades 0.55 → 0, giving the "ripple" feel without spawning multiple
    // animated children.
    val haloScale = 1f + pulse * 1.5f
    val haloAlpha = (1f - pulse) * 0.55f

    Box(
        modifier = modifier.size(coreSize * 3),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(coreSize)
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                    alpha = haloAlpha
                }
                .clip(CircleShape)
                .background(color)
        )
        Box(
            modifier = Modifier
                .size(coreSize)
                .clip(CircleShape)
                .background(color)
        )
    }
}
