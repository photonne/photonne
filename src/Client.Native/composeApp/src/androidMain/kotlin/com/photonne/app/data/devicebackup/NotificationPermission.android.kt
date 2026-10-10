package com.photonne.app.data.devicebackup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberNotificationPermission(): NotificationPermissionState {
    val context = LocalContext.current
    // Below API 33 the grant is implicit at install time.
    val needsGrant = Build.VERSION.SDK_INT >= 33
    var granted by remember {
        mutableStateOf(!needsGrant || context.hasPostNotifications())
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    // El usuario puede conceder o revocar el permiso en Ajustes y volver: el
    // verdict cacheado se relee en cada ON_RESUME (antes se quedaba viejo).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = !needsGrant || context.hasPostNotifications()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return remember(granted, needsGrant) {
        NotificationPermissionState(
            isGranted = granted,
            openSystemSettings = {
                // Tras una denegación el diálogo del sistema no reaparece;
                // la única salida es la pantalla de notificaciones de la app.
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                ).putExtra(
                    android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }
        ) {
            if (needsGrant && !granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private fun Context.hasPostNotifications(): Boolean =
    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
