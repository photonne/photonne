package com.photonne.app.data.devicebackup

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock

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
 *
 * Progress is reported exclusively through [progress]: it's a process-wide
 * [BackupProgressBus], so whoever is on screen sees the pass regardless of
 * which of the three entry points started it.
 */
/**
 * Which of [items] a pass should actually try to upload.
 *
 * Excluded: files already on the server, files the user explicitly skipped,
 * and — unless [retryPermanentFailures] — files whose last failure the server
 * will never accept (quota exceeded, too large, forbidden). Those used to be
 * re-queued on every single wake, spending three backoff retries each time to
 * fail exactly the same way.
 *
 * Pure on purpose: it's the one decision in the pass worth testing directly.
 */
internal fun selectUploadCandidates(
    items: List<DeviceMedia>,
    states: Map<String, DeviceMediaSyncState>,
    retryPermanentFailures: Boolean
): List<DeviceMedia> = items.filter { media ->
    when (val state = states[media.uri]) {
        is DeviceMediaSyncState.Synced, DeviceMediaSyncState.Ignored -> false
        is DeviceMediaSyncState.Failed ->
            // Cuota llena y sesión caducada no son del archivo (lote L11): si
            // la pasada llega aquí es que el bloqueo ya se levantó.
            retryPermanentFailures || state.reason.isRetryable || state.reason.blocksPass
        else -> true
    }
}

