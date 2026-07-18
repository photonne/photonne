package com.photonne.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores semánticos que el `ColorScheme` de Material no cubre. Antes cada
 * pantalla se inventaba su rojo de "favorito" (0xFFFF5252 / 0xFFE53935 /
 * 0xFFF44336) y su verde de "éxito" (0xFF66BB6A / 0xFF1B5E20), y los velos negros
 * sobre foto usaban 10 alfas distintas. Aquí viven una sola vez, por tema.
 *
 * Para "destructivo" NO hay token: se usa `MaterialTheme.colorScheme.error`
 * (ya el patrón mayoritario, 115 usos). Este objeto solo cubre lo que el
 * esquema de Material deja fuera.
 */
data class PhotonneSemanticColors(
    /** Rojo del corazón de favorito (sobre miniatura o en la barra del visor). */
    val favorite: Color,
    /** Verde de estado correcto (backup completado, subida OK). */
    val success: Color,
    /** Contenido sobre [success]. */
    val onSuccess: Color,
    /** Velo negro suave sobre imagen (degradados de legibilidad ligeros). */
    val scrimLight: Color,
    /** Velo negro medio: badges de contador, chips sobre miniatura. */
    val scrimMedium: Color,
    /** Velo negro fuerte: cabeceras/pies sobre foto que deben leerse siempre. */
    val scrimHeavy: Color
)

private val LightSemanticColors = PhotonneSemanticColors(
    favorite = Color(0xFFFF5252),
    success = Color(0xFF2E7D32),
    onSuccess = Color(0xFFFFFFFF),
    scrimLight = Color.Black.copy(alpha = 0.35f),
    scrimMedium = Color.Black.copy(alpha = 0.55f),
    scrimHeavy = Color.Black.copy(alpha = 0.70f)
)

private val DarkSemanticColors = LightSemanticColors.copy(
    // El corazón mantiene el rojo vivo en ambos temas; el verde se aclara para
    // leerse sobre superficies casi-negras.
    success = Color(0xFF66BB6A),
    onSuccess = Color(0xFF10240F)
)

val LocalPhotonneColors = staticCompositionLocalOf { LightSemanticColors }

/** Elige la paleta semántica según el tema efectivo de la app. */
internal fun semanticColorsFor(darkTheme: Boolean): PhotonneSemanticColors =
    if (darkTheme) DarkSemanticColors else LightSemanticColors

/**
 * Acceso corto a los colores semánticos: `PhotonneColors.favorite`, en paralelo
 * a `MaterialTheme.colorScheme`.
 */
object PhotonneColors {
    val favorite: Color
        @Composable @ReadOnlyComposable get() = LocalPhotonneColors.current.favorite
    val success: Color
        @Composable @ReadOnlyComposable get() = LocalPhotonneColors.current.success
    val onSuccess: Color
        @Composable @ReadOnlyComposable get() = LocalPhotonneColors.current.onSuccess
    val scrimLight: Color
        @Composable @ReadOnlyComposable get() = LocalPhotonneColors.current.scrimLight
    val scrimMedium: Color
        @Composable @ReadOnlyComposable get() = LocalPhotonneColors.current.scrimMedium
    val scrimHeavy: Color
        @Composable @ReadOnlyComposable get() = LocalPhotonneColors.current.scrimHeavy
}

/** Suprime el "unused" del receptor mientras da acceso vía MaterialTheme si se prefiere. */
@Suppress("UnusedReceiverParameter")
val MaterialTheme.photonneColors: PhotonneSemanticColors
    @Composable @ReadOnlyComposable get() = LocalPhotonneColors.current
