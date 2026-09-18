package com.photonne.app.data.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Mutación de assets confirmada por el servidor (punto 52 del roadmap).
 *
 * Lo emite [com.photonne.app.data.asset.AssetDetailRepository] justo después
 * de cada operación que cambia dónde vive un asset (archivo, papelera,
 * restauración, purga) o su estado de favorito, de forma que cualquier
 * ViewModel pueda reaccionar sin que App.kt tenga que conocerlo y llamarlo a
 * mano. Primer consumidor: Álbumes y Carpetas refrescan portadas y recuentos
 * tras acciones masivas, que antes quedaban desactualizados.
 *
 * La migración completa (que los ViewModels de listas dejen de recibir los
 * parches manuales de App.kt y dependan solo del bus) queda pendiente.
 */
sealed interface AssetMutation {
    /** Assets archivados o enviados a la papelera: salen de las listas normales. */
    data class Removed(val assetIds: List<String>) : AssetMutation

    /** Assets desarchivados o restaurados: vuelven a las listas normales. */
    data class Restored(val assetIds: List<String>) : AssetMutation

    /** Borrado definitivo. */
    data class Purged(val assetIds: List<String>) : AssetMutation

    /** Favorito conmutado en el servidor. */
    data class FavoriteChanged(val assetId: String, val isFavorite: Boolean) : AssetMutation

    /**
     * Operación global sin lista de ids (vaciar papelera, restaurar todo,
     * desarchivar todo): los suscriptores deben recargar.
     */
    data object AllChanged : AssetMutation
}

class AssetMutationBus {
    // extraBufferCapacity: tryEmit nunca debe suspender ni fallar por no haber
    // colectores lentos; perder un evento antiguo es aceptable (los
    // suscriptores recargan, no aplican deltas).
    private val _events = MutableSharedFlow<AssetMutation>(extraBufferCapacity = 32)
    val events: SharedFlow<AssetMutation> = _events

    fun emit(event: AssetMutation) {
        _events.tryEmit(event)
    }
}
