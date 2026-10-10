package com.photonne.app.data.devicebackup

/**
 * Desktop has no meaningful "background" lifecycle — when the JVM process
 * exits, there's nothing left to run periodic work. The toggle is exposed
 * in the shared UI for parity, but on desktop it's effectively a no-op
 * (the foreground "Sync now" button still works).
 */
class BackgroundSyncSchedulerDesktop : BackgroundSyncScheduler {
    override fun apply(prefs: BackgroundSyncPreferences) {
        // Intentionally empty.
    }
}

actual fun createBackgroundSyncScheduler(): BackgroundSyncScheduler =
    BackgroundSyncSchedulerDesktop()
