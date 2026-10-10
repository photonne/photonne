package com.photonne.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.photonne.app.data.settings.ThemePreference
import com.photonne.app.resources.Res
import com.photonne.app.resources.photonne_logo_dark
import com.photonne.app.resources.photonne_logo_light
import org.jetbrains.compose.resources.painterResource

// Brand colors mirror src/Client.Web/Services/ThemeService.cs so the
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

// Neutral slate ladder for the light surfaceContainer* tokens. Material3's
// defaults are lavender-tinted (~0xFFF3EDF7), which is what bottom sheets,
// menus, dialogs, date pickers and switch tracks picked up in light mode.
private val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
private val SurfaceContainerLowLight = Color(0xFFF8F9FA)
private val SurfaceContainerLight = Color(0xFFF3F4F6)
private val SurfaceContainerHighLight = Color(0xFFEEF0F3)
private val SurfaceContainerHighestLight = Color(0xFFE7EAEE)
private val SurfaceDimLight = Color(0xFFDDE1E6)
private val SurfaceBrightLight = Color(0xFFFFFFFF)
private val OutlineLight = Color(0xFF94A3B8)
private val OutlineVariantLight = Color(0xFFE2E8F0)
private val InverseSurfaceLight = Color(0xFF1E1E2E)
private val InverseOnSurfaceLight = Color(0xFFF1F5F9)

// Near-black neutral dark theme — drops the old blue-grey tint so surfaces
// read as truly dark instead of dark grey.
private val SurfaceDark = Color(0xFF121214)
private val BackgroundDark = Color(0xFF0A0A0C)
private val OnSurfaceDark = Color(0xFFE2E8F0)
private val OnSurfaceVariantDark = Color(0xFF94A3B8)
private val SurfaceVariantDark = Color(0xFF1C1C20)

// Near-black ladder for the surfaceContainer* tokens. Material3 leaves these
// at a lavender-tinted grey (~0xFF211F26) by default, which is what made the
// bottom navigation bar, sheets and cards read as light-grey chrome. Pin them
// to near-black so tinted-elevation surfaces stay truly dark.
private val SurfaceContainerLowestDark = Color(0xFF050506)
private val SurfaceContainerLowDark = Color(0xFF0E0E10)
private val SurfaceContainerDark = Color(0xFF141416)
private val SurfaceContainerHighDark = Color(0xFF1A1A1D)
private val SurfaceContainerHighestDark = Color(0xFF222226)
private val SurfaceDimDark = Color(0xFF0A0A0C)
private val SurfaceBrightDark = Color(0xFF29292E)
private val OutlineDark = Color(0xFF64748B)
private val OutlineVariantDark = Color(0xFF2A2A30)
private val InverseSurfaceDark = Color(0xFFE2E8F0)
private val InverseOnSurfaceDark = Color(0xFF1A1A1D)

// secondary/tertiary are the brand gold, so their containers are the gold
// container too. Left unset, Material3 fills them with its lavender/mauve
// baseline — FilterChip selection, MetaBadge and the tonal cards showed it.
private val LightColors = lightColorScheme(
    primary = GoldPrimaryLight,
    onPrimary = GoldOnPrimaryLight,
    primaryContainer = GoldContainerLight,
    onPrimaryContainer = GoldOnContainerLight,
    inversePrimary = GoldPrimaryDark,
    secondary = GoldPrimaryLight,
    onSecondary = GoldOnPrimaryLight,
    secondaryContainer = GoldContainerLight,
    onSecondaryContainer = GoldOnContainerLight,
    tertiary = GoldPrimaryLight,
    onTertiary = GoldOnPrimaryLight,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = GoldOnContainerLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceBrightLight,
    // Same as dark: no gold tint on tonally-elevated surfaces.
    surfaceTint = SurfaceLight,
    onSurface = OnSurfaceLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight
)

private val DarkColors = darkColorScheme(
    primary = GoldPrimaryDark,
    onPrimary = GoldOnPrimaryDark,
    primaryContainer = GoldContainerDark,
    onPrimaryContainer = GoldOnContainerDark,
    inversePrimary = GoldPrimaryLight,
    secondary = GoldPrimaryDark,
    onSecondary = GoldOnPrimaryDark,
    secondaryContainer = GoldContainerDark,
    onSecondaryContainer = GoldOnContainerDark,
    tertiary = GoldPrimaryDark,
    onTertiary = GoldOnPrimaryDark,
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = GoldOnContainerDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceDim = SurfaceDimDark,
    surfaceBright = SurfaceBrightDark,
    // Kill the elevation tint so tonally-elevated surfaces (nav bar, sheets)
    // don't get lightened/tinted back toward grey.
    surfaceTint = SurfaceDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark
)

/**
 * Los dos esquemas, fuera del tema activo: las miniaturas del selector de tema
 * (Apariencia) pintan el claro y el oscuro a la vez, sea cual sea el actual.
 */
internal val PhotonneLightColorScheme: ColorScheme get() = LightColors
internal val PhotonneDarkColorScheme: ColorScheme get() = DarkColors

private val PhotonneShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Asymmetric corner radius reserved for "memory" surfaces (Hub stories,
 * memory sheets). The oversized top-left corner makes these cards read
 * as keepsakes/clippings instead of generic content cards.
 */
val MemoryCardShape: RoundedCornerShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 12.dp,
    bottomEnd = 12.dp,
    bottomStart = 12.dp
)

/**
 * Exposes the effective dark/light state to descendants so resource pickers
 * (e.g. [photonneLogoPainter]) follow the user's in-app preference, not just
 * the OS setting — mirrors the PWA's LayoutService.IsDarkMode behavior.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

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
    CompositionLocalProvider(
        LocalIsDarkTheme provides useDarkTheme,
        LocalPhotonneColors provides semanticColorsFor(useDarkTheme)
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) DarkColors else LightColors,
            shapes = PhotonneShapes,
            content = content
        )
    }
}

/** Photonne wordmark variant that follows the effective in-app theme. */
@Composable
fun photonneLogoPainter(): Painter =
    painterResource(
        if (LocalIsDarkTheme.current) Res.drawable.photonne_logo_dark
        else Res.drawable.photonne_logo_light
    )
