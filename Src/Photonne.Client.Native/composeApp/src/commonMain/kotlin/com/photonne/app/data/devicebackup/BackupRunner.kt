package com.photonne.app.data.devicebackup

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
     * Lists [folder], checks each media's SHA-256 against the server, uploads
     * the ones that aren't already there. Stops if [shouldContinue] returns
     * false between items (lets the foreground caller cancel mid-batch).
     * Per-item progress is delivered through [onProgress] when set.
     */
    suspend fun runBackup(
        folder: DeviceFolderRef,
        onProgress: ((current: Int, total: Int, currentName: String) -> Unit)? = null,
        shouldContinue: () -> Boolean = { true }
    ): Result {
        val items = gallery.listMedia(folder)
        var uploaded = 0
        var skipped = 0
        var failed = 0
        val failuresByReason = mutableMapOf<UploadFailureReason, Int>()

        for ((index, media) in items.withIndex()) {
            if (!shouldContinue()) break
            onProgress?.invoke(index + 1, items.size, media.displayName)

            // Hash check first — if the server already has this file, we save
            // the upload bandwidth entirely.
            val syncCheck = runCatching { repository.checkSyncStatus(media) }.getOrNull()
            if (syncCheck != null && syncCheck.second is DeviceMediaSyncState.Synced) {
                skipped++
                continue
            }

            val uploadResult = runCatching { repository.upload(media) }
            uploadResult
                .onSuccess { response ->
                    val isDup = response.message.contains("already exists", ignoreCase = true)
                    if (isDup) skipped++ else uploaded++
                }
                .onFailure { error ->
                    failed++
                    val reason = error.toUploadFailureReason()
                    failuresByReason[reason] = (failuresByReason[reason] ?: 0) + 1
                }
        }

        return Result(
            total = items.size,
            uploaded = uploaded,
            skipped = skipped,
            failed = failed,
            failuresByReason = failuresByReason.toMap()
        )
    }
}
