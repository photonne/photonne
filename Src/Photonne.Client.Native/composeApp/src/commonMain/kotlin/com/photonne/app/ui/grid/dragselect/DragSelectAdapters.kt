package com.photonne.app.ui.grid.dragselect

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.photonne.app.ui.grid.TimelineRowEntry

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

    override fun idsAtOrdinal(ordinal: Int): List<String> = listOfNotNull(idAt(ordinal))
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

/**
 * Adaptador del timeline, donde cada ítem del `LazyColumn` es una FILA entera.
 * El Lazy solo resuelve el eje Y; la X se reconstruye contra las filas
 * empaquetadas, y sale exacta porque el packer trabaja con el mismo tamaño de
 * celda que se guarda en cada fila.
 *
 * Aquí "fila" es el índice de entrada en [rows], que incluye bandas de mes y
 * filas de esqueleto. Son inertes, así que la banda las atraviesa sin marcar
 * nada, y eso es justo lo que se quiere: pasar por encima de una cabecera no
 * debe cortar la selección.
 */
private class TimelineDragSelectAdapter(
    private val listState: LazyListState,
    private val rows: () -> List<TimelineRowEntry>,
    private val headerCount: () -> Int,
    private val spacingPx: Float,
    private val dpToPx: Float,
    private val idAt: (Int) -> String?
) : DragSelectAdapter {

    private fun entryIndexAt(y: Float): Int? {
        val info = listState.layoutInfo
        val itemY = toItemSpaceY(y, info.viewportStartOffset)
        val hit = info.visibleItemsInfo.firstOrNull {
            itemY >= it.offset && itemY < it.offset + it.size
        } ?: return null
        val index = hit.index - headerCount()
        return if (index >= 0) index else null
    }

    override fun cellAt(position: Offset): DragSelectCell? {
        val entryIndex = entryIndexAt(position.y) ?: return null
        val cell = hitTestRowEntry(
            rows = rows(),
            entryIndex = entryIndex,
            x = position.x,
            spacingPx = spacingPx,
            dpToPx = dpToPx
        ) ?: return null
        // idAt filtra además los ítems solo-locales, que no entran en las
        // operaciones en bloque del timeline.
        return if (idAt(cell.ordinal) == null) null else cell
    }

    override fun idsAtOrdinal(ordinal: Int): List<String> = listOfNotNull(idAt(ordinal))
}

/** [DragSelectAdapter] para el `LazyColumn` de filas del timeline. */
@Composable
internal fun rememberTimelineDragSelectAdapter(
    listState: LazyListState,
    rows: List<TimelineRowEntry>,
    headerCount: () -> Int,
    cellSpacing: Dp,
    idAt: (ordinal: Int) -> String?
): DragSelectAdapter {
    val density = LocalDensity.current
    val currentRows by rememberUpdatedState(rows)
    val currentHeaderCount by rememberUpdatedState(headerCount)
    val currentIdAt by rememberUpdatedState(idAt)
    return remember(listState, cellSpacing, density) {
        TimelineDragSelectAdapter(
            listState = listState,
            rows = { currentRows },
            headerCount = { currentHeaderCount() },
            spacingPx = with(density) { cellSpacing.toPx() },
            dpToPx = density.density,
            idAt = { currentIdAt(it) }
        )
    }
}
