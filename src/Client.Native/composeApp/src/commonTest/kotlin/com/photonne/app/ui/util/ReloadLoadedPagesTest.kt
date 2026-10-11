package com.photonne.app.ui.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReloadLoadedPagesTest {

    /** Offset-paged fake list of "0".."[size]-1", [pageSize] per page. */
    private fun pager(size: Int, pageSize: Int, calls: MutableList<Int?>): suspend (Int?) -> LoadedPage<String, Int> =
        { cursor ->
            calls += cursor
            val from = cursor ?: 0
            val to = minOf(from + pageSize, size)
            LoadedPage(
                items = (from until to).map { it.toString() },
                hasMore = to < size,
                nextCursor = if (to < size) to else null
            )
        }

    @Test
    fun reads_as_many_pages_as_were_loaded() = runTest {
        val calls = mutableListOf<Int?>()
        val result = reloadLoadedPages(loadedCount = 25, idOf = { it }, fetch = pager(100, 10, calls))

        assertEquals(30, result.items.size)
        assertTrue(result.hasMore)
        assertEquals(30, result.nextCursor)
        assertEquals(listOf(null, 10, 20), calls)
    }

    @Test
    fun stops_when_the_list_ends() = runTest {
        val calls = mutableListOf<Int?>()
        val result = reloadLoadedPages(loadedCount = 50, idOf = { it }, fetch = pager(15, 10, calls))

        assertEquals(15, result.items.size)
        assertFalse(result.hasMore)
        assertEquals(2, calls.size)
    }

    @Test
    fun always_reads_the_first_page_even_with_nothing_loaded() = runTest {
        val calls = mutableListOf<Int?>()
        val result = reloadLoadedPages(loadedCount = 0, idOf = { it }, fetch = pager(15, 10, calls))

        assertEquals(10, result.items.size)
        assertEquals(1, calls.size)
    }

    @Test
    fun duplicates_across_pages_are_kept_once() = runTest {
        val result = reloadLoadedPages<String, Int>(loadedCount = 4, idOf = { it }) { cursor ->
            if (cursor == null) LoadedPage(listOf("a", "b"), hasMore = true, nextCursor = 1)
            else LoadedPage(listOf("b", "c"), hasMore = false, nextCursor = null)
        }

        assertEquals(listOf("a", "b", "c"), result.items)
    }

    @Test
    fun a_failing_page_fails_the_whole_reload() = runTest {
        assertFailsWith<IllegalStateException> {
            reloadLoadedPages<String, Int>(loadedCount = 20, idOf = { it }) { cursor ->
                if (cursor == null) LoadedPage(listOf("a"), hasMore = true, nextCursor = 1)
                else error("boom")
            }
        }
    }
}
