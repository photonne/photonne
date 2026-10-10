package com.photonne.app.ui.actions

import com.photonne.app.data.models.TimelineItem

/**
 * Cuántas de las fotos [ids] rechazará el servidor al moverlas a la papelera.
 *
 * El servidor solo deja mandar a la papelera lo que vive en el espacio
 * personal de quien la pide o en el espacio compartido (ahí además mira el
 * CanDelete de la carpeta, que el cliente no conoce por foto); una foto de la
 * carpeta personal de otro miembro o de una biblioteca externa hace fallar el
 * lote ENTERO con un 403. Pasa sobre todo en álbumes compartidos, donde se
 * mezclan fotos de varios miembros.
 */
fun countUntrashable(items: List<TimelineItem>, ids: Set<String>, username: String): Int {
    if (ids.isEmpty()) return 0
    return items.count { it.id in ids && !isTrashableBy(it, username) }
}

/**
 * Seleccionadas que "Mover a carpeta" no puede tocar según lo que el cliente
 * sabe: las de una biblioteca externa (solo lectura). El servidor pide además
 * escritura en la carpeta de cada foto, que puede venir de una compartición;
 * eso no se adivina aquí y, si falla, el diálogo enseña su 403.
 */
fun countUnmovable(items: List<TimelineItem>, ids: Set<String>): Int {
    if (ids.isEmpty()) return 0
    return items.count { it.id in ids && it.isReadOnly }
}

private fun isTrashableBy(item: TimelineItem, username: String): Boolean {
    if (item.isReadOnly) return false
    val path = item.fullPath.replace('\\', '/')
    if (username.isNotBlank() && path.contains("/users/$username/", ignoreCase = true)) return true
    return path.startsWith("/assets/shared/", ignoreCase = true)
}
