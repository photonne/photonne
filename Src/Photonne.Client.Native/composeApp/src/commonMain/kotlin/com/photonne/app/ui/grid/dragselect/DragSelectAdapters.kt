package com.photonne.app.ui.grid.dragselect

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset

/**
 * `contentType` con el que `AssetGrid` marca sus celdas.
 *
 * Filtrar por él es más robusto que restar índices a ojo: las rejillas
 * intercalan cabeceras de ancho completo, tarjetas de subcarpeta y separadores
 * antes de los assets, y cualquier aritmética que los cuente se rompe en
 * cuanto alguien añade otro. De paso mejora el reciclado del LazyGrid, que
 * hasta ahora recibía las celdas sin tipo.
 */
internal const val AssetCellContentType = "photonne-asset-cell"

/**
 * Adaptador del `LazyVerticalGrid`. Aquí el Lazy ya publica la caja de cada
 * celda, así que sólo hay que traducirla al tipo puro y buscar.
 */
private class LazyGridDragSelectAdapter(
    private val gridState: LazyGridState,
    private val headerCount: () -> Int,
    private val idAt: (Int) -> String?
) : DragSelectAdapter {

    private fun visibleCells(): List<LazyGridItemInfo> =
        gridState.layoutInfo.visibleItemsInfo.filter { it.contentType == AssetCellContentType }

    private fun boundsOf(cells: List<LazyGridItemInfo>): List<GridItemBounds> =
        cells.map {
            GridItemBounds(
                index = it.index,
                left = it.offset.x,
                top = it.offset.y,
                width = it.size.width,
                height = it.size.height,
                row = it.row
            )
        }

    private fun itemSpaceY(position: Offset): Float =
        toItemSpaceY(position.y, gridState.layoutInfo.viewportStartOffset)

    override fun cellAt(position: Offset): DragSelectCell? {
        val hit = hitTestGridItems(
            items = boundsOf(visibleCells()),
            x = position.x,
            y = itemSpaceY(position)
        ) ?: return null
        val ordinal = hit.index - headerCount()
        val id = idAt(ordinal) ?: return null
        return DragSelectCell(ordinal, id)
    }

    override fun rowAt(position: Offset): DragSelectRow? = rowAtY(
        items = boundsOf(visibleCells()),
        y = itemSpaceY(position),
        headerCount = headerCount()
    )

    override fun idsAtOrdinal(ordinal: Int): List<String> = listOfNotNull(idAt(ordinal))

    override fun idsInRow(rowKey: Int): List<String> {
        // Las filas se resuelven aritméticamente y no a partir de lo visible:
        // un barrido rápido por el carril, o el auto-scroll, cruzan filas que
        // ya han salido de pantalla, y resolverlas como vacías las dejaría
        // sin marcar sin que se note.
        val columns = columnCount() ?: return emptyList()
        // La cabecera de ancho completo ocupa ella sola la fila 0.
        val cellRow = rowKey - if (headerCount() > 0) 1 else 0
        if (cellRow < 0) return emptyList()
        val first = cellRow * columns
        return (first until first + columns).mapNotNull(idAt)
    }

    /**
     * Columnas de la rejilla. `LazyGridLayoutInfo` no las publica, así que se
     * deducen de la distancia entre los primeros ítems de dos filas
     * consecutivas — exacto y ajeno a que la última fila esté a medias. El
     * máximo de columnas visibles solo sirve de respaldo, porque si lo único
     * en pantalla es una fila parcial se queda corto.
     */
    private fun columnCount(): Int? {
        val cells = visibleCells()
        if (cells.isEmpty()) return null
        val rowStarts = cells.filter { it.column == 0 }.sortedBy { it.row }
        for (i in 0 until rowStarts.size - 1) {
            val current = rowStarts[i]
            val next = rowStarts[i + 1]
            if (next.row == current.row + 1) return next.index - current.index
        }
        return cells.maxOf { it.column } + 1
    }
}

/**
 * [DragSelectAdapter] para una rejilla de assets sobre `LazyVerticalGrid`.
 *
 * [headerCount] son los ítems que el Lazy emite antes de las celdas, para
 * pasar de índice del Lazy a ordinal del asset. [idAt] devuelve null en los
 * ordinales que no se pueden seleccionar.
 */
@Composable
internal fun rememberLazyGridDragSelectAdapter(
    gridState: LazyGridState,
    headerCount: () -> Int,
    idAt: (ordinal: Int) -> String?
): DragSelectAdapter {
    val currentHeaderCount by rememberUpdatedState(headerCount)
    val currentIdAt by rememberUpdatedState(idAt)
    return remember(gridState) {
        LazyGridDragSelectAdapter(
            gridState = gridState,
            headerCount = { currentHeaderCount() },
            idAt = { currentIdAt(it) }
        )
    }
}
