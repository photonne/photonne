package com.photonne.app.data.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.photonne.app.data.api.LocalReachabilityProbe
import com.photonne.app.data.api.ServerUrlStore
import com.photonne.app.data.auth.TokenStorage
import com.photonne.app.resources.Res
import com.photonne.app.resources.activity_notification_channel
import com.photonne.app.resources.activity_notification_title
import com.photonne.app.ui.main.ExternalDestination
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.koin.core.context.GlobalContext

/**
 * Cada ~15 min, con sesión: pregunta `unread-count` y, si ha subido respecto al
 * último recuento visto (por la app o por este mismo worker), publica una
 * notificación en el canal "Actividad" que abre Notificaciones. Con una sola
 * nueva enseña su título; con varias, el recuento y el título de la última.
 */
class ActivityNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val koin = runCatching { GlobalContext.get() }.getOrNull() ?: return Result.success()
        val tokenStorage: TokenStorage = koin.get()
        val activity: ActivityNotifications = koin.get()
        if (!tokenStorage.hasSession() || !activity.enabled.value) return Result.success()

        // Proceso sin UI: nadie ha lanzado la sonda LAN (ver BackupWorker).
        val urlStore: ServerUrlStore = koin.get()
        if (!urlStore.isLocalReachable() && !urlStore.getLocal().isNullOrEmpty()) {
            runCatching { koin.get<LocalReachabilityProbe>().runProbe() }
        }

        val repository: NotificationsRepository = koin.get()
        val count = try {
            repository.unreadCount()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Sin servidor ahora: la próxima pasada lo intenta otra vez.
            Log.i(TAG, "unread-count failed: ${t::class.simpleName}")
            return Result.success()
        }

        val lastSeen = activity.lastSeenUnread
        activity.markSeen(count)
        if (lastSeen == null || count <= lastSeen) return Result.success()

        val latest = runCatching {
            repository.list(page = 1, pageSize = 1, unreadOnly = true).items.firstOrNull()
        }.getOrNull()
        notify(newCount = count - lastSeen, latestTitle = latest?.title, latestMessage = latest?.message)
        return Result.success()
    }

    private suspend fun notify(newCount: Int, latestTitle: String?, latestMessage: String?) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "POST_NOTIFICATIONS not granted; skipping activity notification")
            return
        }
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(Res.string.activity_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val countTitle = getPluralString(Res.plurals.activity_notification_title, newCount, newCount)
        val (title, text) = when {
            newCount == 1 && !latestTitle.isNullOrBlank() -> latestTitle to latestMessage
            else -> countTitle to latestTitle
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .apply { if (!text.isNullOrBlank()) setContentText(text) }
            .setAutoCancel(true)
            .apply { openNotificationsIntent()?.let { setContentIntent(it) } }
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Mismo mecanismo que las notificaciones del backup (ExternalDestination). */
    private fun openNotificationsIntent(): PendingIntent? {
        val context = applicationContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return null
        launchIntent.putExtra(ExternalDestination.EXTRA_KEY, ExternalDestination.Notifications.name)
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        return PendingIntent.getActivity(
            context, ExternalDestination.Notifications.ordinal + 1, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ActivityNotifications"
        const val UNIQUE_WORK_NAME = "photonne.activity.periodic"
        private const val CHANNEL_ID = "photonne.activity"
        private const val NOTIFICATION_ID = 4101
    }
}
