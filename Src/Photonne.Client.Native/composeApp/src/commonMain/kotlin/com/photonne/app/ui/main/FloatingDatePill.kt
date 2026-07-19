package com.photonne.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp,
        ) {
            Box {
                Box(Modifier.matchParentSize().chromeCapsuleBackdrop(hazeState = hazeState))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Marcas de año a lo largo de la pista del scrubber (borde derecho, a la
 * izquierda del mango), visibles solo mientras se arrastra. Se dibujan dentro
 * del [BoxWithConstraints] del scrubber, así que reciben el alto útil de la
 * pista en píxeles y colocan cada año en su fracción real. Fondo gris sólido (no
 * blur) porque pueden ser varias y el cristal por marca saldría caro.
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
    val markerGray = chromeBaseGray()
    shown.forEach { (m, yPx) ->
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Transparent,
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
            Box(Modifier.background(markerGray.copy(alpha = 0.92f))) {
                Text(
                    text = m.year,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
