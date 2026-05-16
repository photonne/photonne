package com.photonne.app.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class TopNEntry(
    val label: String,
    val value: Float,
    val displayValue: String,
    val color: Color
)

/**
 * Vertical list of horizontal bars sized relative to the maximum entry.
 * Each row is `label · bar · displayValue`. The bars animate from 0 → full
 * length together on first composition (and when [entries] changes), so
 * the layout doesn't jump around as data loads.
 *
 * Entries are NOT re-sorted — pass them in the order you want them shown.
 */
@Composable
fun TopNBars(
    entries: List<TopNEntry>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
    cornerRadius: Dp = 6.dp,
    trackColor: Color? = null,
    animationDurationMs: Int = 700,
    labelWidth: Dp = 96.dp,
    valueWidth: Dp = 80.dp
) {
    val maxValue = entries.maxOfOrNull { it.value }?.coerceAtLeast(0.0001f) ?: 0.0001f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(entries) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = animationDurationMs, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        entries.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(labelWidth).padding(end = 8.dp)
                )
                SingleBar(
                    value = entry.value,
                    maxValue = maxValue,
                    color = entry.color,
                    trackColor = trackColor,
                    barHeight = barHeight,
                    cornerRadius = cornerRadius,
                    progressFactor = progress.value,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.displayValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(valueWidth)
                )
            }
        }
    }
}

@Composable
private fun SingleBar(
    value: Float,
    maxValue: Float,
    color: Color,
    trackColor: Color?,
    barHeight: Dp,
    cornerRadius: Dp,
    progressFactor: Float,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.height(barHeight).fillMaxWidth()
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
        val targetFraction = (value / maxValue).coerceIn(0f, 1f)
        val currentWidth = size.width * targetFraction * progressFactor
        if (currentWidth <= 0f) return@Canvas
        drawRoundRect(
            color = color,
            topLeft = Offset.Zero,
            size = Size(currentWidth, size.height),
            cornerRadius = radius
        )
    }
}
