package com.photonne.app.data.devicebackup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Which stage of a backup pass is running right now. */
enum class BackupPhase { Verifying, Uploading }

/** Who asked for the pass. Drives both the wording in the UI and which
 *  cancellation path applies (in-process vs. OS worker). */
enum class BackupOrigin {
    /** Running inside the ViewModel's own scope (iOS/desktop, or a retry). */
    Manual,

    /** Explicit "upload now": an OS-prioritized worker that outlives the screen. */
    Foreground,

    /** The scheduled pass (WorkManager / BGTaskScheduler). */
    Background
}

/**
 * Live snapshot of a running backup pass. Counts are cumulative within the pass;
 * [bytesDone]/[bytesTotal] only cover the upload phase (verification is measured
 * in files, since hashing cost tracks file count more than payload size).
 */
data class BackupActivity(
    val phase: BackupPhase,
    val origin: BackupOrigin,
    val hashedCount: Int = 0,
    val hashTotal: Int = 0,
    val completed: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val total: Int = 0,
    val inFlight: Int = 0,
    val currentName: String? = null,
    val bytesDone: Long = 0L,
    val bytesTotal: Long = 0L,
    /**
     * Upload fraction (0..1) of each file currently in flight, keyed by device
     * URI. Bounded by the upload fan-out (a handful of entries), so the pending
     * list can draw a per-file bar even when the pass belongs to the worker.
     */
    val inFlightItems: Map<String, Float> = emptyMap()
) {
    /** Files finished, whatever the outcome. */
    val done: Int get() = completed + skipped + failed
}

/**
 * The one channel a backup pass has to tell the rest of the app what it's doing.
 *
 * Every pass — the in-process one, the Android foreground worker and the
 * scheduled background one — lives in the same process, so a plain shared
 * [StateFlow] beats plumbing WorkManager `WorkInfo` observers: the ViewModel
 * gets identical, live state no matter who is driving.
 *
 * Without this, tapping "upload now" on Android handed the pass to the worker
 * and the screen went completely silent until the user navigated away and back.
 */
class BackupProgressBus {

    private val _activity = MutableStateFlow<BackupActivity?>(null)

    /** Null while nothing is running. */
    val activity: StateFlow<BackupActivity?> = _activity.asStateFlow()

    private val _ledgerRevision = MutableStateFlow(0L)

    /**
     * Bumped every time a pass writes verdicts to the ledger. Observers re-read
     * the ledger on change instead of polling, so a screen open during a worker
     * pass sees its pending list shrink in real time.
     */
    val ledgerRevision: StateFlow<Long> = _ledgerRevision.asStateFlow()

    fun start(phase: BackupPhase, origin: BackupOrigin) {
        _activity.value = BackupActivity(phase = phase, origin = origin)
    }

    /** Mutates the current activity; no-op when no pass is running. */
    fun update(transform: (BackupActivity) -> BackupActivity) {
        _activity.update { it?.let(transform) }
    }

    /** Marks the ledger as changed so observers refresh their verdicts. */
    fun bumpLedger() {
        _ledgerRevision.update { it + 1 }
    }

    /** Ends the pass. Always called from a `finally` so a crash mid-pass can't
     *  leave the UI stuck showing a phantom upload. */
    fun finish() {
        _activity.value = null
    }
}
