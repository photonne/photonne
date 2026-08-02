package com.photonne.app.ui.grid.dragselect

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.photonne.app.ui.haptics.HapticEvent
import com.photonne.app.ui.haptics.HapticThrottle
import com.photonne.app.ui.haptics.PhotonneHaptics
import com.photonne.app.ui.selection.SelectionPatch
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Arrastre en banda sobre una rejilla de assets.
 *
 * Se pone sobre el nodo del propio Lazy, no sobre las celdas: las celdas se
 * reciclan al desplazarse y un gesto anclado a una de ellas moriría en cuanto
 * saliera de pantalla, que es justo lo que tiene que sobrevivir.
 *
 * [enabled] a false devuelve el modificador intacto, así que una pantalla que
 * no quiera arrastre (la vista Año del timeline) no paga nada.
 */
@Composable
internal fun Modifier.dragSelectable(
    state: DragSelectState,
    adapter: DragSelectAdapter,
    /**
     * La rejilla que se auto-desplaza al llegar el dedo a un borde. Se toma su
     * mutex con [MutatePriority.PreventUserInput] durante todo el gesto, así
     * que además es lo que garantiza que nada mueva la lista por debajo
     * aunque algún evento se nos escape.
     */
    scrollableState: ScrollableState,
    enabled: Boolean,
    selectionActive: Boolean,
    isSelected: (String) -> Boolean,
    onPatch: (SelectionPatch) -> Unit,
    haptics: PhotonneHaptics,
    railStartPx: Float = 0f,
    config: DragSelectConfig = DragSelectConfig.Default
): Modifier {
    // Todo lo mutable entra por un holder: el pointerInput vive en una
    // corrutina larga y volver a lanzarla en cada cambio de selección mataría
    // el gesto en curso justo al primer parche.
    val currentAdapter by rememberUpdatedState(adapter)
    val currentActive by rememberUpdatedState(selectionActive)
    val currentSelected by rememberUpdatedState(isSelected)
    val currentPatch by rememberUpdatedState(onPatch)
    val currentHaptics by rememberUpdatedState(haptics)
    val currentRailStart by rememberUpdatedState(railStartPx)
    val currentConfig by rememberUpdatedState(config)

    return this.pointerInput(state, enabled) {
        if (!enabled) return@pointerInput
        coroutineScope {
        val gestureScope = this

        var session: DragSelectSession? = null
        var throttle: HapticThrottle? = null
        var autoScrollJob: Job? = null

        fun moveTo(position: Offset, atMillis: Long) {
            val active = session ?: return
            state.pointer = position
            val kind = state.kind ?: return
            val ordinal = when (kind) {
                DragSelectKind.Cells -> currentAdapter.cellAt(position)?.ordinal
                DragSelectKind.Rail -> currentAdapter.rowAt(position)?.rowKey
                // Un frame en el que el dedo cae en la separación de 2 dp no
                // mueve la banda; el siguiente la corrige y no queda hueco,
                // porque se recalcula entera desde el ancla.
            } ?: return
            val patch = active.moveTo(ordinal)
            if (patch.isEmpty) return
            currentPatch(patch)
            if (currentConfig.haptics) throttle?.tick(kind.hapticEvent(), atMillis)
        }

        fun begin(position: Offset, kind: DragSelectKind, atMillis: Long): Boolean {
            val cfg = currentConfig
            val adapterNow = currentAdapter
            val anchorOrdinal: Int
            val anchorIds: List<String>
            when (kind) {
                DragSelectKind.Cells -> {
                    val cell = adapterNow.cellAt(position) ?: return false
                    anchorOrdinal = cell.ordinal
                    anchorIds = listOf(cell.id)
                }
                DragSelectKind.Rail -> {
                    val row = adapterNow.rowAt(position) ?: return false
                    anchorOrdinal = row.rowKey
                    anchorIds = adapterNow.idsInRow(row.rowKey)
                    if (anchorIds.isEmpty()) return false
                }
            }
            val selectedNow = currentSelected
            // La foto de la selección al arrancar es lo que permite que
            // retroceder devuelva las celdas a como estaban, en vez de
            // borrarlas.
            val base = HashSet<String>()
            val newSession = DragSelectSession(
                anchorOrdinal = anchorOrdinal,
                mode = modeForAnchor(anchorIds) { id ->
                    selectedNow(id).also { if (it) base += id }
                },
                baseSelected = { id -> id in base || selectedNow(id) },
                idsAt = { ordinal ->
                    when (kind) {
                        DragSelectKind.Cells -> currentAdapter.idsAtOrdinal(ordinal)
                        DragSelectKind.Rail -> currentAdapter.idsInRow(ordinal)
                    }
                }
            )
            session = newSession
            state.kind = kind
            state.pointer = position
            // Antes del primer parche: así el latch del cromo ve la selección
            // estrenarse con el gesto ya en marcha y no reflowea bajo el dedo.
            state.isDragging = true
            if (cfg.haptics) {
                currentHaptics.prepare()
                currentHaptics.perform(HapticEvent.SelectionStart)
                throttle = HapticThrottle(currentHaptics).also { it.tick(kind.hapticEvent(), atMillis) }
            }
            currentPatch(newSession.start())
            autoScrollJob = gestureScope.launch {
                runAutoScroll(
                    state = state,
                    scrollableState = scrollableState,
                    config = { currentConfig },
                    onFrame = { nowMillis -> moveTo(state.pointer, nowMillis) }
                )
            }
            return true
        }

        fun end() {
            val hadSession = session != null
            session = null
            throttle = null
            autoScrollJob?.cancel()
            autoScrollJob = null
            state.isDragging = false
            state.kind = null
            if (hadSession && currentConfig.haptics) {
                currentHaptics.perform(HapticEvent.SelectionEnd)
            }
        }

        detectDragSelectGesture(
            config = { currentConfig },
            selectionActive = { currentActive },
            railStartPx = { currentRailStart },
            onBegin = ::begin,
            onMove = ::moveTo,
            onEnd = ::end
        )
        }
    }
}

