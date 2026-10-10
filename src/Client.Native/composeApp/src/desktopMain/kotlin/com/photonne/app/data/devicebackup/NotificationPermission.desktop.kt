package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Desktop has no background backup pass, so nothing to notify about. */
@Composable
actual fun rememberNotificationPermission(): NotificationPermissionState =
    remember { NotificationPermissionState(isGranted = true) {} }
