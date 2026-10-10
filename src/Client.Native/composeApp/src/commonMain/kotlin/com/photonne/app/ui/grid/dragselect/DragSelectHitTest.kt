package com.photonne.app.ui.grid.dragselect

import com.photonne.app.ui.grid.TimelineRowEntry

/**
 * Hit-test de las dos rejillas, en aritmética pura.
 *
 * La app tiene dos motores distintos y hay que cubrir los dos:
 *
 *  - **A**, `LazyVerticalGrid` (`AssetGrid`): el Lazy ya publica la caja de
 *    cada celda, así que basta con mapearla a [GridItemBounds] y buscar.
 *  - **B**, `LazyColumn` de filas preempaquetadas (`GroupedAssetGrid`, el
 *    timeline): el Lazy solo conoce el eje Y, porque cada ítem es una fila
 *    entera. La X se reconstruye — y sale exacta, no aproximada, porque
 *    `TimelineRow.rowHeightDp` ES el `cellSizeFor(...)` con el que el packer
 *    repartió el ancho.
 */

/** Celda alcanzada por el puntero. */
internal data class DragSelectCell(val ordinal: Int, val id: String)

// ---------- Motor A: LazyVerticalGrid ----------

/**
 * Caja de una celda en el espacio de offsets del Lazy (ver [toItemSpaceY]).
 * Es la frontera con Compose: `LazyGridItemInfo` se traduce a esto en el
 * adaptador y de aquí para dentro ya no hay tipos de UI.
 */
internal data class GridItemBounds(
    val index: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val row: Int
)

internal fun hitTestGridItems(
    items: List<GridItemBounds>,
    x: Float,
    y: Float
): GridItemBounds? = items.firstOrNull {
    x >= it.left && x < it.left + it.width &&
        y >= it.top && y < it.top + it.height
}


// ---------- Motor B: LazyColumn de filas (timeline) ----------

/**
 * Celda del timeline bajo [x] dentro de la fila [entryIndex] (ya descontada la
 * cabecera del Lazy).
 *
 * Devuelve null — posición inerte — para las bandas de mes, para las filas de
 * esqueleto de un bucket sin cargar, y para los huecos que una fila parcial
 * reserva al final para no estirar sus celdas.
 *
 * [dpToPx] es la densidad: el packer trabaja en dp y el puntero llega en px.
 */
internal fun hitTestRowEntry(
    rows: List<TimelineRowEntry>,
    entryIndex: Int,
    x: Float,
    spacingPx: Float,
    dpToPx: Float
): DragSelectCell? {
    val entry = rows.getOrNull(entryIndex) as? TimelineRowEntry.Row ?: return null
    val row = entry.row
    val column = columnAt(x, row.rowHeightDp * dpToPx, spacingPx, row.columns)
    if (column < 0 || column >= row.cells.size) return null
    val cell = row.cells[column]
    return DragSelectCell(ordinal = cell.index, id = cell.item.id)
}
