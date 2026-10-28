package com.photonne.app.data.devicebackup

import com.photonne.app.resources.Res
import com.photonne.app.resources.backup_notification_failures_text
import com.photonne.app.resources.backup_notification_failures_title
import com.photonne.app.ui.main.ExternalDestination
import com.photonne.app.ui.main.ExternalNavigation
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSLog
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

internal fun isAllowedNotificationStatus(status: UNAuthorizationStatus): Boolean =
    status == UNAuthorizationStatusAuthorized ||
        status == UNAuthorizationStatusProvisional ||
        status == UNAuthorizationStatusEphemeral

/**
 * Notificaciones locales del backup en iOS. Igual que en Android: silencio si
 * todo fue bien y un aviso cuando una pasada en segundo plano deja archivos sin
 * subir; tocarlo abre Backup → Pendientes ([IosNotificationRouter]). No hay
 * notificación de progreso: la BGProcessingTask corre sin que se vea.
 */
object IosBackupNotifications {
    /** Uno solo: un aviso nuevo sustituye al anterior en vez de apilarse. */
    private const val FAILURE_IDENTIFIER = "com.photonne.app.backup.failures"

    suspend fun notifyFailures(failed: Int) {
        if (failed <= 0 || !isAuthorized()) return
        val content = UNMutableNotificationContent().apply {
            title = getString(Res.string.backup_notification_failures_title)
            body = getPluralString(Res.plurals.backup_notification_failures_text, failed, failed)
            sound = UNNotificationSound.defaultSound
            userInfo = mapOf<Any?, Any?>(
                ExternalDestination.EXTRA_KEY to ExternalDestination.BackupPending.name
            )
        }
        val request = UNNotificationRequest.requestWithIdentifier(
            FAILURE_IDENTIFIER,
            content = content,
            trigger = null
        )
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { error ->
                if (error != null) NSLog("[IosBackup] failure notification not posted: ${error.localizedDescription}")
            }
    }

    private suspend fun isAuthorized(): Boolean = suspendCancellableCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                val status = settings?.authorizationStatus
                cont.resume(status != null && isAllowedNotificationStatus(status))
            }
    }
}

/**
 * Lo que hace la app con el toque en una notificación local: el `userInfo`
 * lleva el destino ([ExternalDestination.EXTRA_KEY]) y aquí se publica en el
 * buzón de [ExternalNavigation], como el extra del intent en Android. Lo llama
 * el `UNUserNotificationCenterDelegate` del `AppDelegate`.
 */
object IosNotificationRouter {
    /** Clave del `userInfo` con el destino. */
    val userInfoKey: String = ExternalDestination.EXTRA_KEY

    fun open(destinationKey: String?) {
        val destination = ExternalDestination.fromKey(destinationKey) ?: return
        ExternalNavigation.request(destination)
    }
}
