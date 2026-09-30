package com.photonne.app.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.photonne.app.ui.theme.LocalIsDarkTheme

/**
 * Shared chart colors used across the storage donut, stacked bars and
 * top-N bars. Photos lean on the brand gold (primary), videos on a
 * complementary cool tone so the two are easy to tell apart even on a
 * grayscale donut. This file is the single home for chart colours: the
 * blue is the only hue in the app outside the theme, on purpose.
 *
 * "Free" / inactive segments are a translucent onSurfaceVariant rather than
 * surfaceVariant: the storage donut sits on a surfaceVariant card, so that
 * slice (and its legend dot) used to vanish.
 */
data class ChartPalette(
    val photos: Color,
    val videos: Color,
    val free: Color
)

@Composable
@ReadOnlyComposable
fun rememberChartPalette(): ChartPalette {
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return ChartPalette(
        photos = primary,
        videos = if (isDark) Color(0xFF93C5FD) else Color(0xFF3B82F6),
        free = muted.copy(alpha = 0.25f)
    )
}
