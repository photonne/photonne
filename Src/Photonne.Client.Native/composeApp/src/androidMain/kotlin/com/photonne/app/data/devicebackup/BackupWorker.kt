package com.photonne.app.data.devicebackup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.photonne.app.resources.Res
import com.photonne.app.resources.backup_notification_channel
import com.photonne.app.resources.backup_notification_failures_text
import com.photonne.app.resources.backup_notification_failures_title
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

    override suspend fun doWork(): Result {
        val koin = runCatching { GlobalContext.get() }.getOrNull()
        if (koin == null) {
            Log.w(TAG, "Koin not started; BackupWorker exiting without doing anything")
            return Result.success()
        }

        val stateStore: DeviceBackupStateStore = koin.get()
        if (!stateStore.isAutoBackupEnabled() || !stateStore.isBackupEnabled()) {
            Log.i(TAG, "Auto-backup disabled; skipping run")
            return Result.success()
        }

        val folder = stateStore.savedFolder()
        if (folder == null) {
            Log.i(TAG, "No saved folder; skipping run")
            return Result.success()
        }

        val runner: BackupRunner = koin.get()
        return try {
            val outcome = runner.runBackup(folder)
            Log.i(
                TAG,
                "Background backup done — total=${outcome.total} uploaded=${outcome.uploaded} " +
                    "skipped=${outcome.skipped} failed=${outcome.failed}"
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
        private const val CHANNEL_ID = "photonne.backup"
        private const val FAILURE_NOTIFICATION_ID = 4001
    }
}
