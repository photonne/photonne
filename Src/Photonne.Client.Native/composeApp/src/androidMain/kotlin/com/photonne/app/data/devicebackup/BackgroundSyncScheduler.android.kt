package com.photonne.app.data.devicebackup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * WorkManager-backed scheduler. Reconciles a single unique periodic
 * `BackupWorker` against the user's preferences:
 *
 * - When prefs.enabled is true → enqueue with KEEP policy (idempotent across
 *   app launches) using the constraints derived from requireWifi/charging.
 * - When prefs.enabled is false → cancel the unique work entry so nothing
 *   wakes us up anymore.
 *
 * Constraints can't be edited in-place; toggling them while keeping the same
 * work name uses REPLACE so the constraints actually take effect.
 */
class BackgroundSyncSchedulerAndroid(private val appContext: Context) : BackgroundSyncScheduler {

    private val workManager get() = WorkManager.getInstance(appContext)

    override fun apply(prefs: BackgroundSyncPreferences) {
        if (!prefs.enabled) {
            Log.i(TAG, "Cancelling periodic backup work")
            workManager.cancelUniqueWork(BackupWorker.UNIQUE_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresCharging(prefs.requireCharging)
            // Battery-not-low keeps the worker civil; the user can still
            // trigger a foreground sync from the UI any time.
            .setRequiresBatteryNotLow(true)
            .build()

        // 15-min minimum is a WorkManager constraint, not ours. The OS may
        // batch this with other work, so actual cadence is "≥ 15min when
        // constraints are satisfied".
        val request = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        // REPLACE so constraint changes from the UI actually take effect
        // (KEEP would silently keep the old constraints).
        workManager.enqueueUniquePeriodicWork(
            BackupWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.i(
            TAG,
            "Scheduled periodic backup — requireWifi=${prefs.requireWifi} " +
                "requireCharging=${prefs.requireCharging}"
        )
    }

    override fun requestImmediateSync(prefs: BackgroundSyncPreferences) {
        if (!prefs.enabled) return

        // Expedited one-shot so flipping the toggle starts backing up right
        // away instead of waiting for the end of the first 15-min periodic
        // window. Only the network constraint applies (expedited work cannot
        // carry a charging constraint, and the user is explicitly asking).
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()

        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            BackupWorker.ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.i(TAG, "Enqueued immediate one-time backup — requireWifi=${prefs.requireWifi}")
    }

    override val supportsForegroundBackup: Boolean get() = true

    override fun requestForegroundBackup(selectionKey: String?): Boolean {
        // Explicit user action ("Subir ahora" / "Sincronizar N"): no network or
        // charging constraints, and KEY_FOREGROUND tells the worker to promote
        // itself to a foreground service with a progress notification so the OS
        // keeps it at high priority and it survives the app being backgrounded.
        val input = if (selectionKey == null) {
            workDataOf(BackupWorker.KEY_FOREGROUND to true)
        } else {
            workDataOf(
                BackupWorker.KEY_FOREGROUND to true,
                BackupWorker.KEY_SELECTION to selectionKey
            )
        }
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(input)
            .build()

        workManager.enqueueUniqueWork(
            BackupWorker.FOREGROUND_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "Enqueued foreground (prioritized) backup — selection=$selectionKey")
        return true
    }

    override fun cancelForegroundBackup() {
        // Flips the worker's isStopped, which the runner polls between files.
        // Only the one-shots: cancelling the periodic unique work would also
        // unschedule every future run, which "stop this pass" must not do.
        workManager.cancelUniqueWork(BackupWorker.FOREGROUND_WORK_NAME)
        workManager.cancelUniqueWork(BackupWorker.ONE_TIME_WORK_NAME)
        Log.i(TAG, "Cancelled the running one-shot backup")
    }

    private companion object {
        const val TAG = "BackgroundSync"
    }
}

actual fun createBackgroundSyncScheduler(): BackgroundSyncScheduler {
    // Pull the Context that PhotonneApplication registered via `androidContext()`.
    val context: Context = GlobalContext.get().get()
    return BackgroundSyncSchedulerAndroid(context)
}
