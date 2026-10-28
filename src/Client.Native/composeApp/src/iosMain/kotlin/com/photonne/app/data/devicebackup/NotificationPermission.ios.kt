package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS avisa con una notificación local cuando una copia en segundo plano deja
 * archivos sin subir ([IosBackupNotifications]), así que el permiso se pide al
 * activar el backup, como en Android 13+.
 *
 * Hasta que responde el sistema se da por concedido: así no parpadea el aviso
 * de "denegado" al abrir la pantalla.
 */
@Composable
actual fun rememberNotificationPermission(): NotificationPermissionState {
    var granted by remember { mutableStateOf(true) }
    // iOS solo enseña su diálogo una vez: denegado, la salida son los Ajustes.
    var canPrompt by remember { mutableStateOf(false) }

    // El permiso se puede cambiar en Ajustes y volver: se relee en cada ON_RESUME.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val refresh = {
            UNUserNotificationCenter.currentNotificationCenter()
                .getNotificationSettingsWithCompletionHandler { settings ->
                    val status = settings?.authorizationStatus
                    dispatch_async(dispatch_get_main_queue()) {
                        granted = status != null && isAllowedNotificationStatus(status)
                        canPrompt = status == UNAuthorizationStatusNotDetermined
                    }
                }
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(granted, canPrompt) {
        val openSettings: () -> Unit = {
            NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString)?.let { url ->
                UIApplication.sharedApplication.openURL(url)
            }
        }
        NotificationPermissionState(
            isGranted = granted,
            openSystemSettings = if (canPrompt) null else openSettings
        ) {
            if (granted || !canPrompt) return@NotificationPermissionState
            UNUserNotificationCenter.currentNotificationCenter()
                .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { ok, _ ->
                    dispatch_async(dispatch_get_main_queue()) {
                        granted = ok
                        canPrompt = false
                    }
                }
        }
    }
}
