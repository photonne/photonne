package com.photonne.app.data.actions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TrashRetentionTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z")

    @Test
    fun countsWholeDaysLeftRoundingUp() {
        assertEquals(30, trashDaysLeft(Instant.parse("2026-10-26T11:00:00Z"), 30, now))
        assertEquals(24, trashDaysLeft(Instant.parse("2026-10-20T12:00:00Z"), 30, now))
        assertEquals(1, trashDaysLeft(Instant.parse("2026-09-26T13:00:00Z"), 30, now))
    }

    @Test
    fun isZeroOnceTheRetentionHasPassed() {
        assertEquals(0, trashDaysLeft(Instant.parse("2026-09-01T00:00:00Z"), 30, now))
    }

    @Test
    fun isNullWithoutRetentionOrDate() {
        assertNull(trashDaysLeft(Instant.parse("2026-10-20T12:00:00Z"), null, now))
        assertNull(trashDaysLeft(Instant.parse("2026-10-20T12:00:00Z"), 0, now))
        assertNull(trashDaysLeft(null, 30, now))
    }
}
