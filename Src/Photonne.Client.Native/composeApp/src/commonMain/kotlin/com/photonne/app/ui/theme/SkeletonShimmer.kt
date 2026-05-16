package com.photonne.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Paints a soft gold-tinted shimmer over the modified node. Pair with a
 * `.background(surfaceVariant)` (or just a shape clip) below it so the
 * animated highlight has something to slide across.
 *
 * The base tone uses `MaterialTheme.colorScheme.primary` to keep the
 * brand identity readable in both light and dark themes, but at very low
 * alphas so the placeholder reads as "loading" and not "active element".
 */
@Composable
fun Modifier.goldShimmer(
    durationMillis: Int = 1400
): Modifier {
    val transition = rememberInfiniteTransition()
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val baseColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val low = baseColor.copy(alpha = 0.08f)
    val high = baseColor.copy(alpha = 0.24f)
    return this.drawWithCache {
        // Diagonal highlight that travels from top-left to bottom-right.
        val travel = size.width * 2f
        val offset = -size.width + travel * phase
        val brush = Brush.linearGradient(
            colors = listOf(low, high, low),
            start = Offset(offset, 0f),
            end = Offset(offset + size.width, size.height)
        )
        onDrawBehind {
            drawRect(color = low)
            drawRect(brush = brush)
        }
    }
}

/**
 * Convenience block that fills the given size with the gold shimmer.
 * The corner radius defaults to the project's `medium` shape so most
 * placeholder cards drop in without bespoke styling.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .goldShimmer()
    )
}

/**
 * Tinted neutral placeholder — no animation. Use for static skeletons (e.g.
 * a placeholder for text that's about to swap in) where the shimmer would
 * be noisy.
 */
@Composable
fun SkeletonChip(
    width: Dp,
    height: Dp,
    cornerRadius: Dp = 8.dp
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                androidx.compose.material3.MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.10f)
            )
    )
}
