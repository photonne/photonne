package com.photonne.app.ui.grid.dragselect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Por dónde se está arrastrando: sobre las celdas o por el carril lateral. */
internal enum class DragSelectKind { Cells, Rail }

/**
 * Estado observable del arrastre. Lo lee la pantalla para congelar su cromo
 * mientras dura el gesto y para resaltar el carril.
 */
@Stable
class DragSelectState {
    var isDragging: Boolean by mutableStateOf(false)
        internal set

    internal var kind: DragSelectKind? by mutableStateOf(null)

    /** Última posición del dedo, en coordenadas locales de la rejilla. */
    internal var pointer: Offset by mutableStateOf(Offset.Zero)

    /** true mientras el barrido va por el carril, para pintarlo activo. */
    val isRailActive: Boolean get() = isDragging && kind == DragSelectKind.Rail
}

@Composable
fun rememberDragSelectState(): DragSelectState = remember { DragSelectState() }

@Immutable
data class DragSelectConfig(
    /** Mantener pulsado arranca la banda, en cualquier modo. */
    val enterOnLongPress: Boolean = true,
    /**
     * Con selección YA activa, un arrastre claramente horizontal entra en
     * banda sin mantener pulsado. Solo tiene sentido ahí: fuera de selección
     * ese mismo gesto es el paso de pestaña.
     */
    val plainDragWhenActive: Boolean = false,
    /** Cuánto debe dominar la horizontal para no confundirse con un scroll. */
    val horizontalBias: Float = 1.5f,
    /** Barrido por el margen izquierdo para marcar filas enteras. */
    val railEnabled: Boolean = false,
    val railWidth: Dp = 32.dp,
    /** Franja de cada borde en la que el arrastre empieza a auto-scrollar. */
    val autoScrollEdge: Dp = 96.dp,
    val autoScrollMaxDpPerSecond: Float = 1800f,
    val haptics: Boolean = true
) {
    companion object {
        val Default = DragSelectConfig()
    }
}

/**
 * Lo que cada rejilla tiene que saber resolver para que el gesto sea común:
 * qué hay bajo el dedo y qué ids cuelgan de una posición. Todo en coordenadas
 * locales del nodo; la conversión al espacio de offsets del Lazy es cosa de
 * cada implementación.
 */
@Stable
internal interface DragSelectAdapter {
    fun cellAt(position: Offset): DragSelectCell?
    fun rowAt(position: Offset): DragSelectRow?

    /** Id del asset en [ordinal]; vacío si esa posición no es seleccionable. */
    fun idsAtOrdinal(ordinal: Int): List<String>

    /**
     * Ids de la fila [rowKey] entera. Ojo, el carril indexa por FILA y el
     * arrastre normal por ordinal de asset: son numeraciones distintas y
     * confundirlas selecciona cualquier cosa.
     */
    fun idsInRow(rowKey: Int): List<String>
}

/**
 * Mantiene [value] congelado en lo que valía al empezar el arrastre.
 *
 * Las pantallas cambian de cromo flotante a barras sólidas en cuanto hay algo
 * seleccionado, y eso mueve el `contentPadding` del grid unos 100 px. Si pasa
 * a mitad de gesto, el contenido se desliza bajo el dedo y la banda sigue
 * creciendo desde una celda que ya no está donde estaba. Con el cromo
 * congelado hasta soltar, el salto ocurre una vez y con el dedo fuera.
 */
@Composable
fun rememberLatchedDuringDrag(state: DragSelectState, value: Boolean): Boolean {
    var latched by remember { mutableStateOf(value) }
    // El gesto marca isDragging ANTES de aplicar su primer parche, así que
    // cuando este efecto ve la selección estrenarse ya está bloqueado y se
    // queda con el valor de antes del gesto.
    LaunchedEffect(state.isDragging, value) {
        if (!state.isDragging) latched = value
    }
    return if (state.isDragging) latched else value
}
