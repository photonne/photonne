package com.photonne.app.ui.grid.dragselect

import com.photonne.app.ui.selection.SelectionPatch

/** Qué hace la banda con lo que va cubriendo. Lo decide el ancla, no cada celda. */
internal enum class DragSelectMode { Select, Deselect }

/**
 * Un arrastre de selección en curso.
 *
 * No guarda la selección: emite [SelectionPatch] y el ViewModel los aplica.
 * Eso mantiene toda la semántica aquí, sin estado compartido y sin Compose.
 *
 * Semántica (la de Google Fotos, y es la que hay que clavar):
 *
 *  - El **modo lo fija el ancla**: si la celda donde arrancó el gesto estaba
 *    sin marcar, la banda selecciona; si estaba marcada, deselecciona. Así el
 *    mismo gesto sirve para corregir una selección sin cambiar de herramienta.
 *  - Al **retroceder**, lo que sale de la banda vuelve a [baseSelected], su
 *    estado justo antes del gesto. Sin esto, pasarse de largo y volver
 *    borraría selecciones que el usuario ya tenía hechas de antes.
 *
 * La banda se recalcula entera desde el ancla en cada movimiento, así que un
 * frame en el que el hit-test falle (el dedo cae en la separación de 2dp) no
 * deja un agujero: simplemente no actualiza, y el siguiente frame lo corrige.
 *
 * [idsAt] devuelve los ids seleccionables de una posición: uno por celda en el
 * arrastre normal, la fila entera en el carril lateral. Devolver vacío marca
 * la posición como inerte — es lo que deja fuera los esqueletos del timeline,
 * los ítems solo-locales y las cabeceras.
 */
internal class DragSelectSession(
    val anchorOrdinal: Int,
    val mode: DragSelectMode,
    private val baseSelected: (String) -> Boolean,
    private val idsAt: (ordinal: Int) -> List<String>
) {
    var band: IntRange = anchorOrdinal..anchorOrdinal
        private set

    /** Parche que aplica el modo al ancla, al arrancar el gesto. */
    fun start(): SelectionPatch = patchFor(listOf(band), emptyList())

    /**
     * Lleva la banda hasta [ordinal]. Devuelve [SelectionPatch.Empty] cuando
     * el dedo sigue sobre la misma celda — es también la señal que usa el
     * gesto para no repetir el tic háptico.
     */
    fun moveTo(ordinal: Int): SelectionPatch {
        val next = bandOf(anchorOrdinal, ordinal)
        if (next == band) return SelectionPatch.Empty
        val diff = bandDiff(band, next)
        band = next
        return patchFor(diff.entering, diff.leaving)
    }

    private fun patchFor(
        entering: List<IntRange>,
        leaving: List<IntRange>
    ): SelectionPatch {
        val select = ArrayList<String>()
        val deselect = ArrayList<String>()
        for (range in entering) {
            for (ordinal in range) {
                for (id in idsAt(ordinal)) {
                    if (mode == DragSelectMode.Select) select += id else deselect += id
                }
            }
        }
        for (range in leaving) {
            for (ordinal in range) {
                for (id in idsAt(ordinal)) {
                    if (baseSelected(id)) select += id else deselect += id
                }
            }
        }
        return if (select.isEmpty() && deselect.isEmpty()) SelectionPatch.Empty
        else SelectionPatch(select, deselect)
    }
}

/**
 * Modo que corresponde a un ancla ya seleccionada por entero. Una fila a
 * medias cuenta como no seleccionada: el barrido la termina de marcar, que es
 * lo que espera quien la ve incompleta.
 */
internal fun modeForAnchor(
    anchorIds: List<String>,
    isSelected: (String) -> Boolean
): DragSelectMode =
    if (anchorIds.isNotEmpty() && anchorIds.all(isSelected)) DragSelectMode.Deselect
    else DragSelectMode.Select
