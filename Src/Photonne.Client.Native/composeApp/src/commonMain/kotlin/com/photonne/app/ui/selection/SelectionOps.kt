package com.photonne.app.ui.selection

/**
 * Vocabulario compartido de la selección múltiple de assets.
 *
 * Cada pantalla con selección guarda su propio `selection: Set<String>` en su
 * UiState — diez ViewModels con el mismo campo. Antes cada uno llevaba además
 * su copia literal del toggle; estas operaciones puras son ese cuerpo común,
 * de modo que el ViewModel solo aporte el `_state.update { … }`.
 */

/**
 * Mutación en lote de un conjunto de selección: qué ids pasan a seleccionados
 * y cuáles a deseleccionados.
 *
 * El arrastre en banda emite un parche por frame del puntero en vez de N
 * llamadas a [toggled]: una banda que cruza 12 celdas supondría si no 12
 * `_state.update` — y 12 recomposiciones de la rejilla entera — en un mismo
 * frame.
 *
 * [select] y [deselect] son disjuntos por construcción (un id que entra en la
 * banda no puede salir de ella en el mismo movimiento); si aun así colisionan,
 * gana [deselect].
 */
data class SelectionPatch(
    val select: List<String> = emptyList(),
    val deselect: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = select.isEmpty() && deselect.isEmpty()

    companion object {
        val Empty = SelectionPatch()
    }
}

/** Añade [id] si falta, lo quita si está. El tap de toda la vida. */
fun Set<String>.toggled(id: String): Set<String> {
    val next = toMutableSet()
    if (!next.add(id)) next.remove(id)
    return next
}

/**
 * Aplica [patch] en una sola pasada. Devuelve `this` cuando el parche está
 * vacío para que un frame de arrastre que no cruza ninguna celda no genere
 * un estado nuevo.
 */
fun Set<String>.applying(patch: SelectionPatch): Set<String> {
    if (patch.isEmpty) return this
    val next = toMutableSet()
    next.addAll(patch.select)
    next.removeAll(patch.deselect)
    return next
}

/**
 * Marca o desmarca [ids] en bloque, sin alternar: lo que necesitan el carril
 * de filas y el checkbox de grupo, donde el modo lo decide el ancla y no cada
 * celda por separado.
 */
fun Set<String>.withSelection(ids: Collection<String>, selected: Boolean): Set<String> {
    if (ids.isEmpty()) return this
    val next = toMutableSet()
    if (selected) next.addAll(ids) else next.removeAll(ids)
    return next
}

/** "Seleccionar todo" como conmutador: limpia cuando [all] ya está entero. */
fun Set<String>.toggledAll(all: Collection<String>): Set<String> {
    val target = all.toSet()
    return if (this == target) emptySet() else target
}

/**
 * Cuántos de [ids] están seleccionados — base del checkbox tri-estado de la
 * cabecera de grupo (ninguno / parcial / todo).
 */
fun Set<String>.selectionStateOf(ids: Collection<String>): GroupSelectionState {
    if (ids.isEmpty()) return GroupSelectionState.None
    var selected = 0
    ids.forEach { if (it in this) selected++ }
    return when (selected) {
        0 -> GroupSelectionState.None
        ids.size -> GroupSelectionState.All
        else -> GroupSelectionState.Partial
    }
}

enum class GroupSelectionState { None, Partial, All }
