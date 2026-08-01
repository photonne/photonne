package com.photonne.app.data.devicebackup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The bus is the only channel a worker pass has to reach the UI, so its
 * contract matters: no activity means idle, updates never resurrect a finished
 * pass, and the ledger revision only ever moves forward.
 */
class BackupProgressBusTest {

    @Test
    fun idleUntilAPassStarts() {
        val bus = BackupProgressBus()
        assertNull(bus.activity.value)
    }

    @Test
    fun startPublishesPhaseAndOrigin() {
        val bus = BackupProgressBus()
        bus.start(BackupPhase.Verifying, BackupOrigin.Foreground)

        val activity = bus.activity.value
        assertEquals(BackupPhase.Verifying, activity?.phase)
        assertEquals(BackupOrigin.Foreground, activity?.origin)
        assertEquals(0, activity?.done)
    }

    @Test
    fun updateMutatesTheRunningPass() {
        val bus = BackupProgressBus()
        bus.start(BackupPhase.Uploading, BackupOrigin.Background)

        bus.update { it.copy(total = 10, completed = 3, failed = 1, bytesDone = 2048L) }

        val activity = bus.activity.value
        assertEquals(10, activity?.total)
        assertEquals(4, activity?.done, "done counts every finished file, failures included")
        assertEquals(2048L, activity?.bytesDone)
    }

    @Test
    fun updateIsANoOpWhenNoPassIsRunning() {
        val bus = BackupProgressBus()

        bus.update { it.copy(total = 99) }

        assertNull(bus.activity.value, "an update must never resurrect a finished pass")
    }

    @Test
    fun finishClearsTheActivity() {
        val bus = BackupProgressBus()
        bus.start(BackupPhase.Uploading, BackupOrigin.Manual)
        bus.update { it.copy(completed = 5) }

        bus.finish()

        assertNull(bus.activity.value)
    }

    @Test
    fun ledgerRevisionMovesForwardOnEveryBump() {
        val bus = BackupProgressBus()
        val initial = bus.ledgerRevision.value

        bus.bumpLedger()
        bus.bumpLedger()

        assertEquals(initial + 2, bus.ledgerRevision.value)
    }
}
