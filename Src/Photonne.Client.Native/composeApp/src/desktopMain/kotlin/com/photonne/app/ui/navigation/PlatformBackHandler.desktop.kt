package com.photonne.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Escritorio no tiene gesto de sistema, pero sí teclado y ratón: Main.kt
 * traduce Escape, Alt+← y el botón "atrás" del ratón a [dispatch]. El registro
 * replica la semántica del BackHandler de Android: gana el último handler
 * habilitado que entró en composición.
 */
object DesktopBackDispatcher {
    internal class Entry(var enabled: Boolean, val onBack: () -> Unit)

    private val entries = mutableListOf<Entry>()

    internal fun register(entry: Entry) {
        entries.add(entry)
    }

    internal fun unregister(entry: Entry) {
        entries.remove(entry)
    }

    /** Ejecuta el back más prioritario; false si nadie puede manejarlo. */
    fun dispatch(): Boolean {
        val entry = entries.lastOrNull { it.enabled } ?: return false
        entry.onBack()
        return true
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = remember { DesktopBackDispatcher.Entry(enabled) { currentOnBack() } }
    SideEffect { entry.enabled = enabled }
    DisposableEffect(entry) {
        DesktopBackDispatcher.register(entry)
        onDispose { DesktopBackDispatcher.unregister(entry) }
    }
}
