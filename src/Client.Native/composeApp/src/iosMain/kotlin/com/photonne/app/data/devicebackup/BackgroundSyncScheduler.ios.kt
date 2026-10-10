package com.photonne.app.data.devicebackup

/**
 * iOS scheduler backed by `BGTaskScheduler`. Reconciles the OS-level
 * request with the user's preferences by either submitting a new
 * [platform.BackgroundTasks.BGProcessingTaskRequest] or cancelling any
 * pending one.
 *
 * **Manual setup required in the Xcode project** (one-time per install):
 *   1. `Info.plist` →
 *      `BGTaskSchedulerPermittedIdentifiers = [ "com.photonne.app.backup" ]`
 *      and `UIBackgroundModes = [ "processing" ]`.
 *   2. `AppDelegate.swift` → register the BGTaskScheduler handler at launch
 *      that calls into [IosBackupBridge.runBackup] and rescheduling via
 *      [IosBackupBridge.scheduleNext] when done.
 *
 * Both pieces are already wired in the repo's `iosApp/`. If you regenerate
 * the iOS app, follow the same recipe.
 */
class BackgroundSyncSchedulerIos : BackgroundSyncScheduler {
    override fun apply(prefs: BackgroundSyncPreferences) {
        if (prefs.enabled) {
            // Idempotent: BGTaskScheduler replaces any pending request for
            // the same identifier, so callers can `apply` repeatedly without
            // double-scheduling.
            IosBackupBridge.scheduleNext()
        } else {
            IosBackupBridge.cancelPending()
        }
    }

    override fun requestImmediateSync(prefs: BackgroundSyncPreferences) {
        if (prefs.enabled) IosBackupBridge.scheduleImmediate()
    }
}

actual fun createBackgroundSyncScheduler(): BackgroundSyncScheduler =
    BackgroundSyncSchedulerIos()
