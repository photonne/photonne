package com.photonne.app.ui.actions

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The plain-file sink: the real name only appears once the download is whole. */
class FileDownloadSinkTest {

    private val dir = Path(SystemTemporaryDirectory, "photonne-sink-test-${Random.nextLong()}")
        .also { SystemFileSystem.createDirectories(it) }
    private val target = Path(dir, "photos.zip")
    private val partial = Path("$target.part")

    @AfterTest
    fun cleanUp() {
        SystemFileSystem.list(dir).forEach { SystemFileSystem.delete(it, mustExist = false) }
        SystemFileSystem.delete(dir, mustExist = false)
    }

    private fun read(path: Path): ByteArray = SystemFileSystem.source(path).buffered().use { it.readByteArray() }

    @Test
    fun a_committed_download_takes_the_real_name() = runTest {
        val sink = FileDownloadSink(target = target, displayName = "photos.zip", mimeType = "application/zip")
        sink.write(byteArrayOf(9, 1, 2, 3), offset = 1, length = 3)

        val saved = sink.commit()

        assertEquals(listOf<Byte>(1, 2, 3), read(target).toList())
        assertFalse(SystemFileSystem.exists(partial))
        assertEquals(target.toString(), saved.path)
    }

    @Test
    fun a_discarded_download_leaves_nothing_behind() = runTest {
        val sink = FileDownloadSink(target = target, displayName = "photos.zip", mimeType = "application/zip")
        sink.write(byteArrayOf(1, 2, 3), offset = 0, length = 3)

        sink.discard()

        assertFalse(SystemFileSystem.exists(target))
        assertFalse(SystemFileSystem.exists(partial))
    }

    @Test
    fun a_discarded_download_keeps_the_file_it_would_have_replaced() = runTest {
        SystemFileSystem.sink(target).buffered().use { it.writeString("old") }
        val sink = FileDownloadSink(target = target, displayName = "photos.zip", mimeType = "application/zip")
        sink.write(byteArrayOf(1), offset = 0, length = 1)

        sink.discard()

        assertTrue(SystemFileSystem.exists(target))
        assertEquals("old", read(target).decodeToString())
    }
}
