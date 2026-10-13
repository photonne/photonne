package com.photonne.app.data.devicebackup

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
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
            workManager.cancelUniqueWork(BackupWorker.NEW_MEDIA_WORK_NAME)
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

        // KEEP: a pending trigger stays armed, and once it has fired and run,
        // finished unique work doesn't block a fresh enqueue. Constraint
        // changes reach the trigger on its next re-arm cycle — REPLACE here
        // would kill a triggered pass mid-upload every time the app starts.
        armNewMediaTrigger(prefs)
    }

    /**
     * One-shot request that fires when MediaStore reports new or changed
     * images/videos, so a photo just taken starts uploading within seconds
     * instead of waiting out a 15-min periodic window.
     *
     * Content-URI triggers only exist on one-time requests and are consumed
     * when they fire, so the request is re-armed after every triggered run —
     * [BackupWorker] does that on completion with APPEND_OR_REPLACE (it still
     * holds the unique name as "running" at that point, so KEEP would no-op).
     */
    fun armNewMediaTrigger(
        prefs: BackgroundSyncPreferences,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
    ) {
        if (!prefs.enabled) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.requireWifi) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresCharging(prefs.requireCharging)
            .setRequiresBatteryNotLow(true)
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            // Batch a burst (a photo session) into one pass instead of one
            // wake per shot; never sit on a change for more than two minutes.
            .setTriggerContentUpdateDelay(15, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(2, TimeUnit.MINUTES)
            .build()
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(BackupWorker.KEY_NEW_MEDIA to true))
            .build()
        workManager.enqueueUniqueWork(BackupWorker.NEW_MEDIA_WORK_NAME, policy, request)
        Log.i(TAG, "Armed new-media trigger (policy=$policy)")
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
            // WorkInfo doesn't expose input data: the tag is how a stop finds
            // the selections waiting in the chain (cancelForegroundBackup).
            .apply { selectionKey?.let { addTag(SELECTION_TAG_PREFIX + it) } }
            .build()

        // A full pass already running or queued covers another "Subir ahora"
        // (KEEP). A selection ("Subir estos") is queued behind it instead:
        // with KEEP it was dropped while this still answered true, so the
        // files never went up and nothing said so.
        workManager.enqueueUniqueWork(
            BackupWorker.FOREGROUND_WORK_NAME,
            if (selectionKey == null) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        Log.i(TAG, "Enqueued foreground (prioritized) backup — selection=$selectionKey")
        return true
    }

    override fun cancelForegroundBackup(keepQueuedSelections: Boolean) {
        if (!keepQueuedSelections) {
            cancelOneShots()
            return
        }
        // Cancelling a unique work cancels the work chained behind it too, so
        // a "Subir estos" waiting for this pass (BLOCKED) would go with it.
        // Its selection is still in the ledger — only a run that ends consumes
        // it — so it's queued again once the chain is gone. The running work
        // is never BLOCKED: whatever the user is stopping stays stopped.
        val infos = workManager.getWorkInfosForUniqueWork(BackupWorker.FOREGROUND_WORK_NAME)
        infos.addListener({
            val waiting = runCatching { infos.get() }.getOrDefault(emptyList())
                .filter { it.state == WorkInfo.State.BLOCKED }
                .flatMap { info ->
                    info.tags.filter { it.startsWith(SELECTION_TAG_PREFIX) }
                        .map { it.removePrefix(SELECTION_TAG_PREFIX) }
                }
            cancelOneShots()
            // Both go through WorkManager's serial executor: the chain is
            // cancelled first, so APPEND_OR_REPLACE starts a fresh one. Its
            // run waits for the stopped pass to let go of the pass lock.
            waiting.forEach { requestForegroundBackup(it) }
            if (waiting.isNotEmpty()) Log.i(TAG, "Re-queued ${waiting.size} waiting selection(s)")
        }, Runnable::run)
    }

    private fun cancelOneShots() {
        // Flips the worker's isStopped, which the runner polls between files.
        // Only the one-shots: cancelling the periodic unique work would also
        // unschedule every future run, which "stop this pass" must not do.
        workManager.cancelUniqueWork(BackupWorker.FOREGROUND_WORK_NAME)
        workManager.cancelUniqueWork(BackupWorker.ONE_TIME_WORK_NAME)
        Log.i(TAG, "Cancelled the running one-shot backup")
    }

    private companion object {
        const val TAG = "BackgroundSync"
        const val SELECTION_TAG_PREFIX = "backup-selection:"
    }
}

actual fun createBackgroundSyncScheduler(): BackgroundSyncScheduler {
    // Pull the Context that PhotonneApplication registered via `androidContext()`.
    val context: Context = GlobalContext.get().get()
    return BackgroundSyncSchedulerAndroid(context)
}
