package com.photonne.app.ui.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Text that interpolates an [Int] toward [value] over [durationMs] using
 * a FastOutSlowIn ease. Avoids the "number snaps" feel when polling
 * pushes a new pending count into the dashboard tiles. Pass [formatter]
 * to inject thousand separators or units.
 */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    durationMs: Int = 600,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    formatter: (Int) -> String = { it.toString() }
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
    )
    Text(
        text = formatter(animated),
        style = style,
        color = color,
        modifier = modifier
    )
}
