package com.photonne.app.ui.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Atajos de teclado de las rejillas con selección: Ctrl/Cmd+A (seleccionar
 * todo) y Supr/Retroceso (papelera).
 *
 * Las pantallas registran aquí lo que ofrecen y el `Main.kt` de escritorio
 * traduce las teclas a [selectAll] / [delete], igual que hace con Escape y
 * `DesktopBackDispatcher`. En móvil nadie llama a esto, así que registrar no
 * cambia nada. Gana el último registro con acción que entró en composición:
 * un recuerdo con selección abierto encima de una pantalla con selección
 * recibe el atajo él.
 */
object SelectionShortcuts {
    internal class Entry(var onSelectAll: (() -> Unit)?, var onDelete: (() -> Unit)?)

    private val entries = mutableListOf<Entry>()

    internal fun register(entry: Entry) {
        entries.add(entry)
    }

    internal fun unregister(entry: Entry) {
        entries.remove(entry)
    }

    /** Ctrl/Cmd+A. False si la pantalla actual no ofrece "Seleccionar todo". */
    fun selectAll(): Boolean {
        val action = entries.lastOrNull { it.onSelectAll != null }?.onSelectAll ?: return false
        action()
        return true
    }

    /** Supr/Retroceso. False si no hay selección con papelera en pantalla. */
    fun delete(): Boolean {
        val action = entries.lastOrNull { it.onDelete != null }?.onDelete ?: return false
        action()
        return true
    }
}

/**
 * Registra los atajos mientras el llamador está en composición. `null` = la
 * pantalla no ofrece esa acción ahora mismo (sin selección, sin "Seleccionar
 * todo"…).
 */
@Composable
fun SelectionShortcutsHandler(
    onSelectAll: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val latestSelectAll by rememberUpdatedState(onSelectAll)
    val latestDelete by rememberUpdatedState(onDelete)
    val entry = remember { SelectionShortcuts.Entry(null, null) }
    SideEffect {
        entry.onSelectAll = if (onSelectAll != null) ({ latestSelectAll?.invoke() }) else null
        entry.onDelete = if (onDelete != null) ({ latestDelete?.invoke() }) else null
    }
    DisposableEffect(entry) {
        SelectionShortcuts.register(entry)
        onDispose { SelectionShortcuts.unregister(entry) }
    }
}
