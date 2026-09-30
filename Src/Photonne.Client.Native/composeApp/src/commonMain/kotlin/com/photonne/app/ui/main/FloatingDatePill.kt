package com.photonne.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import com.photonne.app.ui.theme.PillShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.photonne.app.ui.theme.ChromeElevation
import com.photonne.app.ui.theme.Spacing

/** Un año y su posición (0..1) a lo largo de la pista del scrubber. */
data class ScrubberYearMarker(val year: String, val fraction: Float)

/**
 * Píldora de fecha flotante centrada arriba, gemela de [ScrollToTopPill] pero en
 * la parte superior. Muestra el mes-año de lo que hay arriba MIENTRAS se hace
 * scroll normal, y se desvanece tras una pausa. Se oculta con [suppressed]
 * (cuando el usuario arrastra el scrubber, donde la fecha vuelve al mango, o en
 * selección). El posicionamiento (TopCenter + margen bajo el cromo) lo pone el
 * host vía [modifier].
 */
@Composable
internal fun FloatingDatePill(
    label: String,
    isScrollInProgress: () -> Boolean,
    suppressed: Boolean,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val active = label.isNotEmpty() && !suppressed && isScrollInProgress()
    LaunchedEffect(active) {
        if (active) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible && !suppressed && label.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier,
    ) {
        ChromePill(hazeState = hazeState, elevation = ChromeElevation.pill) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

/**
 * Burbuja de fecha pegada al mango de un scrubber, visible solo mientras se
 * arrastra (sigue la fila o celda de destino del dedo). Misma tipografía que
 * [FloatingDatePill]; el oro de `primary` se reserva a esta burbuja para que la
 * fecha "agarrada" se distinga de la que solo informa. La comparten el scrubber
 * del timeline y el de las rejillas de álbum.
 */
@Composable
internal fun ScrubberDateBubble(
    label: String,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    ChromePill(modifier = modifier, hazeState = hazeState, elevation = ChromeElevation.pill) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

/**
 * Mango de un scrubber: cápsula de cristal con flechas arriba y abajo. Siempre
 * cristal (sin el realce `primaryContainer` que tuvo al arrastrar), a juego con
 * la burbuja de fecha y el botón de subir. El tamaño lo pone quien lo llama.
 */
@Composable
internal fun ScrubberHandle(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    ChromePill(modifier = modifier, hazeState = hazeState, elevation = ChromeElevation.pill) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Marcas de año a lo largo de la pista del scrubber (borde derecho, a la
 * izquierda del mango), visibles solo mientras se arrastra. Se dibujan dentro
 * del [BoxWithConstraints] del scrubber, así que reciben el alto útil de la
 * pista en píxeles y colocan cada año en su fracción real. Fondo sólido del
 * cromo ([chromeSolidColor], no blur) porque pueden ser varias y el cristal por
 * marca saldría caro.
 *
 * @param usableTrackPx alto de la pista descontando la altura del mango, igual
 *   que usa el offset del propio mango, para que un año caiga donde caería el
 *   mango en esa fracción.
 * @param handleEndPadding margen derecho para sentarse a la izquierda del mango.
 */
@Composable
internal fun BoxScope.ScrubberYearMarkers(
    markers: List<ScrubberYearMarker>,
    usableTrackPx: Float,
    minGapPx: Float,
    handleEndPadding: Dp,
    visible: Boolean,
    hazeState: HazeState? = null,
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "yearMarkers")
    if (alpha <= 0.01f || markers.isEmpty()) return
    // Filtra años demasiado juntos para que las etiquetas no se solapen.
    val shown = remember(markers, usableTrackPx, minGapPx) {
        var lastY = Float.NEGATIVE_INFINITY
        markers.mapNotNull { m ->
            val y = usableTrackPx * m.fraction
            if (y - lastY >= minGapPx) {
                lastY = y
                m to y
            } else {
                null
            }
        }
    }
    val markerColor = chromeSolidColor()
    shown.forEach { (m, yPx) ->
        Surface(
            shape = PillShape,
            color = markerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer { this.alpha = alpha }
                .offset { IntOffset(0, yPx.roundToInt()) }
                // Centra la etiqueta contra el mango en lugar de contra su borde
                // superior (el área táctil del mango mide 64dp de alto).
                .offset(y = 18.dp)
                .padding(end = handleEndPadding),
        ) {
            Text(
                text = m.year,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            )
        }
    }
}
