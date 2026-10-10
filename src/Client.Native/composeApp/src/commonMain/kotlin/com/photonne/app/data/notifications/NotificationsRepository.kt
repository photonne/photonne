package com.photonne.app.data.notifications

import com.photonne.app.data.api.PhotonneApi
import com.photonne.app.data.models.NotificationsPage

class NotificationsRepository(private val api: PhotonneApi) {

    suspend fun list(page: Int, pageSize: Int, unreadOnly: Boolean): NotificationsPage =
        api.getNotifications(page = page, pageSize = pageSize, unreadOnly = unreadOnly)

    suspend fun unreadCount(): Int = api.getUnreadNotificationCount()

    suspend fun markRead(id: String) = api.markNotificationRead(id)

    suspend fun markAllRead() = api.markAllNotificationsRead()
}
