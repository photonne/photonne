package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** iOS backups report progress in-app only — no notification, no permission. */
@Composable
actual fun rememberNotificationPermission(): NotificationPermissionState =
    remember { NotificationPermissionState(isGranted = true) {} }
