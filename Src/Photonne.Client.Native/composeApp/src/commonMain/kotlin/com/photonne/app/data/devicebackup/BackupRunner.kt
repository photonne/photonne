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

        for ((index, media) in pending.withIndex()) {
            if (!shouldContinue()) break
            onProgress?.invoke(index + 1, pending.size, media.displayName)

            val uploadResult = runCatching { repository.upload(media) }
            uploadResult
                .onSuccess { response ->
                    val isDup = response.message.contains("already exists", ignoreCase = true)
                    if (isDup) skipped++ else uploaded++
                    repository.markUploaded(folder, media.uri, response.assetId.orEmpty())
                }
                .onFailure { error ->
                    failed++
                    val reason = error.toUploadFailureReason()
                    failuresByReason[reason] = (failuresByReason[reason] ?: 0) + 1
                    repository.markUploadFailed(
                        folder, media.uri, reason, error.toUploadFailureDetail()
                    )
                }
        }

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
