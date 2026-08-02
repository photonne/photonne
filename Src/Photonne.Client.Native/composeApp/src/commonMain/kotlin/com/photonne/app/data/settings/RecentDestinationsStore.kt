package com.photonne.app.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Últimas carpetas a las que se ha movido algo, la más reciente primero.
 *
 * Organizar una bandeja es repetitivo por naturaleza: veinte lotes seguidos
 * acaban en tres o cuatro carpetas. Sin memoria, cada uno obliga a recorrer el
 * árbol entero otra vez, y esa es la fricción que más se nota cuando el
 * backlog es grande.
 *
 * Guarda solo ids: el nombre y la ruta se resuelven contra la lista de
 * carpetas al pintar, así que renombrar o mover una carpeta no deja aquí un
 * rótulo obsoleto — y una carpeta borrada simplemente deja de aparecer.
 */
class RecentDestinationsStore(private val settings: Settings) {

    private val _value = MutableStateFlow(load())
    val value: StateFlow<List<String>> = _value.asStateFlow()

    /** Sube [folderId] a lo alto de la lista, sin duplicarlo. */
    fun record(folderId: String) {
        if (folderId.isBlank()) return
        val next = (listOf(folderId) + _value.value.filterNot { it == folderId }).take(MAX)
        if (next == _value.value) return
        settings.putString(KEY, next.joinToString(SEPARATOR))
        _value.value = next
    }

    private fun load(): List<String> =
        settings.getStringOrNull(KEY)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.take(MAX)
            .orEmpty()

    private companion object {
        const val KEY = "organize.recent_destinations"

        /** Suficientes para cubrir las carpetas "de esta tanda" sin volverse otra lista que leer. */
        const val MAX = 4

        /** Los ids son UUID, así que la coma nunca aparece dentro de uno. */
        const val SEPARATOR = ","
    }
}
