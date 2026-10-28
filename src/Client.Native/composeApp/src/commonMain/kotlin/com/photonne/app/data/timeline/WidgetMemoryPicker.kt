package com.photonne.app.data.timeline

import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.MemoryKind

/** Cuántos de los mejores recuerdos rotan en el widget cuando no hay "un día como hoy". */
internal const val WIDGET_ROTATION_SIZE = 10

/**
 * El recuerdo que enseña el widget de Recuerdos: el de "un día como hoy" si lo
 * hay; si no, uno de los mejores del feed ([feed] llega ordenado de mejor a
 * peor), distinto cada día. Solo cuentan los que tienen portada: el widget es
 * una foto.
 *
 * @param epochDay día local del dispositivo (días desde 1970-01-01); el mismo
 *   día da siempre el mismo recuerdo, aunque el widget se refresque varias veces.
 */
fun pickWidgetMemory(feed: List<Memory>, epochDay: Long): Memory? {
    val candidates = feed.filter { it.coverAssetId != null }
    if (candidates.isEmpty()) return null
    candidates.firstOrNull { MemoryKind.from(it.kind) == MemoryKind.OnThisDay }?.let { return it }
    val top = candidates.take(WIDGET_ROTATION_SIZE)
    val size = top.size.toLong()
    return top[(((epochDay % size) + size) % size).toInt()]
}
