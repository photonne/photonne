package com.photonne.app.ui.album

import com.photonne.app.data.models.RemovedAlbumAsset
import kotlin.time.Instant

/**
 * Quitar del álbum esta cantidad de fotos o más de golpe pregunta antes. Menos
 * se quitan sin más, con Deshacer, como el resto de acciones reversibles: un
 * diálogo cada vez estorbaría, y "Quitadas recientemente" guarda lo demás.
 * El mismo umbral que la web.
 */
const val ALBUM_REMOVE_CONFIRM_FROM = 20

fun albumRemovalNeedsConfirm(count: Int): Boolean = count >= ALBUM_REMOVE_CONFIRM_FROM

/** Las fotos que una misma acción quitó del álbum. */
data class RemovalGroup(
    val removedAt: Instant,
    /** Null cuando esa cuenta ya no existe. */
    val removedBy: String?,
    val items: List<RemovedAlbumAsset>
)

/**
 * Lo quitado (el servidor lo da de lo más reciente a lo más antiguo) agrupado
 * por la acción que lo quitó: una petición marca todas sus fotos con el mismo
 * instante, así que mismo momento y misma persona es una sola acción.
 */
fun groupRemovals(items: List<RemovedAlbumAsset>): List<RemovalGroup> {
    val groups = mutableListOf<RemovalGroup>()
    for (item in items) {
        val last = groups.lastOrNull()
        if (last != null && last.removedAt == item.removedAt &&
            last.items.first().removedByUserId == item.removedByUserId
        ) {
            groups[groups.lastIndex] = last.copy(items = last.items + item)
        } else {
            groups += RemovalGroup(item.removedAt, item.removedByUsername, listOf(item))
        }
    }
    return groups
}
