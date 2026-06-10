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

    /**
     * Best-effort request to run one backup pass as soon as possible,
     * independent of the periodic schedule. Called when the user just
     * enabled auto-backup: a periodic WorkManager job fires at the END of
     * its first 15-min window (and only once the charging/Wi-Fi
     * constraints hold), so without this kick "I turned it on and nothing
     * happens" is the default experience. The kick honours the network
     * preference but deliberately ignores the charging one — the user is
     * actively asking for a sync right now.
     *
     * Default is a no-op for platforms without a meaningful "now"
     * (desktop) or where the OS owns the timing entirely.
     */
    fun requestImmediateSync(prefs: BackgroundSyncPreferences) {}

    /**
     * Runs a full backup pass NOW as a prioritized, OS-foreground task that
     * survives the app going to the background or the screen turning off, with
     * a persistent progress notification. This is what the "Subir ahora" /
     * "Subir todo" action triggers for big backlogs.
     *
     * Returns `true` when the platform took ownership (Android, via a
     * foreground WorkManager worker). The default returns `false` so callers
     * on iOS/Desktop — which have no equivalent foreground primitive — fall
     * back to running the pass in-process.
     */
    fun requestForegroundBackup(): Boolean = false
}

/** Returns the platform's [BackgroundSyncScheduler]. Set up via DI. */
expect fun createBackgroundSyncScheduler(): BackgroundSyncScheduler
