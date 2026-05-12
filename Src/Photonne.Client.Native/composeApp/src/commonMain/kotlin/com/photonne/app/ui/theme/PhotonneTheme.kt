package com.photonne.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photonne.app.data.settings.ThemePreference

// Brand colors mirror Src/Photonne.Client.Web/Services/ThemeService.cs so the
// native and PWA clients share the same gold identity.
private val GoldPrimaryLight = Color(0xFFC8960A)
private val GoldOnPrimaryLight = Color(0xFFFFFFFF)
private val GoldContainerLight = Color(0xFFFFEFC2)
private val GoldOnContainerLight = Color(0xFF3A2A00)

private val GoldPrimaryDark = Color(0xFFFFD166)
private val GoldOnPrimaryDark = Color(0xFF1A1A2E)
private val GoldContainerDark = Color(0xFF564000)
private val GoldOnContainerDark = Color(0xFFFFE2A4)

private val SurfaceLight = Color(0xFFFFFFFF)
private val BackgroundLight = Color(0xFFF8F9FA)
private val OnSurfaceLight = Color(0xFF1E1E2E)
private val OnSurfaceVariantLight = Color(0xFF64748B)
private val SurfaceVariantLight = Color(0xFFF1F5F9)

private val SurfaceDark = Color(0xFF1A1B26)
private val BackgroundDark = Color(0xFF0F1117)
private val OnSurfaceDark = Color(0xFFE2E8F0)
private val OnSurfaceVariantDark = Color(0xFF94A3B8)
private val SurfaceVariantDark = Color(0xFF1E1F2E)

private val LightColors = lightColorScheme(
    primary = GoldPrimaryLight,
    onPrimary = GoldOnPrimaryLight,
    primaryContainer = GoldContainerLight,
    onPrimaryContainer = GoldOnContainerLight,
    secondary = GoldPrimaryLight,
    tertiary = GoldPrimaryLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight
)

private val DarkColors = darkColorScheme(
    primary = GoldPrimaryDark,
    onPrimary = GoldOnPrimaryDark,
    primaryContainer = GoldContainerDark,
    onPrimaryContainer = GoldOnContainerDark,
    secondary = GoldPrimaryDark,
    tertiary = GoldPrimaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark
)

private val PhotonneShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PhotonneTheme(
    preference: ThemePreference = ThemePreference.System,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (preference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        shapes = PhotonneShapes,
        content = content
    )
}
