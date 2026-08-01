package com.photonne.app.data.devicebackup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.photonne.app.resources.Res
import com.photonne.app.resources.backup_notification_channel
import com.photonne.app.resources.backup_notification_channel_progress
import com.photonne.app.resources.backup_notification_failures_text
import com.photonne.app.resources.backup_notification_failures_title
import com.photonne.app.resources.backup_notification_progress_text
import com.photonne.app.resources.backup_notification_progress_title
import com.photonne.app.resources.backup_notification_verifying_title
import com.photonne.app.resources.backup_status_stop
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.core.context.GlobalContext

/**
 * Runs the per-folder backup batch when WorkManager wakes us up. Resolves the
 * domain services from Koin's global context because WorkManager instantiates
 * Workers itself — there's no per-instance constructor injection without a
 * custom WorkerFactory, and the extra wiring isn't worth it for one Worker.
 *
 * The worker is a no-op when the user has no saved folder or has disabled
 * auto-backup since the last schedule (cheap idempotence — the scheduler may
 * race with the user toggling the preference).
 */
class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    /** True when this run was kicked by an explicit "Subir ahora" — it then
     *  promotes itself to a foreground service with a progress notification. */
    private val isForeground: Boolean
        get() = inputData.getBoolean(KEY_FOREGROUND, false)

    /** Ledger key holding the URIs this run should upload, when the request came
     *  from an explicit selection rather than "back up everything". */
    private val selectionKey: String?
        get() = inputData.getString(KEY_SELECTION)

    override suspend fun doWork(): Result {
        val koin = runCatching { GlobalContext.get() }.getOrNull()
        if (koin == null) {
            Log.w(TAG, "Koin not started; BackupWorker exiting without doing anything")
            return Result.success()
        }

        val stateStore: DeviceBackupStateStore = koin.get()
        // The periodic job honours the auto-backup opt-in; an explicit
        // foreground request is the user asking right now, so it only needs the
        // master backup switch on.
        val gateOpen = if (isForeground) {
            stateStore.isBackupEnabled()
        } else {
            stateStore.isAutoBackupEnabled() && stateStore.isBackupEnabled()
        }
        if (!gateOpen) {
            Log.i(TAG, "Backup gate closed (foreground=$isForeground); skipping run")
            return Result.success()
        }

        val folders = stateStore.savedFolders()
        if (folders.isEmpty()) {
            Log.i(TAG, "No saved folders; skipping run")
            return Result.success()
        }

        // Promote to a foreground service before the (potentially long) pass so
        // the OS keeps us alive and prioritized while the app is backgrounded.
        if (isForeground) {
            runCatching { setForeground(progressForegroundInfo(BackupPhase.Verifying, 0, 0)) }
                .onFailure { Log.w(TAG, "Could not enter foreground; running normally", it) }
        }

        val runner: BackupRunner = koin.get()
        val progress: BackupProgressBus = koin.get()
        // Consumed exactly once: a re-run must not resurrect an old selection.
        val selection = selectionKey?.let { key ->
            val ledger: BackupLedger = koin.get()
            ledger.takeMeta(key)?.lineSequence()?.filter { it.isNotBlank() }?.toSet()
        }
        if (selectionKey != null && selection.isNullOrEmpty()) {
            Log.i(TAG, "Selection payload missing or empty; skipping run")
            return Result.success()
        }
        return try {
            val outcome = coroutineScope {
                // Mirror the shared progress bus into the ongoing notification.
                // Cancelled as soon as the pass returns.
                val mirror = if (isForeground) launch { mirrorProgress(progress) } else null
                try {
                    runner.runBackup(
                        folders = folders,
                        // WorkManager flips isStopped on cancellation (the user
                        // tapping "Detener") and when the OS reclaims the worker.
                        shouldContinue = { !isStopped },
                        // A foreground pass is the user tapping "Subir ahora", so
                        // record it as a manual run, not a scheduled background one.
                        origin = if (isForeground) BackupOrigin.Foreground
                        else BackupOrigin.Background,
                        only = selection
                    )
                } finally {
                    mirror?.cancel()
                }
            }
            Log.i(
                TAG,
                "Backup done (foreground=$isForeground) — total=${outcome.total} " +
                    "uploaded=${outcome.uploaded} skipped=${outcome.skipped} " +
                    "failed=${outcome.failed}"
            )
            // Silent when everything went fine; one notification when files
            // were left behind, so "background sync quietly failing" stops
            // being invisible.
            if (outcome.failed > 0) {
                runCatching { notifyFailures(outcome.failed) }
                    .onFailure { Log.w(TAG, "Could not post failure notification", it) }
            }
            // Even partial failures shouldn't poison the schedule — the failed
            // items get retried via the server-side enrichment retry surface
            // or the next periodic run, and a Worker.Result.failure() would
            // disable the WorkManager backoff we don't want.
            Result.success()
        } catch (ex: Throwable) {
            Log.e(TAG, "Background backup crashed", ex)
            // retry() lets WorkManager re-run with its own backoff (10s base).
            Result.retry()
        }
    }

    /**
     * Fallback foreground info if WorkManager itself asks for it (e.g. when
     * re-running expedited work). The real promotion happens via the explicit
     * [setForeground] call in [doWork] once we know it's a foreground request.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo =
        progressForegroundInfo(BackupPhase.Verifying, 0, 0)

    /**
     * Follows the shared progress bus and re-posts the ongoing notification,
     * throttled to whole-percent steps so a fast batch doesn't spam the
     * notification manager. Runs until cancelled by [doWork].
     */
    private suspend fun mirrorProgress(bus: BackupProgressBus) {
        var lastPct = -1
        bus.activity.collect { activity ->
            if (activity == null) return@collect
            val done: Int
            val total: Int
            if (activity.phase == BackupPhase.Verifying) {
                done = activity.hashedCount
                total = activity.hashTotal
            } else {
                done = activity.done
                total = activity.total
            }
            val pct = if (total > 0) done * 100 / total else 0
            if (pct == lastPct) return@collect
            lastPct = pct
            runCatching { updateProgressNotification(activity.phase, done, total) }
        }
    }

    /** Bundles the progress notification into a [ForegroundInfo], tagging it as
     *  a DATA_SYNC foreground service on the API levels that require a type. */
    private suspend fun progressForegroundInfo(
        phase: BackupPhase,
        done: Int,
        total: Int
    ): ForegroundInfo {
        val notification = buildProgressNotification(phase, done, total)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    /** Re-posts the ongoing progress notification as files complete. */
    private suspend fun updateProgressNotification(phase: BackupPhase, done: Int, total: Int) {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(PROGRESS_NOTIFICATION_ID, buildProgressNotification(phase, done, total))
    }

    private suspend fun buildProgressNotification(
        phase: BackupPhase,
        done: Int,
        total: Int
    ): Notification {
        val context = applicationContext
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                PROGRESS_CHANNEL_ID,
                getString(Res.string.backup_notification_channel_progress),
                // Low importance: an ongoing progress bar shouldn't buzz/peek.
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val title = getString(
            if (phase == BackupPhase.Verifying) Res.string.backup_notification_verifying_title
            else Res.string.backup_notification_progress_title
        )
        val text = getString(Res.string.backup_notification_progress_text, done, total)
        // Stopping from the notification matters most exactly when the app
        // isn't on screen — which is the whole point of the foreground worker.
        val cancelAction = Notification.Action.Builder(
            null,
            getString(Res.string.backup_status_stop),
            WorkManager.getInstance(context).createCancelPendingIntent(id)
        ).build()
        return Notification.Builder(context, PROGRESS_CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(cancelAction)
            .apply { if (total > 0) setProgress(total, done, false) }
            .build()
    }

    private suspend fun notifyFailures(failed: Int) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "POST_NOTIFICATIONS not granted; skipping failure notification")
            return
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(Res.string.backup_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            android.app.PendingIntent.getActivity(
                context, 0, it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(getString(Res.string.backup_notification_failures_title))
            .setContentText(getString(Res.string.backup_notification_failures_text, failed))
            .setAutoCancel(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()
        manager.notify(FAILURE_NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "BackupWorker"
        const val UNIQUE_WORK_NAME = "photonne.backup.periodic"
        const val ONE_TIME_WORK_NAME = "photonne.backup.once"
        const val FOREGROUND_WORK_NAME = "photonne.backup.foreground"

        /** Input-data flag: run as a prioritized foreground service with a
         *  progress notification (set by an explicit "Subir ahora"). */
        const val KEY_FOREGROUND = "foreground"

        /** Input-data key naming the ledger row that holds the URIs to upload.
         *  Absent means "the whole folder". */
        const val KEY_SELECTION = "selectionKey"

        private const val CHANNEL_ID = "photonne.backup"
        private const val FAILURE_NOTIFICATION_ID = 4001
        private const val PROGRESS_CHANNEL_ID = "photonne.backup.progress"
        private const val PROGRESS_NOTIFICATION_ID = 4002
    }
}
