package com.photonne.app.ui.admin

import com.photonne.app.data.models.PendingCountResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The numbers an AI row prints while its queue drains. A percentage can't
 * tell a queue that's working from one that's dead — on twenty thousand
 * photos it reads 0 % for minutes either way — so these are the signals
 * that can, and they have to stay honest on old servers that don't send them.
 */
class AiQueueProgressTest {

    private val now = 1_800_000_000_000L // any fixed instant

    private fun pending(
        completed: Int = 0,
        inQueue: Int = 0,
        processing: Int? = null,
        lastCompletedAt: String? = null,
        completedLastMinute: Int = 0,
    ) = PendingCountResponse(
        unprocessed = 0,
        inQueue = inQueue,
        completed = completed,
        processing = processing,
        lastCompletedAt = lastCompletedAt,
        completedLastMinute = completedLastMinute,
    )

    private fun isoSecondsAgo(seconds: Long): String =
        kotlin.time.Instant.fromEpochMilliseconds(now - seconds * 1000L).toString()

    @Test
    fun progressIsScopedToThisRun() {
        // 5.000 were done before the admin tapped Iniciar; this run queued 100
        // and 25 have finished. The bar is 25 %, not 98 %.
        val p = aiQueueProgress(pending(completed = 5_025, inQueue = 75, processing = 1), sessionBaseline = 5_000, nowMs = now)

        assertEquals(25, p.done)
        assertEquals(100, p.total)
        assertEquals(0.25f, p.fraction)
    }

    @Test
    fun withoutABaselineThereIsNoFractionButLivenessStillWorks() {
        val p = aiQueueProgress(pending(completed = 10, inQueue = 40, processing = 1, completedLastMinute = 30), sessionBaseline = null, nowMs = now)

        assertNull(p.fraction)
        assertEquals(30, p.perMinute)
        assertEquals(80, p.etaSeconds)
        assertFalse(p.isStalled)
    }

    @Test
    fun etaRoundsUpToWholeSecondsOfTheMeasuredRate() {
        // 100 in queue at 7/min = 857.1 s → 858.
        val p = aiQueueProgress(pending(inQueue = 100, processing = 1, completedLastMinute = 7), sessionBaseline = 0, nowMs = now)

        assertEquals(858, p.etaSeconds)
    }

    @Test
    fun aQueueWithNothingClaimedAndNothingFinishedIsStalled() {
        val p = aiQueueProgress(
            pending(inQueue = 500, processing = 0, completedLastMinute = 0, lastCompletedAt = isoSecondsAgo(240)),
            sessionBaseline = 0,
            nowMs = now,
        )

        assertTrue(p.isStalled)
        assertEquals(240, p.stalledForSeconds)
        assertNull(p.perMinute)
        assertNull(p.etaSeconds)
    }

    @Test
    fun aQueueThatJustStartedIsNotStalledYet() {
        // The first job has to be read, sent and stored before it counts as
        // finished; a completion 20 s ago with nothing in flight is a gap,
        // not a corpse.
        val p = aiQueueProgress(
            pending(inQueue = 500, processing = 0, completedLastMinute = 0, lastCompletedAt = isoSecondsAgo(20)),
            sessionBaseline = 0,
            nowMs = now,
        )

        assertFalse(p.isStalled)
    }

    @Test
    fun aQueueThatNeverFinishedAnythingAndHasNoWorkerOnItIsStalled() {
        val p = aiQueueProgress(pending(inQueue = 500, processing = 0, completedLastMinute = 0, lastCompletedAt = null), sessionBaseline = 0, nowMs = now)

        assertTrue(p.isStalled)
        assertEquals(AiQueueProgress.StalledAfterSeconds, p.stalledForSeconds)
    }

    @Test
    fun somethingInFlightMeansNotStalledEvenWithNoCompletions() {
        // OCR on a 24 MP original takes seconds; one claimed job is life.
        val p = aiQueueProgress(pending(inQueue = 500, processing = 1, completedLastMinute = 0, lastCompletedAt = null), sessionBaseline = 0, nowMs = now)

        assertFalse(p.isStalled)
    }

    @Test
    fun anOlderServerNeverReportsAStallOrARate() {
        // Pre-liveness servers send neither field; the row must not call the
        // queue dead because the server never said it was alive.
        val p = aiQueueProgress(pending(inQueue = 500, processing = null), sessionBaseline = 0, nowMs = now)

        assertFalse(p.isStalled)
        assertNull(p.perMinute)
        assertNull(p.processing)
    }

    @Test
    fun countsGroupThousandsWithADot() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("1.000", formatCount(1_000))
        assertEquals("1.234.567", formatCount(1_234_567))
        assertEquals("-1.234", formatCount(-1_234))
    }

    @Test
    fun etaIsCoarseOnPurpose() {
        assertEquals("<1 min", formatEta(45))
        assertEquals("~7 min", formatEta(7 * 60 + 30))
        assertEquals("~2 h", formatEta(2 * 3600))
        assertEquals("~2 h 10 min", formatEta(2 * 3600 + 10 * 60 + 59))
    }
}
