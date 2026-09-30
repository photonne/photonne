package com.photonne.app.ui.album

import com.photonne.app.data.models.AlbumSummary

/**
 * Qué acciones ofrece la cápsula de selección de la lista de álbumes para las
 * tarjetas elegidas. Una acción solo aparece si vale para TODAS: un lote no
 * puede fallar a medias por permisos que ya conocemos de antemano.
 *
 * Con una sola tarjeta coincide con las reglas de siempre (las del menú del
 * detalle); Editar y Miembros son de un álbum concreto y desaparecen al
 * seleccionar más de uno.
 */
data class AlbumSelectionActions(
    val canEdit: Boolean,
    val canManageMembers: Boolean,
    val canLeave: Boolean,
    val canDelete: Boolean,
    // Fijar es personal y vale con cualquier álbum que veo, pero solo con uno:
    // en bloque no está claro si fijar o desfijar cuando se mezclan.
    val canPin: Boolean = false,
)

fun albumSelectionActions(selected: List<AlbumSummary>): AlbumSelectionActions {
    if (selected.isEmpty()) {
        return AlbumSelectionActions(
            canEdit = false,
            canManageMembers = false,
            canLeave = false,
            canDelete = false
        )
    }
    val single = selected.singleOrNull()
    return AlbumSelectionActions(
        canEdit = single != null && (single.canWrite || single.isOwner),
        canManageMembers = single != null && (single.isOwner || single.canManagePermissions),
        // Salir es de álbumes que me han compartido: ninguno puede ser mío.
        canLeave = selected.all { !it.isOwner },
        canDelete = selected.all { it.isOwner || it.canDelete },
        canPin = single != null
    )
}