/**
 * Desplaza la rejilla mientras el dedo aguanta en una franja de borde, para
 * poder seleccionar más allá de lo que cabe en pantalla.
 *
 * Se toma el mutex de scroll con [MutatePriority.PreventUserInput] durante
 * todo el gesto: no lo puede interrumpir un scroll de usuario, así que sirve
 * a la vez de motor del auto-scroll y de cerrojo contra que la lista se mueva
 * por debajo si algún evento se nos escapara sin consumir.
 */
private suspend fun PointerInputScope.runAutoScroll(
    state: DragSelectState,
    scrollableState: ScrollableState,
    config: () -> DragSelectConfig,
    onFrame: (nowMillis: Long) -> Unit
) {
    val cfg = config()
    val zonePx = cfg.autoScrollEdge.toPx()
    val topEdge = cfg.autoScrollTopInset.toPx()
    val bottomEdge = size.height - cfg.autoScrollBottomInset.toPx()
    val maxPxPerSecond = cfg.autoScrollMaxDpPerSecond * density

    scrollableState.scroll(MutatePriority.PreventUserInput) {
        var lastNanos = 0L
        while (state.isDragging) {
            val nowNanos = withFrameNanos { it }
            val seconds = if (lastNanos == 0L) 0f else (nowNanos - lastNanos) / 1_000_000_000f
            lastNanos = nowNanos
            val velocity = autoScrollVelocity(
                y = state.pointer.y,
                topEdge = topEdge,
                bottomEdge = bottomEdge,
                zonePx = zonePx,
                maxPxPerSecond = maxPxPerSecond
            )
            if (velocity == 0f || seconds <= 0f) continue
            scrollBy(velocity * seconds)
            // Con el dedo quieto y el contenido moviéndose, la celda que hay
            // debajo cambia sola: sin reevaluar aquí, la banda se congelaría
            // en cuanto dejaran de llegar eventos de puntero.
            onFrame(nowNanos / 1_000_000L)
        }
    }
}

private fun DragSelectKind.hapticEvent(): HapticEvent = when (this) {
    DragSelectKind.Cells -> HapticEvent.CellCrossed
    DragSelectKind.Rail -> HapticEvent.RowCrossed
}

