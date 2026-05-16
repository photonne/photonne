package com.photonne.app.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DonutSlice(
    val value: Float,
    val color: Color
)

/**
 * Donut chart drawn with `drawArc` on a [Canvas]. The full sweep animates
 * from 0 → 360° once on first composition (or whenever [slices] changes),
 * and each slice is separated by a small gap so adjacent segments stay
 * visually distinct even when the strokes meet at a sharp angle.
 *
 * Pass [centerContent] to render anything inside the hole (commonly a
 * totals label). Slices with a zero or negative value are skipped.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 18.dp,
    gapDegrees: Float = 2.5f,
    startAngle: Float = -90f,
    animationDurationMs: Int = 700,
    centerContent: @Composable () -> Unit = {}
) {
    val sanitized = slices.filter { it.value > 0f }
    val total = sanitized.fold(0f) { acc, s -> acc + s.value }.coerceAtLeast(0.0001f)
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(sanitized) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = animationDurationMs, easing = FastOutSlowInEasing)
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (sanitized.isEmpty()) return@Canvas
            val sw = strokeWidth.toPx()
            val diameter = size.minDimension - sw
            if (diameter <= 0f) return@Canvas
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            val sliceCount = sanitized.size
            val gapBudget = if (sliceCount > 1) gapDegrees * sliceCount else 0f
            val drawable = (360f - gapBudget).coerceAtLeast(0f)

            var cursor = startAngle
            var consumed = 0f
            val targetSweep = drawable * animationProgress.value
            for (slice in sanitized) {
                val sweep = (slice.value / total) * drawable
                val clipped = sweep.coerceAtMost((targetSweep - consumed).coerceAtLeast(0f))
                if (clipped <= 0f) break
                drawArc(
                    color = slice.color,
                    startAngle = cursor,
                    sweepAngle = clipped,
                    useCenter = false,
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                    topLeft = topLeft,
                    size = arcSize
                )
                cursor += sweep + gapDegrees
                consumed += sweep
            }
        }
        centerContent()
    }
}
