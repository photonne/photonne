package com.photonne.app.ui.folder

import com.photonne.app.data.models.FolderSummary

/**
 * Qué acciones ofrece la cápsula de selección de la lista de carpetas para las
 * tarjetas elegidas. Una acción solo aparece si vale para TODAS.
 *
 * Una biblioteca externa es un espejo de solo lectura de una ruta del host,
 * pero el servidor sigue diciendo IsOwner a un admin sobre cualquier ruta
 * compartida: por eso lo destructivo se ata también al id de biblioteca.
 *
 * Con una sola tarjeta coincide con las reglas de siempre; Renombrar,
 * Miembros y la visibilidad en el timeline son de una carpeta concreta y
 * desaparecen al seleccionar más de una.
 */
data class FolderSelectionActions(
    val canRename: Boolean,
    val canManageMembers: Boolean,
    val canToggleTimeline: Boolean,
    val canMove: Boolean,
    val canDelete: Boolean,
)

fun folderSelectionActions(selected: List<FolderSummary>): FolderSelectionActions {
    if (selected.isEmpty()) {
        return FolderSelectionActions(
            canRename = false,
            canManageMembers = false,
            canToggleTimeline = false,
            canMove = false,
            canDelete = false
        )
    }
    val single = selected.singleOrNull()
    fun FolderSummary.isExternal() = externalLibraryId != null
    return FolderSelectionActions(
        canRename = single != null && single.canWrite && !single.isExternal(),
        canManageMembers = single != null && single.isOwner && single.isShared,
        canToggleTimeline = single != null && single.isShared && !single.isExternal(),
        canMove = selected.all { it.canWrite && !it.isExternal() },
        canDelete = selected.all { it.canDelete && !it.isExternal() }
    )
}

/**
 * Quita de la selección las carpetas que cuelgan de otra también
 * seleccionada: borrar o mover la de arriba ya arrastra su subárbol, y
 * procesar después la de abajo fallaría (ya no existe) o la sacaría de su
 * padre sin que nadie lo pidiera.
 */
fun topmostFolders(selected: List<FolderSummary>): List<FolderSummary> {
    val prefixes = selected.map { it.normalizedFolderPath() + "/" }
    return selected.filter { folder ->
        val path = folder.normalizedFolderPath()
        prefixes.none { prefix -> path.startsWith(prefix, ignoreCase = true) }
    }
}

private fun FolderSummary.normalizedFolderPath(): String =
    path.replace('\\', '/').trimEnd('/')