/**
 * Detector propio en vez de `detectDragGesturesAfterLongPress`, por dos
 * motivos que no son de estilo:
 *
 *  1. **Escucha en `PointerEventPass.Initial`.** Este modificador cuelga por
 *     FUERA del `scrollable` interno del Lazy. En el pase `Main` el orden es
 *     hijo→padre, o sea que el scroll vería cada movimiento antes que
 *     nosotros, acumularía su slop y arrancaría a media selección. En
 *     `Initial` el orden se invierte y consumimos primero. Es el mismo truco
 *     que usa `detectTimelinePinch`.
 *  2. **Arbitra sin consumir.** Durante la fase de decisión no se consume
 *     nada, así que un gesto que resulte ser un scroll (o un tap) llega
 *     intacto a quien le toca. Solo al comprometerse se empieza a consumir,
 *     y eso mata de paso el `onClick` del `combinedClickable` de la celda,
 *     que corre en `Main` más adentro.
 */
private suspend fun PointerInputScope.detectDragSelectGesture(
    config: () -> DragSelectConfig,
    selectionActive: () -> Boolean,
    railStartPx: () -> Float,
    onBegin: (Offset, DragSelectKind, Long) -> Boolean,
    onMove: (Offset, Long) -> Unit,
    onEnd: () -> Unit
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val cfg = config()
    val slop = viewConfiguration.touchSlop
    val railWidthPx = cfg.railWidth.toPx()
    val startedInRail = cfg.railEnabled &&
        railContains(down.position.x, railStartPx(), railWidthPx)

    var travelled = Offset.Zero
    var position = down.position
    var lastMillis = down.uptimeMillis
    var kind: DragSelectKind? = null
    var abandoned = false

    // ---- Fase 1: arbitraje. Ni un solo consume aquí. ----
    val settled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (kind == null && !abandoned) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // Un segundo dedo es un pinch de zoom del timeline: fuera.
            if (event.changes.count { it.pressed } > 1) {
                abandoned = true
                break
            }
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) {
                // Se ha levantado sin moverse: es un tap, y lo resuelve el
                // combinedClickable de la celda.
                abandoned = true
                break
            }
            travelled += change.positionChange()
            position = change.position
            lastMillis = change.uptimeMillis
            when {
                startedInRail && selectionActive() &&
                    isVerticalCommit(travelled.x, travelled.y, slop) ->
                    kind = DragSelectKind.Rail

                cfg.plainDragWhenActive && selectionActive() &&
                    isHorizontalCommit(travelled.x, travelled.y, slop, cfg.horizontalBias) ->
                    kind = DragSelectKind.Cells

                // Cualquier otro movimiento que pase el slop es del scroll.
                travelled.getDistance() > slop -> abandoned = true
            }
        }
    }
    if (abandoned) return@awaitEachGesture

    if (settled == null) {
        // Se agotó el temporizador con el dedo quieto: long-press.
        if (!cfg.enterOnLongPress || travelled.getDistance() > slop) return@awaitEachGesture
        kind = if (startedInRail && selectionActive()) DragSelectKind.Rail
        else DragSelectKind.Cells
    }
    val committed = kind ?: return@awaitEachGesture
    // El ancla es donde se POSÓ el dedo, no donde el gesto acabó de decidirse.
    // Con el arrastre horizontal, para cuando se confirma el dedo ya ha pasado
    // de celda, y anclar ahí dejaría la primera sin marcar.
    if (!onBegin(down.position, committed, lastMillis)) return@awaitEachGesture
    if (position != down.position) onMove(position, lastMillis)

    // ---- Fase 2: el gesto es nuestro. ----
    try {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            // Llega un segundo dedo: cerramos limpiamente y le dejamos el
            // gesto al pinch. Lo seleccionado hasta aquí se conserva.
            if (event.changes.count { it.pressed } > 1) break
            val change: PointerInputChange =
                event.changes.firstOrNull { it.id == down.id } ?: break
            // Consumir también el "up" es lo que evita que el tap fantasma
            // abra el visor al soltar.
            change.consume()
            if (!change.pressed) break
            onMove(change.position, change.uptimeMillis)
        }
    } finally {
        onEnd()
    }
}

/**
 * `withTimeoutOrNull` del ámbito de punteros: devuelve null si se agota el
 * plazo (que aquí significa "long-press") y Unit si el bloque terminó solo.
 */
private suspend fun <T> AwaitPointerEventScope.withTimeoutOrNull(
    timeMillis: Long,
    block: suspend AwaitPointerEventScope.() -> T
): T? = try {
    withTimeout(timeMillis) { block() }
} catch (_: PointerEventTimeoutCancellationException) {
    null
}
