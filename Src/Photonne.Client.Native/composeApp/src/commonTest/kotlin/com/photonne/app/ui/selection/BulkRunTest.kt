package com.photonne.app.ui.selection

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BulkRunTest {

    @Test
    fun countsSuccessesAndFailuresWithoutStopping() = runTest {
        val seen = mutableListOf<String>()
        val outcome = runBulk(listOf("a", "b", "c"), concurrency = 1) { id ->
            seen += id
            if (id == "b") error("boom")
        }
        assertEquals(listOf("a", "b", "c"), seen)
        assertEquals(listOf("a", "c"), outcome.succeeded)
        assertEquals(listOf("b"), outcome.failed)
        assertEquals(3, outcome.total)
    }

    @Test
    fun emptyInputDoesNothing() = runTest {
        val outcome = runBulk(emptyList()) { error("never") }
        assertEquals(0, outcome.total)
    }
}
