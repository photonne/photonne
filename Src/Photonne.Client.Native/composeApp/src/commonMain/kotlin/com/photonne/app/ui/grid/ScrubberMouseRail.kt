package com.photonne.app.ui.grid

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

/**
 * Carril de ratón para los scrubbers (timeline y rejilla de álbum): pasar el
 * puntero por el borde derecho revela el scrubber, y pulsar o arrastrar sobre
 * el carril mueve el mango en ABSOLUTO (la y del puntero es la fracción),
 * complementando el arrastre en delta del propio mango.
 *
 * La franja solo instala su pointerInput después de un hover real. En táctil
 * el hover no existe, así que nunca roba toques cerca del borde — la razón por
 * la que los scrubbers limitan el arrastre al mango. Debe componerse ANTES que
 * el mango para que este, como hermano superior, siga ganando sus gestos.
 */
@Composable
internal fun ScrubberMouseRail(
    railWidth: Dp,
    touchHeightPx: Float,
    usableTrackPx: Float,
    onHoverChange: (Boolean) -> Unit,
    onScrubStart: (fraction: Float) -> Unit,
    onScrub: (fraction: Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val hoverChange by rememberUpdatedState(onHoverChange)
    val scrubStart by rememberUpdatedState(onScrubStart)
    val scrub by rememberUpdatedState(onScrub)
    val scrubEnd by rememberUpdatedState(onScrubEnd)
    val touchHeight by rememberUpdatedState(touchHeightPx)
    val usableTrack by rememberUpdatedState(usableTrackPx)

    LaunchedEffect(hovered) { hoverChange(hovered) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .hoverable(interactionSource)
            .then(
                if (hovered) {
                    Modifier.pointerInput(Unit) {
                        fun fractionFor(y: Float): Float =
                            ((y - touchHeight / 2f) / usableTrack).coerceIn(0f, 1f)
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            scrubStart(fractionFor(down.position.y))
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                change.consume()
                                scrub(fractionFor(change.position.y))
                            }
                            scrubEnd()
                        }
                    }
                } else {
                    Modifier
                }
            )
    )
}