class BackupRunner(
    private val repository: DeviceBackupRepository,
    private val gallery: DeviceGallery,
    private val progress: BackupProgressBus
) {

    data class Result(
        val total: Int,
        val uploaded: Int,
        val skipped: Int,
        val failed: Int,
        val failuresByReason: Map<UploadFailureReason, Int>,
        /** La pasada se cortó por la cuenta (cuota llena, sesión caducada). */
        val blockedBy: UploadFailureReason? = null
    )

    /**
     * Lists [folder], runs the ledger-backed incremental verification
     * (only new/changed files are hashed; the server is asked in bulk),
     * then uploads everything not already on the server. Stops if
     * [shouldContinue] returns false between items (lets the caller cancel
     * mid-batch).
     *
     * Because verification state persists in the ledger, a wake that gets
     * killed mid-pass resumes where it left off on the next one instead
     * of re-hashing the whole folder.
     */
    suspend fun runBackup(
        folders: List<DeviceFolderRef>,
        shouldContinue: () -> Boolean = { true },
        origin: BackupOrigin = BackupOrigin.Background,
        only: Set<String>? = null
    ): Result {
        // Lote L11: una pasada programada no insiste mientras la cuenta siga
        // igual que cuando se cortó (misma sesión, misma cuota). Las que pide
        // el usuario sí prueban: es él quien dice que ya lo ha resuelto.
        if (origin == BackupOrigin.Background && repository.passBlockHolds()) {
            return Result(
                total = 0, uploaded = 0, skipped = 0, failed = 0,
                failuresByReason = emptyMap(),
                blockedBy = repository.passBlock()?.reason
            )
        }
        return runPass(folders, shouldContinue, origin, only)
    }

    private suspend fun runPass(
        folders: List<DeviceFolderRef>,
        shouldContinue: () -> Boolean,
        origin: BackupOrigin,
        only: Set<String>?
    ): Result = try {
        progress.start(BackupPhase.Verifying, origin)

        var total = 0
        var uploaded = 0
        var skipped = 0
        var failed = 0
        val failuresByReason = mutableMapOf<UploadFailureReason, Int>()

        // Verify every folder first, then upload the union: one wide upload
        // pool across folders beats draining them one at a time, and the
        // progress bar counts the whole job instead of restarting per folder.
        val pending = mutableListOf<DeviceMedia>()
        val folderByUri = mutableMapOf<String, DeviceFolderRef>()
        val listed = mutableListOf<DeviceMedia>()
        var verifiedHashes = 0
        // A user-triggered pass always re-asks the server about everything; a
        // scheduled one only does that sweep about once a day (see
        // [DeviceBackupRepository.consumeFullReconcileDue]).
        val fullReconcile = origin != BackupOrigin.Background ||
            repository.consumeFullReconcileDue(Clock.System.now().toEpochMilliseconds())
        for (folder in folders) {
            if (!shouldContinue()) break
            // iOS revokes a BGTask's budget by cancelling the job; without an
            // explicit check the suspend chain would keep going past the
            // expiration handler.
            currentCoroutineContext().ensureActive()
            val items = gallery.listMedia(folder)
            total += items.size
            items.forEach { folderByUri[it.uri] = folder }
            listed += items

            val folderPending = if (only != null) {
                // An explicit "upload these": the user already picked the files,
                // so skip the folder-wide verification (minutes of hashing on a
                // big library) and go straight to uploading. The server dedups
                // by SHA-256 anyway, so a redundant upload can't duplicate an
                // asset.
                items.filter { it.uri in only }
            } else {
                val alreadyHashed = verifiedHashes
                val states = repository.verifyAgainstServer(
                    folder = folder,
                    scanned = items,
                    onProgress = { verification ->
                        progress.update {
                            it.copy(
                                hashedCount = alreadyHashed + verification.hashedCount,
                                hashTotal = alreadyHashed + verification.hashTotal
                            )
                        }
                    },
                    shouldContinue = shouldContinue,
                    fullReconcile = fullReconcile
                )
                verifiedHashes = progress.activity.value?.hashedCount ?: alreadyHashed
                selectUploadCandidates(
                    items = items,
                    states = states,
                    // A scheduled pass shouldn't burn radio and battery every 15
                    // minutes on files the server will never accept; an explicit
                    // one is the user asking, so it retries everything.
                    retryPermanentFailures = origin != BackupOrigin.Background
                )
            }
            // Files the verification proved were already on the server. A
            // targeted "upload these" pass didn't examine the rest of the
            // folder, so it has no business counting them as skipped.
            if (only == null) skipped += items.size - folderPending.size
            pending += folderPending
        }

        // A scheduled pass may only get JobScheduler's ~10-minute window (FGS
        // promotion can be denied while the app is backgrounded), so upload
        // the small files first: more items land per window and one giant
        // video can't starve everything queued behind it.
        if (origin == BackupOrigin.Background) pending.sortBy { it.sizeBytes }

        progress.update {
            it.copy(
                phase = BackupPhase.Uploading,
                total = pending.size,
                bytesTotal = pending.sumOf { media -> media.sizeBytes }
            )
        }

        // Upload with bounded concurrency (see [uploadInParallel]). The
        // callbacks run under the helper's mutex, so these plain vars/map stay
        // consistent without locking here.
        // Lote L11: con la cuota llena o la sesión caducada el siguiente
        // archivo recibirá el mismo rechazo; la pasada se corta al primero
        // (los que ya estaban en vuelo terminan).
        var blockedBy: UploadFailureReason? = null
        var landed = 0
        uploadInParallel(
            pending = pending,
            concurrency = repository.uploadConcurrency(),
            upload = { media, report -> repository.upload(media, onProgress = report) },
            shouldContinue = { blockedBy == null && shouldContinue() },
            onItemStart = { media ->
                progress.update {
                    it.copy(
                        inFlight = it.inFlight + 1,
                        currentName = media.displayName,
                        inFlightItems = it.inFlightItems + (media.uri to 0f)
                    )
                }
            },
            onItemProgress = { media, fraction ->
                // Whole-percent steps only: a fast upload would otherwise emit a
                // new state on every 64 KiB chunk.
                val pct = (fraction * 100).toInt()
                val previous = progress.activity.value?.inFlightItems?.get(media.uri)
                if (previous == null || (previous * 100).toInt() != pct) {
                    progress.update {
                        it.copy(inFlightItems = it.inFlightItems + (media.uri to fraction))
                    }
                }
            },
            onItemDone = { media, outcome, _ ->
                // Ledger rows are keyed by folder, so each result goes back to
                // the folder the file was listed from.
                val owner = folderByUri.getValue(media.uri)
                when (outcome) {
                    is UploadOutcome.Uploaded -> {
                        uploaded++
                        landed++
                        repository.markUploaded(owner.uri, media.uri, outcome.assetId)
                    }
                    is UploadOutcome.Skipped -> {
                        skipped++
                        landed++
                        repository.markUploaded(owner.uri, media.uri, outcome.assetId)
                    }
                    is UploadOutcome.Failed -> {
                        failed++
                        if (outcome.reason.blocksPass && blockedBy == null) blockedBy = outcome.reason
                        failuresByReason[outcome.reason] =
                            (failuresByReason[outcome.reason] ?: 0) + 1
                        repository.markUploadFailed(
                            owner.uri, media.uri, outcome.reason, outcome.detail
                        )
                    }
                }
                progress.update {
                    it.copy(
                        completed = uploaded,
                        // Only the dedup skips of THIS phase belong to the bar;
                        // the already-synced files never entered `pending`.
                        skipped = it.skipped + if (outcome is UploadOutcome.Skipped) 1 else 0,
                        failed = failed,
                        inFlight = (it.inFlight - 1).coerceAtLeast(0),
                        bytesDone = it.bytesDone + media.sizeBytes,
                        inFlightItems = it.inFlightItems - media.uri
                    )
                }
                // The ledger just changed; let any open screen refresh its
                // verdicts instead of waiting for the pass to end.
                progress.bumpLedger()
            }
        )

        // Live Photos already on the server without their motion clip (backed
        // up before the clip travelled with the still, or its upload failed).
        // Rides the full reconcile: one lookup per 500 Live Photos.
        if (only == null && fullReconcile && blockedBy == null && shouldContinue()) {
            val states = repository.syncStatesFor(folders)
            repository.repairMotionClips(
                synced = listed.mapNotNull { media ->
                    (states[media.uri] as? DeviceMediaSyncState.Synced)?.let { media to it.assetId }
                },
                shouldContinue = shouldContinue,
                onProgress = { done, clips ->
                    progress.update {
                        it.copy(
                            phase = BackupPhase.RepairingMotionClips,
                            motionClipsDone = done,
                            motionClipsTotal = clips
                        )
                    }
                }
            )
        }

        val result = Result(
            total = total,
            uploaded = uploaded,
            skipped = skipped,
            failed = failed,
            failuresByReason = failuresByReason.toMap(),
            blockedBy = blockedBy
        )

        // El bloqueo se anota al cortarse y se olvida en cuanto una subida
        // entra (la cuenta vuelve a aceptar archivos).
        val blocker = blockedBy
        if (blocker != null) {
            repository.recordPassBlock(blocker, Clock.System.now().toEpochMilliseconds())
        } else if (landed > 0) {
            repository.clearPassBlock()
        }

        // Leave a visible trace so the status card can show "last sync ran at
        // X, uploaded Y" — the answer to "is background sync actually working?"
        repository.recordLastRun(
            LastBackupRun(
                finishedAtMillis = Clock.System.now().toEpochMilliseconds(),
                uploaded = result.uploaded,
                skipped = result.skipped,
                failed = result.failed,
                background = origin == BackupOrigin.Background
            )
        )

        result
    } finally {
        // Always clear, including on cancellation or an unexpected throw —
        // otherwise the UI stays stuck showing a pass that isn't running.
        progress.finish()
        progress.bumpLedger()
    }
}
