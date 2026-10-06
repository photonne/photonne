package com.photonne.app.ui.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

/**
 * Carril de ratón para los scrubbers (timeline y rejilla de álbum): pasar el
 * puntero por el borde derecho revela el scrubber, y pulsar o arrastrar sobre
 * el carril mueve el mango en ABSOLUTO (la y del puntero es la fracción),
 * complementando el arrastre en delta del propio mango.
 *
 * El hover se detecta con un nodo que COMPARTE el puntero con sus hermanos:
 * cualquier nodo de entrada (también `hoverable`) gana el hit-test frente a la
 * rejilla que tiene debajo aunque no consuma nada, y eso dejaba una franja
 * muerta de 48 dp a lo alto del borde derecho en táctil. La captura que sí
 * bloquea solo se instala tras un hover real de ratón, que en táctil no existe.
 * Debe componerse ANTES que el mango para que este, como hermano superior,
 * siga ganando sus gestos.
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
    var hovered by remember { mutableStateOf(false) }
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
            .then(MouseHoverPassThroughElement { hovered = it })
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

/**
 * Hover de ratón sin robar el hit-test: [sharePointerInputWithSiblings] deja
 * que el mismo evento siga llegando a la rejilla de debajo.
 */
private class MouseHoverPassThroughElement(
    private val onHoverChange: (Boolean) -> Unit,
) : ModifierNodeElement<MouseHoverPassThroughNode>() {
    override fun create() = MouseHoverPassThroughNode(onHoverChange)

    override fun update(node: MouseHoverPassThroughNode) {
        node.onHoverChange = onHoverChange
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "mouseHoverPassThrough"
    }

    override fun equals(other: Any?) =
        other is MouseHoverPassThroughElement && other.onHoverChange === onHoverChange

    override fun hashCode() = onHoverChange.hashCode()
}

private class MouseHoverPassThroughNode(
    var onHoverChange: (Boolean) -> Unit,
) : Modifier.Node(), PointerInputModifierNode {
    private var hovered = false

    private fun setHovered(value: Boolean) {
        if (hovered != value) {
            hovered = value
            onHoverChange(value)
        }
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        when (pointerEvent.type) {
            PointerEventType.Enter, PointerEventType.Move ->
                if (pointerEvent.changes.any { it.type == PointerType.Mouse }) setHovered(true)
            PointerEventType.Exit -> setHovered(false)
        }
    }

    override fun onCancelPointerInput() = setHovered(false)

    override fun onDetach() = setHovered(false)

    override fun sharePointerInputWithSiblings() = true
}
