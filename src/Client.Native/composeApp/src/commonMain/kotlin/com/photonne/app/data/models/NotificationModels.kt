package com.photonne.app.data.models

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Mirrors `NotificationType` on the server (1 = JobCompleted, 2 = JobFailed,
 * 3 = ShareViewed, 4 = SharedAssetsDeleted). The server serializes the enum as
 * its integer value, so we keep the wire format as `Int` and map to a
 * UI-friendly type only when rendering.
 */
object NotificationKind {
    const val JobCompleted = 1
    const val JobFailed = 2
    const val ShareViewed = 3
    const val SharedAssetsDeleted = 4
}

@Serializable
data class NotificationDto(
    val id: String,
    val type: Int,
    val title: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    @Serializable(with = FlexibleInstantSerializer::class)
    val createdAt: Instant,
    val actionUrl: String? = null,
    /**
     * Clave de agrupación del servidor: mientras una notificación sin leer con
     * la misma clave exista, las repeticiones se pliegan en ella (sube
     * [groupCount] y se reescribe el mensaje) en vez de crear filas nuevas.
     * Nula en los tipos que no agrupan.
     */
    val groupKey: String? = null,
    /** Cuántas repeticiones se plegaron; 1 si ninguna. El mensaje ya lo cuenta. */
    val groupCount: Int = 1
)

@Serializable
data class NotificationsPage(
    val items: List<NotificationDto> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 0,
    val unreadCount: Int = 0
)

@Serializable
data class UnreadNotificationCount(val count: Int = 0)
