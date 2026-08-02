package com.photonne.app.ui.grid

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.grid.dragselect.DragSelectConfig
import com.photonne.app.ui.grid.dragselect.DragSelectState
import com.photonne.app.ui.grid.dragselect.RowSelectRail
import com.photonne.app.ui.grid.dragselect.rememberDragSelectState
import com.photonne.app.ui.grid.dragselect.rememberLatchedDuringDrag
import com.photonne.app.ui.platform.backGestureEdgeInset
import com.photonne.app.ui.selection.SelectionPatch

/**
 * Todo lo que una pantalla con [AssetGrid] necesita para tener selección
 * gestual, en una sola pieza.
 *
 * Nueve pantallas comparten exactamente el mismo cableado — estado del
 * arrastre, inset del gesto de "atrás", carril armado, latch del cromo — y
 * repetirlo en cada una es donde se cuelan las diferencias silenciosas.
 */
@Stable
class AssetGridSelectionGestures internal constructor(
    val state: DragSelectState,
    val dragSelect: AssetGridDragSelect,
    internal val railInset: Dp
)

@Composable
fun rememberAssetGridSelectionGestures(
    onApplySelection: (SelectionPatch) -> Unit,
    enabled: Boolean = true
): AssetGridSelectionGestures {
    val state = rememberDragSelectState()
    val railInset = backGestureEdgeInset()
    val railStartPx = with(LocalDensity.current) { railInset.toPx() }
    return remember(state, railStartPx, enabled, onApplySelection) {
        AssetGridSelectionGestures(
            state = state,
            dragSelect = AssetGridDragSelect(
                state = state,
                onPatch = onApplySelection,
                enabled = enabled,
                railStartPx = railStartPx,
                config = DragSelectConfig.Default.copy(railEnabled = true)
            ),
            railInset = railInset
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

/** Carril de selección de filas, ya colocado en el margen correcto. */
@Composable
fun BoxScope.RowSelectRail(
    gestures: AssetGridSelectionGestures,
    visible: Boolean,
    modifier: Modifier = Modifier,
    reservedTop: Dp = 0.dp,
    reservedBottom: Dp = 0.dp
) {
    RowSelectRail(
        visible = visible,
        state = gestures.state,
        startInset = gestures.railInset,
        modifier = modifier,
        reservedTop = reservedTop,
        reservedBottom = reservedBottom
    )
}
