package com.photonne.app.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.photonne.app.ui.grid.dragselect.DragSelectState
import com.photonne.app.ui.grid.dragselect.rememberDragSelectState
import com.photonne.app.ui.grid.dragselect.rememberLatchedDuringDrag
import com.photonne.app.ui.selection.SelectionPatch

/**
 * Lo que una pantalla con [AssetGrid] necesita para tener selección gestual.
 *
 * Nueve pantallas comparten el mismo cableado — estado del arrastre y latch del
 * cromo — y repetirlo en cada una es donde se cuelan las diferencias
 * silenciosas.
 */
@Stable
class AssetGridSelectionGestures internal constructor(
    val state: DragSelectState,
    val dragSelect: AssetGridDragSelect
)

@Composable
fun rememberAssetGridSelectionGestures(
    onApplySelection: (SelectionPatch) -> Unit,
    enabled: Boolean = true
): AssetGridSelectionGestures {
    val state = rememberDragSelectState()
    return remember(state, enabled, onApplySelection) {
        AssetGridSelectionGestures(
            state = state,
            dragSelect = AssetGridDragSelect(
                state = state,
                onPatch = onApplySelection,
                enabled = enabled
            )
        )
    }
}

/**
 * Estado de selección que ve el CROMO, congelado mientras dura un arrastre.
 *
 * Las pantallas cambian de cromo flotante a barras sólidas en cuanto hay algo
 * marcado, y eso mueve el `contentPadding` de la rejilla. A mitad de gesto el
 * contenido se deslizaría bajo el dedo, así que el cambio espera a que se
 * suelte.
 */
@Composable
fun AssetGridSelectionGestures.chromeSelectionActive(isSelectionActive: Boolean): Boolean =
    rememberLatchedDuringDrag(state, isSelectionActive)
