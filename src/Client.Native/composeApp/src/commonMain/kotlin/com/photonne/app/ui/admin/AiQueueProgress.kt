package com.photonne.app.ui.admin

import com.photonne.app.data.models.PendingCountResponse
import kotlin.time.Instant

/**
 * What an AI row can honestly say about a queue that is draining.
 *
 * A percentage alone is the wrong instrument for this: on a queue of twenty
 * thousand photos it reads 0 % for minutes whether the workers are flying or
 * dead, and the admin has no way to tell which. So the row also reports how
 * many are done out of how many this run took on, the rate the server measured
 * over its last minute, the time left at that rate, and — when the queue is
 * non-empty but nothing is being claimed or finished — how long it has been
 * that way. Pure, so it can be tested without a composable.
 */
data class AiQueueProgress(
    /** Completed since the admin tapped Iniciar. */
    val done: Int,
    /** [done] plus what is still queued: the size of *this* run. */
    val total: Int,
    /** `done / total`, or null when there is nothing to divide. */
    val fraction: Float?,
    /** Jobs claimed by a worker right now; null on servers that don't say. */
    val processing: Int?,
    /** Server-measured throughput, null when the server doesn't report it or
     *  measured zero (a zero rate is [stalledForSeconds]'s business). */
    val perMinute: Int?,
    /** Seconds until the queue empties at [perMinute]. Null without a rate. */
    val etaSeconds: Long?,
    /** How long the queue has had work and shown no life: nothing claimed,
     *  nothing finished in the last minute, and the last completion — if any —
     *  older than [StalledAfterSeconds]. Null while things move, and on
     *  servers that don't report liveness. */
    val stalledForSeconds: Long?,
) {
    val isStalled: Boolean get() = stalledForSeconds != null

    companion object {
        /** Before this, a quiet queue is just a queue that started a second
         *  ago: the first job has to be read, sent and stored before anything
         *  counts as finished. */
        const val StalledAfterSeconds = 90L
    }
}

/**
 * Builds the row's progress from the last poll. [sessionBaseline] is the
 * `completed` count when this run started; without one (queue started from the
 * PWA, or before this screen opened) the fraction is unknown and the row shows
 * an indeterminate bar, but the liveness signals still apply — they don't
 * depend on where the run started.
 */
fun aiQueueProgress(
    pending: PendingCountResponse,
    sessionBaseline: Int?,
    nowMs: Long,
): AiQueueProgress {
    val done = sessionBaseline?.let { (pending.completed - it).coerceAtLeast(0) } ?: 0
    val total = if (sessionBaseline != null) done + pending.inQueue else 0
    val fraction = if (sessionBaseline != null && total > 0) done.toFloat() / total.toFloat() else null

    val perMinute = pending.completedLastMinute.takeIf { pending.reportsLiveness && it > 0 }
    val etaSeconds = perMinute?.let { rate -> (pending.inQueue.toLong() * 60L + rate - 1) / rate }

    val stalledForSeconds = if (
        pending.reportsLiveness &&
        pending.inQueue > 0 &&
        (pending.processing ?: 0) == 0 &&
        pending.completedLastMinute == 0
    ) {
        val lastMs = pending.lastCompletedAt
            ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
        val quietFor = if (lastMs != null) (nowMs - lastMs) / 1000L else null
        when {
            // Never finished one and nothing in flight: no reference point, but
            // a queue with work in it and no worker on it is the case this
            // exists for.
            quietFor == null -> AiQueueProgress.StalledAfterSeconds
            quietFor >= AiQueueProgress.StalledAfterSeconds -> quietFor
            else -> null
        }
    } else null

    return AiQueueProgress(
        done = done,
        total = total,
        fraction = fraction,
        processing = pending.processing,
        perMinute = perMinute,
        etaSeconds = etaSeconds,
        stalledForSeconds = stalledForSeconds,
    )
}

/** "1.234" — thousands grouped with a dot, the way the rest of the admin
 *  screens print counts. Sign-safe for the negative deltas a clock skew can
 *  produce, though callers clamp before formatting. */
internal fun formatCount(value: Int): String {
    val digits = kotlin.math.abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (value < 0) "-$grouped" else grouped
}

/** Coarse remaining time: "~2 h 10 min", "~7 min", "menos de un minuto".
 *  Coarser than [formatDurationSeconds] on purpose — an ETA that ticks by
 *  the second pretends to a precision a one-minute rate can't back up. */
internal fun formatEta(seconds: Long): String {
    if (seconds < 60) return "<1 min"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 && m > 0 -> "~${h} h ${m} min"
        h > 0 -> "~${h} h"
        else -> "~${m} min"
    }
}
