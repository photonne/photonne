package com.photonne.app.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class StackedSegment(
    val value: Float,
    val color: Color
)

/**
 * Horizontal stacked bar with rounded ends. Segments share their height
 * and combine their values to fill the available width. The bar animates
 * from 0 → full width on first composition (and when [segments] change).
 *
 * Segments with a zero or negative value are skipped silently. If the
 * total of all segment values is zero, an empty track is drawn so the
 * caller can still rely on a consistent footprint.
 */
@Composable
fun StackedBar(
    segments: List<StackedSegment>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 12.dp,
    cornerRadius: Dp = 8.dp,
    trackColor: Color? = null,
    animationDurationMs: Int = 600
) {
    val sanitized = segments.filter { it.value > 0f }
    val total = sanitized.fold(0f) { acc, s -> acc + s.value }.coerceAtLeast(0.0001f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(sanitized) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = animationDurationMs, easing = FastOutSlowInEasing)
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        val radius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
        if (trackColor != null) {
            drawRoundRect(
                color = trackColor,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = radius
            )
        }
        if (sanitized.isEmpty()) return@Canvas

        val availableWidth = size.width * progress.value
        var x = 0f
        for (segment in sanitized) {
            val segWidth = (segment.value / total) * availableWidth
            if (segWidth <= 0f) continue
            // Each segment paints a rounded rect over its slot. Adjacent
            // segments overlap the rounded corners visually because of
            // how round rects render at <2*radius widths — fine for our
            // sizes, and gives the bar a continuous look.
            drawRoundRect(
                color = segment.color,
                topLeft = Offset(x, 0f),
                size = Size(segWidth, size.height),
                cornerRadius = radius
            )
            x += segWidth
        }
    }
}
