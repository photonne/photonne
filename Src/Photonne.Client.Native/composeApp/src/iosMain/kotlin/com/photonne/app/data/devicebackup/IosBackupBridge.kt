@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.photonne.app.data.devicebackup

import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.dateWithTimeIntervalSinceNow

/**
 * Public Kotlin surface that the iOS `AppDelegate` calls from inside the
 * `BGTaskScheduler` handler. Three responsibilities:
 *
 * 1. Expose the task identifier (single source of truth — must match the
 *    string in `Info.plist > BGTaskSchedulerPermittedIdentifiers`).
 * 2. Run a backup batch on demand and report success/failure so the handler
 *    can call `task.setTaskCompleted(success:)`.
 * 3. Submit the next [BGProcessingTaskRequest] so iOS keeps waking us up
 *    (BGTaskScheduler doesn't repeat tasks automatically — you have to
 *    re-submit after every run).
 *
 * Exposed as an `object` so Swift sees it as a singleton
 * (`IosBackupBridge.shared`).
 */
object IosBackupBridge : KoinComponent {

    /** Must match `BGTaskSchedulerPermittedIdentifiers` in Info.plist. */
    const val TASK_IDENTIFIER = "com.photonne.app.backup"

    /** Earliest the OS may wake us up — same 15-min floor as Android. */
    private const val MIN_DELAY_SECONDS = 15.0 * 60.0

    /**
     * Reads the user's auto-backup preferences. Swift uses this to decide
     * whether to bail (and to apply additional client-side network checks
     * if [BackgroundSyncPreferences.requireWifi] is true — iOS BGTask
     * doesn't have a Wi-Fi-only constraint).
     */
    fun currentPreferences(): BackgroundSyncPreferences {
        val store: DeviceBackupStateStore = get()
        return store.backgroundSyncPreferences()
    }

    /**
     * Runs the full per-folder backup. Suspends until done. Returns
     * `true` on success (including the "nothing to do" case), `false` on
     * an unrecoverable error so Swift can pass it to `setTaskCompleted`.
     */
    suspend fun runBackup(): Boolean {
        val store: DeviceBackupStateStore = get()

        if (!store.isAutoBackupEnabled() || !store.isBackupEnabled()) {
            NSLog("[IosBackup] auto-backup disabled — skipping run")
            return true
        }
        val folder = store.savedFolder()
        if (folder == null) {
            NSLog("[IosBackup] no saved folder — skipping run")
            return true
        }

        val runner: BackupRunner = get()
        return try {
            val outcome = runner.runBackup(folder)
            NSLog(
                "[IosBackup] done — total=${outcome.total} uploaded=${outcome.uploaded} " +
                    "skipped=${outcome.skipped} failed=${outcome.failed}"
            )
            true
        } catch (ex: Throwable) {
            NSLog("[IosBackup] crashed: ${ex.message}")
            false
        }
    }

    /**
     * Submits a [BGProcessingTaskRequest] so iOS schedules us again. Safe
     * to call multiple times — BGTaskScheduler simply replaces the
     * pending request for the same identifier.
     *
     * Call this:
     * - After enabling auto-backup in settings.
     * - At the end of every BGTask handler (so the next run is queued).
     */
    fun scheduleNext() {
        val prefs = currentPreferences()
        if (!prefs.enabled) {
            NSLog("[IosBackup] auto-backup off — not scheduling")
            return
        }

        val request = BGProcessingTaskRequest(TASK_IDENTIFIER).apply {
            requiresNetworkConnectivity = true
            // BGTask requires charging is the closest equivalent to the
            // Android "only while charging" constraint.
            requiresExternalPower = prefs.requireCharging
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(MIN_DELAY_SECONDS)
        }

        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
            NSLog(
                "[IosBackup] scheduled next BGProcessingTask — requireCharging=${prefs.requireCharging}"
            )
        } catch (ex: Throwable) {
            // Submission can fail when the user has restricted background
            // refresh, or when iOS is throttling us. Log and move on; the
            // next foreground prefs change will re-attempt.
            NSLog("[IosBackup] failed to schedule next task: ${ex.message}")
        }
    }

    /**
     * Best-effort "run as soon as you can": submitted with no earliest
     * date and without the external-power requirement, because the user
     * just enabled auto-backup and expects something to happen. iOS still
     * owns the actual timing (BGProcessingTask has no guaranteed "now"),
     * but an unconstrained request is the strongest nudge available. The
     * task handler reschedules the periodic request when it completes.
     */
    fun scheduleImmediate() {
        val prefs = currentPreferences()
        if (!prefs.enabled) {
            NSLog("[IosBackup] auto-backup off — not scheduling immediate run")
            return
        }

        val request = BGProcessingTaskRequest(TASK_IDENTIFIER).apply {
            requiresNetworkConnectivity = true
            requiresExternalPower = false
            earliestBeginDate = null
        }

        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
            NSLog("[IosBackup] requested immediate BGProcessingTask")
        } catch (ex: Throwable) {
            NSLog("[IosBackup] failed to request immediate task: ${ex.message}")
        }
    }

    /** Removes any pending BGTask request. Called when the user disables auto-backup. */
    fun cancelPending() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
        NSLog("[IosBackup] cancelled pending BGTask request")
    }
}
