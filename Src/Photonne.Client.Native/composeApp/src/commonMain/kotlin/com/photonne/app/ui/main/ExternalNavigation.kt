package com.photonne.app.ui.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pantallas a las que se puede llegar desde fuera de la app (lote M4): una
 * notificación del sistema lleva el destino como extra del intent y la
 * plataforma lo publica aquí.
 */
enum class ExternalDestination {
    /** Pantalla de Backup (notificación de progreso). */
    Backup,

    /** Backup → Pendientes (notificación de fallos). */
    BackupPending;

    companion object {
        /** Extra del intent (Android) / userInfo (iOS) que lleva el destino. */
        const val EXTRA_KEY = "com.photonne.app.destination"

        fun fromKey(key: String?): ExternalDestination? =
            key?.let { k -> entries.firstOrNull { it.name == k } }
    }
}

/**
 * Buzón de un solo destino: la plataforma deja el último pedido y
 * `AuthenticatedApp` lo consume una vez. Si llega sin sesión espera al login;
 * un segundo toque antes de consumir el primero lo sustituye.
 */
object ExternalNavigation {
    private val _pending = MutableStateFlow<ExternalDestination?>(null)
    val pending: StateFlow<ExternalDestination?> = _pending.asStateFlow()

    fun request(destination: ExternalDestination) {
        _pending.value = destination
    }

    /** Devuelve el destino pendiente y vacía el buzón. */
    fun consume(): ExternalDestination? {
        val destination = _pending.value
        _pending.value = null
        return destination
    }
}
