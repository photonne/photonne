package com.photonne.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * El [HazeState] compartido que difumina el contenido por debajo del cromo
 * flotante. Lo publica quien dibuja la fuente (`MainScaffold` para la nav; cada
 * pantalla con scroll propio para sus cápsulas), y lo consumen las cápsulas
 * aunque vivan en slots distintos del árbol. `null` → no hay nada que difuminar,
 * y [chromeCapsuleBackdrop] cae a un gris sólido de reserva.
 *
 * Regla de Haze: un `hazeEffect` NO puede colgar de su propia fuente `hazeSource`
 * (leería una capa a medio grabar y saldría transparente). Por eso las pantallas
 * que dibujan su propio contenido (timeline, álbum, visor, mapa) publican un
 * estado cuya fuente es SOLO lo que scrollea/dibuja por detrás, de modo que sus
 * cápsulas queden como HERMANAS de esa fuente y no como descendientes.
 */
val LocalChromeHazeState = compositionLocalOf<HazeState?> { null }

/** Radio del desenfoque del cristal esmerilado. */
private val ChromeBlurRadius = 30.dp

/**
 * Cuánto tiñe el gris base sobre el contenido difuminado. Sube la solidez para
 * que los blancos del contenido se lean; baja para dejar ver más la foto.
 */
private const val ChromeTintAlpha = 0.6f

/**
 * Opacidad del gris de reserva cuando no hay fuente de blur (cápsula acoplada o
 * descendiente de su propia fuente): sólido, sin cristal, solo unifica color.
 */
private const val ChromeFallbackAlpha = 0.92f

/**
 * Gris base del cristal, MÁS OSCURO que el fondo del ítem activo: el blur adopta
 * el color de las fotos de debajo, pero el tinte lo mantiene oscuro y por debajo
 * del velo del pill activo para que este siga contrastando.
 */
@Composable
fun chromeBaseGray(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF242428)
    else Color(0xFFE9E9EE)

/** Velo del ítem activo de la nav, concéntrico dentro de la cápsula. */
@Composable
fun chromeActivePillColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color.White.copy(alpha = 0.12f)
    else Color.Black.copy(alpha = 0.07f)

/**
 * Pinta el fondo de una cápsula de cromo como cristal esmerilado: difumina en
 * tiempo real el contenido de la fuente [hazeState] (o [LocalChromeHazeState] si
 * no se pasa) y lo tiñe con [baseColor] (o [chromeBaseGray] por tema).
 *
 * @param baseColor fuerza el gris del tinte. El visor lo fija OSCURO en ambos
 *   temas porque su cromo va blanco sobre la foto.
 * @param hazeState fuente explícita, para cápsulas fuera de `MainScaffold` que
 *   no pueden heredar el estado por el composition local (o que necesitan una
 *   fuente distinta a la que hay en ese punto del árbol).
 *
 * Se aplica dentro de una `Surface` transparente que aporta forma + sombra y
 * RECORTA el blur a la cápsula, sobre un `Box(Modifier.matchParentSize())`.
 */
fun Modifier.chromeCapsuleBackdrop(
    baseColor: Color = Color.Unspecified,
    hazeState: HazeState? = null
): Modifier = composed {
    val state = hazeState ?: LocalChromeHazeState.current
    val base = if (baseColor.isSpecified) baseColor else chromeBaseGray()
    if (state != null) {
        hazeEffect(state) {
            blurRadius = ChromeBlurRadius
            backgroundColor = base
            tints = listOf(HazeTint(base.copy(alpha = ChromeTintAlpha)))
            noiseFactor = 0f
        }
    } else {
        background(base.copy(alpha = ChromeFallbackAlpha))
    }
}
