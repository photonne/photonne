package com.photonne.app.data.devicebackup

/**
 * Per-platform handle to the OS scheduler that wakes the app up periodically
 * to run a backup batch in the background.
 *
 * - **Android**: WorkManager periodic work with NetworkType/charging constraints.
 * - **iOS**: BGTaskScheduler (BGProcessingTask). Real iOS support requires
 *   `Info.plist` registration + `AppDelegate` handler — see
 *   `docs/mobile-backup.md` for the manual steps.
 * - **Desktop**: no-op. Background sync on a desktop install doesn't make
 *   sense (the app's just a process, not a foreground/background OS citizen).
 *
 * The viewmodel calls [apply] every time the user toggles the auto-backup
 * preferences. The implementation decides whether to register, update or
 * cancel scheduled work based on [BackgroundSyncPreferences.enabled].
 */
interface BackgroundSyncScheduler {
    /**
     * Reconciles the OS-level schedule with [prefs]. Idempotent — calling
     * with the same prefs repeatedly does nothing extra. Calling with
     * `enabled = false` cancels any previously-scheduled work.
     */
    fun apply(prefs: BackgroundSyncPreferences)
}

/** Returns the platform's [BackgroundSyncScheduler]. Set up via DI. */
expect fun createBackgroundSyncScheduler(): BackgroundSyncScheduler
