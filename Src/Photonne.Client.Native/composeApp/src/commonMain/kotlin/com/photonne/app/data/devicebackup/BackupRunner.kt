package com.photonne.app.data.devicebackup

import kotlinx.datetime.Clock

/**
 * Stateless full-folder sync routine. Used by:
 *   • [com.photonne.app.ui.devicebackup.DeviceBackupViewModel] when the user
 *     hits "sync" on the foreground screen (the viewmodel wraps this with
 *     UI state updates per item).
 *   • The platform background scheduler (Android WorkManager,
 *     iOS BGTaskScheduler) when the OS wakes the app for a periodic backup.
 *
 * Extracted so both call-sites share the same dedup-then-upload logic and
 * any future changes (parallel uploads, partial retry, etc.) land in one
 * place instead of two.
 */
class BackupRunner(
    private val repository: DeviceBackupRepository,
    private val gallery: DeviceGallery
) {

    data class Result(
        val total: Int,
        val uploaded: Int,
        val skipped: Int,
        val failed: Int,
        val failuresByReason: Map<UploadFailureReason, Int>
    )

    /**
     * Lists [folder], runs the ledger-backed incremental verification
     * (only new/changed files are hashed; the server is asked in bulk),
     * then uploads everything not already on the server. Stops if
     * [shouldContinue] returns false between items (lets the foreground
     * caller cancel mid-batch). Per-item progress is delivered through
     * [onProgress] when set.
     *
     * Because verification state persists in the ledger, a wake that gets
     * killed mid-pass resumes where it left off on the next one instead
     * of re-hashing the whole folder.
     */
    suspend fun runBackup(
        folder: DeviceFolderRef,
        onProgress: ((current: Int, total: Int, currentName: String) -> Unit)? = null,
        shouldContinue: () -> Boolean = { true },
        background: Boolean = true
    ): Result {
        val items = gallery.listMedia(folder)
        val states = repository.verifyAgainstServer(
            folder = folder,
            scanned = items,
            shouldContinue = shouldContinue
        )

        var uploaded = 0
        var skipped = 0
        var failed = 0
        val failuresByReason = mutableMapOf<UploadFailureReason, Int>()

        val pending = items.filter { states[it.uri] !is DeviceMediaSyncState.Synced }
        skipped += items.size - pending.size

        // Upload with bounded concurrency (see [uploadInParallel]). The
        // callbacks run under the helper's mutex, so these plain vars/map stay
        // consistent without locking here. Progress reports the COMPLETED count
        // — honest under parallelism, where there's no single "current index".
        uploadInParallel(
            pending = pending,
            // Background pass reports progress per completed file, not per byte.
            upload = { media, _ -> repository.upload(media) },
            shouldContinue = shouldContinue,
            onItemDone = { media, outcome, completed ->
                when (outcome) {
                    is UploadOutcome.Uploaded -> {
                        uploaded++
                        repository.markUploaded(folder, media.uri, outcome.assetId)
                    }
                    is UploadOutcome.Skipped -> {
                        skipped++
                        repository.markUploaded(folder, media.uri, outcome.assetId)
                    }
                    is UploadOutcome.Failed -> {
                        failed++
                        failuresByReason[outcome.reason] =
                            (failuresByReason[outcome.reason] ?: 0) + 1
                        repository.markUploadFailed(
                            folder, media.uri, outcome.reason, outcome.detail
                        )
                    }
                }
                onProgress?.invoke(completed, pending.size, media.displayName)
            }
        )

        val result = Result(
            total = items.size,
            uploaded = uploaded,
            skipped = skipped,
            failed = failed,
            failuresByReason = failuresByReason.toMap()
        )

        // Leave a visible trace so the status card can show "last sync ran at
        // X, uploaded Y" — the answer to "is background sync actually working?"
        repository.recordLastRun(
            LastBackupRun(
                finishedAtMillis = Clock.System.now().toEpochMilliseconds(),
                uploaded = result.uploaded,
                skipped = result.skipped,
                failed = result.failed,
                background = background
            )
        )

        return result
    }
}
