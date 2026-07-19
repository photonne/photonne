package com.photonne.app.ui.grid

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.main.FloatingDatePill
import com.photonne.app.ui.main.ScrubberYearMarker
import com.photonne.app.ui.timeline.captureLocalDate
import dev.chrisbanes.haze.HazeState

/**
 * Cromo de scrubber COMPARTIDO para cualquier rejilla uniforme de fotos
 * ([AssetGrid]): scrubber de arrastre rápido + píldora de fecha centrada arriba
 * en scroll normal + botón "Volver arriba", exactamente como el timeline. Se
 * añade como hermano del [AssetGrid] dentro del Box de la pantalla; se
 * autooculta cuando hay pocas fotos (el scrubber tiene su propio umbral).
 *
 * @param showDates false para rejillas sin orden cronológico (p. ej. Buscador
 *   por relevancia): solo el mango de arrastre rápido, sin fechas ni años.
 * @param dateOrdered true si la lista va en orden de fecha monótono; solo
 *   entonces se pintan los años a lo largo del carril.
 * @param headerCount ítems que el grid emite antes de las celdas (p. ej. una
 *   portada); desplaza el mapeo fracción ↔ celda.
 */
@Composable
fun BoxScope.PhotoGridScrubberOverlay(
    gridState: LazyGridState,
    items: List<TimelineItem>,
    reservedTop: Dp,
    reservedBottom: Dp,
    selectionActive: Boolean,
    hazeState: HazeState?,
    headerCount: Int = 0,
    showDates: Boolean = true,
) {
    var isScrubbing by remember { mutableStateOf(false) }

    val labelForCellIndex: ((Int) -> String?)? = if (!showDates) {
        null
    } else {
        { i -> items.getOrNull(i)?.fileCreatedAt?.captureLocalDate()?.let(::formatLocalizedMonth) }
    }
    // SIN carril de años aquí: estas rejillas cargan por páginas, así que el
    // rango de fechas no se conoce entero de antemano y el carril saldría corto
    // y creciendo (leería como un fallo). El carril solo vive donde el rango es
    // completo: timeline (buckets) y álbum (todo cargado), con su propio scrubber.
    // Aquí se queda el mango de scroll rápido + la fecha central/del mango.

    AlbumGridScrubber(
        gridState = gridState,
        cellCount = items.size,
        headerCount = headerCount,
        yearMarkers = emptyList(),
        hazeState = hazeState,
        labelForCellIndex = labelForCellIndex,
        onDraggingChange = { isScrubbing = it },
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(top = reservedTop + 8.dp)
            .padding(bottom = reservedBottom),
    )

    if (showDates) {
        val topDateLabel by remember(items, headerCount) {
            derivedStateOf {
                items.getOrNull((gridState.firstVisibleItemIndex - headerCount).coerceAtLeast(0))
                    ?.fileCreatedAt?.captureLocalDate()?.let(::formatLocalizedMonth).orEmpty()
            }
        }
        FloatingDatePill(
            label = topDateLabel,
            isScrollInProgress = { gridState.isScrollInProgress },
            suppressed = isScrubbing || selectionActive,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = reservedTop + 8.dp),
        )
    }
}

/**
 * Un marcador por AÑO distinto (primera aparición), en la fracción 0..1 de su
 * índice de celda. Solo devuelve algo si la secuencia de años de la lista es
 * MONÓTONA (siempre subiendo o siempre bajando): así el carril sale en cualquier
 * pantalla cuyo orden sea cronológico (timeline, favoritos/persona por fecha, un
 * álbum que resulte ir en orden de fecha aunque esté en "orden de álbum") y se
 * oculta donde los años saltarían (álbum barajado, búsqueda por relevancia),
 * donde un carril desordenado leería como un fallo.
 */
internal fun buildAssetYearMarkers(items: List<TimelineItem>): List<ScrubberYearMarker> {
    val last = (items.size - 1).toFloat()
    if (last <= 0f) return emptyList()
    // firstIndexByYear preserva el orden de aparición (= orden de la lista).
    val firstIndexByYear = LinkedHashMap<Int, Int>()
    var prevYear: Int? = null
    var direction = 0 // +1 años subiendo, -1 bajando, 0 aún sin decidir
    items.forEachIndexed { i, item ->
        val year = item.fileCreatedAt?.captureLocalDate()?.year ?: return@forEachIndexed
        val prev = prevYear
        if (prev != null && year != prev) {
            val step = if (year > prev) 1 else -1
            if (direction == 0) direction = step
            else if (direction != step) return emptyList() // no monótono → sin carril
        }
        if (!firstIndexByYear.containsKey(year)) firstIndexByYear[year] = i
        prevYear = year
    }
    if (firstIndexByYear.size < 2) return emptyList()
    return firstIndexByYear.map { (year, i) ->
        ScrubberYearMarker(year.toString(), (i / last).coerceIn(0f, 1f))
    }
}
