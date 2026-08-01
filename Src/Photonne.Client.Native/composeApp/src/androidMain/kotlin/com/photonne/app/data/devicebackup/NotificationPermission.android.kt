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

    return remember(granted, needsGrant) {
        NotificationPermissionState(isGranted = granted) {
            // A second launch after a denial is a no-op on Android (the system
            // dialog never reappears), so guard on the cached verdict.
            if (needsGrant && !granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

private fun Context.hasPostNotifications(): Boolean =
    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
