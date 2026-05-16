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
 * grayscale donut; "free" / inactive segments fall back to surfaceVariant.
 */
data class ChartPalette(
    val photos: Color,
    val videos: Color,
    val free: Color,
    val accent: Color
)

@Composable
@ReadOnlyComposable
fun rememberChartPalette(): ChartPalette {
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    return ChartPalette(
        photos = primary,
        videos = if (isDark) Color(0xFF93C5FD) else Color(0xFF3B82F6),
        free = surfaceVariant,
        accent = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
    )
}
