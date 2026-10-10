package com.photonne.app.data.notifications

import com.photonne.app.data.auth.TokenStorage
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Programa (o cancela) la consulta periódica de notificaciones del servidor
 * con la app cerrada. Android: WorkManager cada 15 min. iOS y escritorio: no
 * hay (iOS solo tiene la BGProcessingTask del backup; ver roadmap).
 */
interface ActivityNotificationScheduler {
    /** True donde la consulta en segundo plano existe (el ajuste solo sale ahí). */
    val isSupported: Boolean get() = false

    fun apply(enabled: Boolean) {}
}

expect fun createActivityNotificationScheduler(): ActivityNotificationScheduler

/**
 * "Avisos de actividad": el interruptor de Ajustes (activado por defecto), el
 * último recuento de no leídas ya visto y la reconciliación con el
 * [ActivityNotificationScheduler]. Solo se programa con sesión: el cierre de
 * sesión (voluntario o por caducidad) lo cancela junto con el backup.
 */
class ActivityNotifications(
    private val settings: Settings,
    private val scheduler: ActivityNotificationScheduler,
    private val tokenStorage: TokenStorage,
) {
    private val _enabled = MutableStateFlow(settings.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val isSupported: Boolean get() = scheduler.isSupported

    fun setEnabled(value: Boolean) {
        settings.putBoolean(KEY_ENABLED, value)
        _enabled.value = value
        reconcile()
    }

    /** Arranque del proceso, login y cambio del ajuste. Idempotente. */
    fun reconcile() {
        runCatching { scheduler.apply(_enabled.value && tokenStorage.hasSession()) }
    }

    fun onSignedOut() {
        runCatching { scheduler.apply(false) }
        settings.remove(KEY_LAST_SEEN)
    }

    /**
     * Último recuento de no leídas ya conocido (por la app o por un aviso).
     * Null hasta la primera consulta de la sesión: esa solo fija la base, para
     * no avisar al entrar de lo que ya estaba ahí.
     */
    val lastSeenUnread: Int?
        get() = if (settings.hasKey(KEY_LAST_SEEN)) settings.getInt(KEY_LAST_SEEN, 0) else null

    /** La app (o el worker) ha visto [count] sin leer: lo que haya hasta ahí no se avisa. */
    fun markSeen(count: Int) {
        settings.putInt(KEY_LAST_SEEN, count)
    }

    private companion object {
        const val KEY_ENABLED = "photonne.activity_notifications.enabled"
        const val KEY_LAST_SEEN = "photonne.activity_notifications.last_seen_unread"
    }
}
