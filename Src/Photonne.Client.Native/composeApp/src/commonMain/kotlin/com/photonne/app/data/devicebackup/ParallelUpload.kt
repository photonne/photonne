package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.UploadAssetResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Terminal outcome of one item's upload, already classified so callers
 * don't have to re-implement the "already exists" dedup heuristic.
 */
sealed interface UploadOutcome {
    /** A real new upload landed on the server. */
    data class Uploaded(val assetId: String) : UploadOutcome
    /** Server short-circuited via SHA-256 dedup — the asset was already there. */
    data class Skipped(val assetId: String) : UploadOutcome
    /** The upload failed after [DeviceBackupRepository.upload]'s own retries. */
    data class Failed(
        val reason: UploadFailureReason,
        val detail: String?,
        val error: Throwable
    ) : UploadOutcome
}

/**
 * Uploads [pending] with bounded concurrency
 * ([DeviceBackupRepository.UPLOAD_CONCURRENCY] in flight at once), mirroring
 * the Semaphore + Mutex + coroutineScope pattern used for parallel hashing.
 *
 * Shared by both the background [BackupRunner] and the foreground
 * [com.photonne.app.ui.devicebackup.DeviceBackupViewModel] so the
 * concurrency mechanics (bounded fan-out, cancellation gating, completed-count
 * progress, outcome classification) live in exactly one place.
 *
 * For each item it calls [upload] (which already retries internally and is
 * safe to call concurrently), classifies the result, and reports it through
 * [onItemStart] (when the item begins) and [onItemDone] (when it finishes).
 * [onItemStart]/[onItemDone] run under an internal [Mutex], so callers may
 * freely mutate plain `var`s, maps, or a `MutableStateFlow` inside them
 * without their own locking. The [upload] call itself happens OUTSIDE the
 * mutex — holding the lock across the network transfer would serialize
 * everything.
 *
 * [onItemProgress] is invoked OUTSIDE the mutex with the in-flight item's
 * upload fraction (0..1) for a per-file progress bar. It only touches that
 * one item's transient progress, so it doesn't need the lock; callers should
 * throttle it (e.g. to whole-percent steps) to avoid excess recomposition.
 *
 * Cancellation is cooperative and non-destructive: [shouldContinue] is checked
 * before acquiring a permit and again before starting each upload, so items
 * not yet started are skipped while the few already in flight finish cleanly
 * (no half-written assets, no inconsistent ledger rows).
 *
 * @param onItemDone receives the [UploadOutcome] and `completed` — the number
 *   of items fully finished so far (1..pending.size), suitable as a progress
 *   numerator independent of completion order.
 */
suspend fun uploadInParallel(
    pending: List<DeviceMedia>,
    upload: suspend (media: DeviceMedia, onProgress: (Float) -> Unit) -> UploadAssetResponse,
    shouldContinue: () -> Boolean = { true },
    onItemStart: (media: DeviceMedia) -> Unit = {},
    onItemProgress: (media: DeviceMedia, fraction: Float) -> Unit = { _, _ -> },
    onItemDone: (media: DeviceMedia, outcome: UploadOutcome, completed: Int) -> Unit
) {
    if (pending.isEmpty()) return
    val permits = Semaphore(DeviceBackupRepository.UPLOAD_CONCURRENCY)
    val writes = Mutex()
    var completed = 0
    coroutineScope {
        pending.map { media ->
            async(Dispatchers.Default) {
                if (!shouldContinue()) return@async
                permits.withPermit {
                    // The cancel flag may have flipped while we waited for a
                    // permit — re-check so we don't start a new upload.
                    if (!shouldContinue()) return@withPermit
                    writes.withLock { onItemStart(media) }
                    val outcome = runCatching {
                        upload(media) { fraction -> onItemProgress(media, fraction) }
                    }.fold(
                        onSuccess = { response ->
                            val isDup = response.message
                                .contains("already exists", ignoreCase = true)
                            if (isDup) UploadOutcome.Skipped(response.assetId.orEmpty())
                            else UploadOutcome.Uploaded(response.assetId.orEmpty())
                        },
                        onFailure = { error ->
                            UploadOutcome.Failed(
                                reason = error.toUploadFailureReason(),
                                detail = error.toUploadFailureDetail(),
                                error = error
                            )
                        }
                    )
                    writes.withLock {
                        completed++
                        onItemDone(media, outcome, completed)
                    }
                }
            }
        }.awaitAll()
    }
}
