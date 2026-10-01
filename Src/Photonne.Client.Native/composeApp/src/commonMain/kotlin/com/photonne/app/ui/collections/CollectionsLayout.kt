package com.photonne.app.ui.collections

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Las secciones de Colecciones que el usuario puede reordenar u ocultar. */
enum class CollectionSection { Memories, Pinned, People, Favorites, Albums, Folders, Explore, Map }

/**
 * Orden y visibilidad elegidos a mano. [customized] false = el usuario no ha
 * tocado nada y manda [adaptiveSectionOrder]; en cuanto reordena u oculta algo,
 * su orden se respeta tal cual (el adaptativo dejaría de moverlo por debajo).
 */
data class CollectionsLayout(
    val customized: Boolean = false,
    val order: List<CollectionSection> = CollectionSection.entries,
    val hidden: Set<CollectionSection> = emptySet(),
) {
    /** El orden que se pinta, con las secciones ocultas fuera. */
    fun visibleSections(adaptive: List<CollectionSection>): List<CollectionSection> =
        (if (customized) order else adaptive).filterNot { it in hidden }

    /** El orden completo que enseña la hoja de personalizar. */
    fun fullOrder(adaptive: List<CollectionSection>): List<CollectionSection> =
        if (customized) order else adaptive
}

/**
 * Orden por defecto según cómo usa cada cual la biblioteca: hay quien solo
 * hace álbumes, quien organiza por carpetas y quien solo ve carpetas
 * compartidas. Recuerdos va siempre arriba y los fijados justo después (son la
 * elección explícita del usuario); entre Álbumes y Carpetas va primero la que
 * de verdad se usa. Explorar (temas, escenas y objetos) y el Mapa, al final.
 *
 * Carpetas primero cuando no hay ningún álbum y sí carpetas, o cuando el
 * usuario no tiene carpetas propias pero sí compartidas (las consume, no las
 * organiza), o cuando hay fotos esperando en Para organizar.
 */
fun adaptiveSectionOrder(
    albumCount: Int,
    personalFolderCount: Int,
    sharedFolderCount: Int,
    organizePendingCount: Int = 0,
): List<CollectionSection> {
    val anyFolders = personalFolderCount + sharedFolderCount > 0
    val foldersFirst = anyFolders && (
        albumCount == 0 ||
            (personalFolderCount == 0 && sharedFolderCount > 0 && albumCount < sharedFolderCount) ||
            organizePendingCount > 0
        )
    val collections = if (foldersFirst) {
        listOf(CollectionSection.Folders, CollectionSection.Albums)
    } else {
        listOf(CollectionSection.Albums, CollectionSection.Folders)
    }
    return listOf(
        CollectionSection.Memories,
        CollectionSection.Pinned,
        CollectionSection.People,
        CollectionSection.Favorites,
    ) + collections + listOf(CollectionSection.Explore, CollectionSection.Map)
}

/**
 * Guarda la personalización de Colecciones en el dispositivo (Settings). Una
 * sección nueva en una versión futura se añade al final del orden guardado.
 */
class CollectionsLayoutStore(private val settings: Settings) {
    private val _layout = MutableStateFlow(load())
    val layout: StateFlow<CollectionsLayout> = _layout.asStateFlow()

    /**
     * Sube o baja [section] una posición partiendo del orden que se ve ahora
     * ([current], el adaptativo si aún no se había personalizado).
     */
    fun move(section: CollectionSection, delta: Int, current: List<CollectionSection>) {
        val order = current.toMutableList()
        val from = order.indexOf(section)
        val to = from + delta
        if (from < 0 || to !in order.indices) return
        order.removeAt(from)
        order.add(to, section)
        save(_layout.value.copy(customized = true, order = order))
    }

    fun setHidden(section: CollectionSection, hidden: Boolean, current: List<CollectionSection>) {
        val next = if (hidden) _layout.value.hidden + section else _layout.value.hidden - section
        save(_layout.value.copy(customized = true, order = current, hidden = next))
    }

    /** Vuelve al orden adaptativo con todo visible. */
    fun reset() = save(CollectionsLayout())

    private fun save(layout: CollectionsLayout) {
        settings.putBoolean(KEY_CUSTOMIZED, layout.customized)
        settings.putString(KEY_ORDER, layout.order.joinToString(",") { it.name })
        settings.putString(KEY_HIDDEN, layout.hidden.joinToString(",") { it.name })
        _layout.value = layout
    }

    private fun load(): CollectionsLayout {
        val customized = settings.getBoolean(KEY_CUSTOMIZED, false)
        val stored = parse(settings.getStringOrNull(KEY_ORDER))
        val order = stored + CollectionSection.entries.filterNot { it in stored }
        val hidden = parse(settings.getStringOrNull(KEY_HIDDEN)).toSet()
        return CollectionsLayout(customized = customized, order = order, hidden = hidden)
    }

    private fun parse(raw: String?): List<CollectionSection> =
        raw.orEmpty().split(',').mapNotNull { name ->
            CollectionSection.entries.firstOrNull { it.name == name.trim() }
        }.distinct()

    private companion object {
        const val KEY_CUSTOMIZED = "photonne.collections.customized"
        const val KEY_ORDER = "photonne.collections.order"
        const val KEY_HIDDEN = "photonne.collections.hidden"
    }
}
