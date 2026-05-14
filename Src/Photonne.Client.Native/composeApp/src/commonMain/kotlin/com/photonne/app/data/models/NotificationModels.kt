package com.photonne.app.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Mirrors `NotificationType` on the server (1 = JobCompleted, 2 = JobFailed,
 * 3 = ShareViewed). The server serializes the enum as its integer value, so
 * we keep the wire format as `Int` and map to a UI-friendly type only when
 * rendering.
 */
object NotificationKind {
    const val JobCompleted = 1
    const val JobFailed = 2
    const val ShareViewed = 3
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
    val actionUrl: String? = null
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
