package com.photonne.app.data.devicebackup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * Whether the app may post backup notifications right now, plus the way to ask
 * for it.
 *
 * Android 13+ requires a runtime grant for `POST_NOTIFICATIONS`; without it the
 * foreground worker still runs but its progress notification is silently
 * suppressed — so the permission has to be requested at the moment the user
 * opts into backups, not merely declared in the manifest.
 */
@Immutable
data class NotificationPermissionState(
    /** True when notifications can be posted (or the platform needs no grant). */
    val isGranted: Boolean,
    /** Prompts the user. No-op when already granted or not applicable. */
    val request: () -> Unit
)

/**
 * Platform hook for the notification permission. Android asks the OS; the
 * other targets report "granted" because they don't post backup notifications.
 */
@Composable
expect fun rememberNotificationPermission(): NotificationPermissionState
