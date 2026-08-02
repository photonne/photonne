package com.photonne.app.ui.grid.dragselect

import kotlin.math.abs

/**
 * Aritmética del arrastre en banda. Sin nada de Compose a propósito: todo lo
 * que decide QUÉ se selecciona vive aquí y se prueba sin levantar una UI, y la
 * capa de gesto se queda con lo que solo se puede comprobar en un dispositivo
 * (orden de los pases del puntero, consumo de eventos, hápticos).
 */

/**
 * Rango cubierto por el arrastre. Siempre contiene el ancla, así que crece
 * hacia arriba o hacia abajo pero nunca se despega de ella.
 */
internal fun bandOf(anchor: Int, current: Int): IntRange =
    if (current >= anchor) anchor..current else current..anchor

/** Qué ordinales entran y cuáles salen al pasar de una banda a la siguiente. */
internal data class BandDiff(
    val entering: List<IntRange>,
    val leaving: List<IntRange>
) {
    val isEmpty: Boolean get() = entering.isEmpty() && leaving.isEmpty()
}

/**
 * Diferencia entre dos bandas, en rangos y en O(1) — recorrer celda a celda
 * una banda de 3.000 fotos en cada frame de arrastre no es opción.
 *
 * Al cruzar el ancla la banda cambia de lado: lo que había al otro lado sale
 * en el mismo movimiento en que entra lo nuevo, y por eso el resultado puede
 * traer hasta dos rangos por lista.
 */
internal fun bandDiff(previous: IntRange, next: IntRange): BandDiff {
    if (previous == next) return BandDiff(emptyList(), emptyList())
    // Disjuntas: no puede pasar mientras ambas compartan el ancla, pero la
    // aritmética no debería depender de esa invariante.
    if (next.first > previous.last || next.last < previous.first) {
        return BandDiff(listOf(next), listOf(previous))
    }
    val entering = ArrayList<IntRange>(2)
    val leaving = ArrayList<IntRange>(2)
    if (next.first < previous.first) entering += next.first..(previous.first - 1)
    if (next.last > previous.last) entering += (previous.last + 1)..next.last
    if (previous.first < next.first) leaving += previous.first..(next.first - 1)
    if (previous.last > next.last) leaving += (next.last + 1)..previous.last
    return BandDiff(entering, leaving)
}

/**
 * Columna bajo [x] en una fila uniforme, o -1 si cae fuera.
 *
 * El hueco que sigue a una celda pertenece a esa celda: con 2dp de separación
 * un dedo cae en el hueco constantemente, y devolver -1 ahí dejaría agujeros
 * en la banda.
 */
internal fun columnAt(x: Float, cellSizePx: Float, spacingPx: Float, columns: Int): Int {
    val stride = cellSizePx + spacingPx
    if (stride <= 0f || columns <= 0 || x < 0f) return -1
    val column = (x / stride).toInt()
    return if (column < columns) column else -1
}

/**
 * Velocidad de auto-scroll en px/s (negativa hacia arriba) cuando el dedo
 * entra en la franja de [zonePx] pegada a cada borde. El avance es cuadrático
 * con la profundidad: arranca suave para que rozar el borde no dispare un
 * salto, y llega a [maxPxPerSecond] al fondo de la zona.
 *
 * [topEdge] y [bottomEdge] son los bordes ÚTILES, no los del viewport: las
 * barras de selección se superponen al contenido, y una zona que empezara
 * bajo ellas se dispararía con el dedo aún lejos del final visible.
 */
internal fun autoScrollVelocity(
    y: Float,
    topEdge: Float,
    bottomEdge: Float,
    zonePx: Float,
    maxPxPerSecond: Float
): Float {
    val height = bottomEdge - topEdge
    if (height <= 0f || zonePx <= 0f) return 0f
    // Con un viewport corto las dos zonas se solaparían; media altura las deja
    // tocándose justo en el centro, que sigue dando velocidad cero.
    val zone = zonePx.coerceAtMost(height / 2f)
    val topTrigger = topEdge + zone
    val bottomTrigger = bottomEdge - zone
    return when {
        y < topTrigger -> -eased((topTrigger - y) / zone) * maxPxPerSecond
        y > bottomTrigger -> eased((y - bottomTrigger) / zone) * maxPxPerSecond
        else -> 0f
    }
}

private fun eased(t: Float): Float {
    val clamped = t.coerceIn(0f, 1f)
    return clamped * clamped
}

/**
 * ¿El arrastre arranca claramente en horizontal? Es lo que separa "quiero
 * seleccionar en banda" de "quiero hacer scroll" cuando ya hay selección
 * activa y no se ha mantenido pulsado. [bias] pide que el componente
 * horizontal supere al vertical con margen, así que una diagonal se resuelve
 * como scroll.
 */
internal fun isHorizontalCommit(
    dx: Float,
    dy: Float,
    slopPx: Float,
    bias: Float = 1.5f
): Boolean = abs(dx) > slopPx && abs(dx) > abs(dy) * bias

/**
 * Y del puntero llevada al espacio en el que el Lazy publica los offsets de
 * sus ítems.
 *
 * `LazyListItemInfo.offset` / `LazyGridItemInfo.offset` no se miden desde el
 * borde visible sino desde el inicio del contenedor, y el viewport arranca en
 * `viewportStartOffset` (que es `-beforeContentPadding`). Con el
 * `contentPadding` de status bar + barra que usan casi todas las pantallas,
 * saltarse esta conversión desplaza el hit-test fila y media.
 */
internal fun toItemSpaceY(pointerY: Float, viewportStartOffset: Int): Float =
    pointerY + viewportStartOffset
