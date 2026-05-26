package com.photonne.app.data.devicebackup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

    companion object {
        private const val TAG = "BackupWorker"
        const val UNIQUE_WORK_NAME = "photonne.backup.periodic"
    }
}
