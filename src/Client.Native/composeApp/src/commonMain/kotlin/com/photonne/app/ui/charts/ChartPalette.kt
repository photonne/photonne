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
 * blue and the storage-space green and violet are the only hues in the app
 * outside the theme, on purpose.
 *
 * "Free" / inactive segments are a translucent onSurfaceVariant rather than
 * surfaceVariant: the storage donut sits on a surfaceVariant card, so that
 * slice (and its legend dot) used to vanish.
 */
data class ChartPalette(
    val photos: Color,
    val videos: Color,
    val free: Color,
    /** Espacios de almacenamiento: los mismos tonos que el SPA. */
    val spacePersonal: Color,
    val spaceShared: Color,
    val spaceLibrary: Color,
    /** Rayado de un espacio quitado del total. */
    val spaceHidden: Color
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
        free = muted.copy(alpha = 0.25f),
        spacePersonal = primary,
        spaceShared = if (isDark) Color(0xFF86D39F) else Color(0xFF2F7D4F),
        spaceLibrary = if (isDark) Color(0xFFC4A3E8) else Color(0xFF7B4FA6),
        spaceHidden = muted
    )
}
