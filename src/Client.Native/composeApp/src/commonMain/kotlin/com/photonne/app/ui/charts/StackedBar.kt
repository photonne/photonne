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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

data class StackedSegment(
    val value: Float,
    val color: Color,
    /** Sigue en la barra pero rayado: un espacio que no suma al total. */
    val hatched: Boolean = false
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
    animationDurationMs: Int = 600,
    /** Resumen para el lector de pantalla: un Canvas no dice nada por sí solo. */
    description: String? = null,
    /** Parte de la pista que ocupan los segmentos (0..1), para comparar filas. */
    fillFraction: Float = 1f
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
            .then(
                if (description != null) Modifier.semantics { contentDescription = description } else Modifier
            )
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

        val availableWidth = size.width * fillFraction.coerceIn(0f, 1f) * progress.value
        var x = 0f
        for (segment in sanitized) {
            val segWidth = (segment.value / total) * availableWidth
            if (segWidth <= 0f) continue
            // Each segment paints a rounded rect over its slot. Adjacent
            // segments overlap the rounded corners visually because of
            // how round rects render at <2*radius widths — fine for our
            // sizes, and gives the bar a continuous look.
            if (segment.hatched) {
                drawHatched(segment.color, Offset(x, 0f), Size(segWidth, size.height), radius)
            } else {
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(x, 0f),
                    size = Size(segWidth, size.height),
                    cornerRadius = radius
                )
            }
            x += segWidth
        }
    }
}

/**
 * Fondo tenue y rayas a 45° recortadas al segmento: "está, pero no cuenta".
 * Lo comparten la barra y las muestras de la leyenda.
 */
fun DrawScope.drawHatched(color: Color, topLeft: Offset, size: Size, radius: CornerRadius) {
    val clip = Path().apply {
        addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, radius))
    }
    clipPath(clip) {
        drawRect(color = color.copy(alpha = 0.18f), topLeft = topLeft, size = size)
        val step = 3.dp.toPx()
        val stroke = 1.dp.toPx()
        var start = topLeft.x - size.height
        while (start < topLeft.x + size.width) {
            drawLine(
                color = color,
                start = Offset(start, topLeft.y + size.height),
                end = Offset(start + size.height, topLeft.y),
                strokeWidth = stroke
            )
            start += step
        }
    }
}
