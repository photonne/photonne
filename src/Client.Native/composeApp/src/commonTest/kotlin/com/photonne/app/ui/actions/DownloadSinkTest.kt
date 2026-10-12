package com.photonne.app.ui.actions

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingSink : DownloadSink {
    val written = mutableListOf<Byte>()
    var committed = false
    var discarded = false

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        for (i in offset until offset + length) written += buffer[i]
    }

    override suspend fun commit(): SavedAssetFile {
        committed = true
        return SavedAssetFile(path = "/tmp/x", displayName = "x", mimeType = "application/zip")
    }

    override suspend fun discard() {
        discarded = true
    }
}

/** The file a download streams into: whole bytes, progress, and no half files. */
class DownloadSinkTest {

    @Test
    fun the_whole_body_lands_in_the_file_and_is_committed() = runTest {
        val sink = RecordingSink()
        val progress = mutableListOf<Pair<Long, Long?>>()
        val body = ByteArray(200_000) { (it % 251).toByte() }

        val saved = ByteReadChannel(body).drainInto(sink, contentLength = body.size.toLong()) { written, total ->
            progress += written to total
        }

        assertEquals("x", saved.displayName)
        assertEquals(body.toList(), sink.written)
        assertTrue(sink.committed)
        assertFalse(sink.discarded)
        assertEquals(body.size.toLong() to body.size.toLong(), progress.last())
    }

    @Test
    fun without_a_length_the_progress_has_no_total() = runTest {
        val sink = RecordingSink()
        var total: Long? = -1

        ByteReadChannel(byteArrayOf(1, 2, 3)).drainInto(sink, contentLength = null) { _, t -> total = t }

        assertNull(total)
        assertTrue(sink.committed)
    }

    @Test
    fun a_body_shorter_than_announced_is_discarded() = runTest {
        val sink = RecordingSink()

        assertFailsWith<Throwable> {
            ByteReadChannel(byteArrayOf(1, 2, 3)).drainInto(sink, contentLength = 10)
        }

        assertTrue(sink.discarded)
        assertFalse(sink.committed)
    }

    @Test
    fun a_broken_connection_discards_the_half_file() = runTest {
        val sink = RecordingSink()
        val channel = ByteChannel()
        channel.writeFully(byteArrayOf(1, 2, 3))
        channel.flush()
        channel.cancel(IllegalStateException("connection reset"))

        assertFailsWith<Throwable> { channel.drainInto(sink, contentLength = 10) }

        assertTrue(sink.discarded)
        assertFalse(sink.committed)
    }

    @Test
    fun cancelling_mid_download_discards_the_half_file() = runTest {
        val sink = RecordingSink()
        val channel = ByteChannel()
        val started = CompletableDeferred<Unit>()
        val job = launch {
            channel.drainInto(sink, contentLength = 10) { _, _ -> started.complete(Unit) }
        }
        channel.writeFully(byteArrayOf(1, 2, 3))
        channel.flush()
        started.await()

        job.cancelAndJoin()

        assertTrue(sink.discarded)
        assertFalse(sink.committed)
    }

    @Test
    fun an_in_memory_payload_goes_through_the_same_sink() = runTest {
        val sink = RecordingSink()

        sink.writeAll(byteArrayOf(4, 5))

        assertEquals(listOf<Byte>(4, 5), sink.written)
        assertTrue(sink.committed)
    }
}
