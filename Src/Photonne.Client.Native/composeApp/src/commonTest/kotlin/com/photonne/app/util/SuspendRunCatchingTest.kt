package com.photonne.app.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SuspendRunCatchingTest {

    @Test
    fun wraps_ordinary_failures() = runTest {
        val result = suspendRunCatching { error("boom") }
        assertTrue(result.isFailure)
        assertEquals("boom", result.exceptionOrNull()?.message)
    }

    @Test
    fun returns_the_value_on_success() = runTest {
        assertEquals(42, suspendRunCatching { 42 }.getOrThrow())
    }

    @Test
    fun rethrows_cancellation() = runTest {
        assertFailsWith<CancellationException> {
            suspendRunCatching { throw CancellationException("replaced") }
        }
    }
}
