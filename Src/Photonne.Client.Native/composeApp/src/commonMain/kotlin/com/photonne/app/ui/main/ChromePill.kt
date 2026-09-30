package com.photonne.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.photonne.app.ui.theme.ChromeElevation
import com.photonne.app.ui.theme.PillShape
import dev.chrisbanes.haze.HazeState

/**
 * La cápsula de cristal esmerilado del cromo flotante: [PillShape] + sombra +
 * fondo difuminado ([chromeCapsuleBackdrop]). Antes estaba copiada literal en el
 * cromo de subpantalla, el del álbum, el del visor, las acciones del timeline,
 * la nav, las píldoras y los scrubbers.
 *
 * La `Surface` es transparente: aporta forma y sombra y RECORTA el blur a la
 * cápsula; el cristal lo pinta un `Box` de fondo del tamaño del contenido.
 *
 * @param dockedFraction 0 = flotante, 1 = acoplada. A medida que la barra se
 *   acopla el cristal y la sombra se desvanecen (el fondo lo pone entonces lo
 *   que haya detrás: el backdrop acoplado o la portada); el contenido sigue opaco.
 * @param elevation escalón de [ChromeElevation] de esta pieza.
 * @param baseColor gris del tinte; el visor lo fija oscuro en ambos temas.
 * @param contentColor color del contenido; el visor va en blanco sobre foto.
 * @param onClick si no es null la cápsula entera es un botón.
 */
@Composable
fun ChromePill(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    elevation: Dp = ChromeElevation.bar,
    dockedFraction: Float = 0f,
    baseColor: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val body: @Composable () -> Unit = {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - dockedFraction }
                    .chromeCapsuleBackdrop(baseColor = baseColor, hazeState = hazeState)
            )
            content()
        }
    }
    val shadow = elevation * (1f - dockedFraction)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = PillShape,
            color = Color.Transparent,
            contentColor = contentColor,
            shadowElevation = shadow,
            content = body
        )
    } else {
        Surface(
            modifier = modifier,
            shape = PillShape,
            color = Color.Transparent,
            contentColor = contentColor,
            shadowElevation = shadow,
            content = body
        )
    }
}
