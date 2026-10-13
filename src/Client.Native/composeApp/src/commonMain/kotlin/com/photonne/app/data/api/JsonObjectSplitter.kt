package com.photonne.app.data.api

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

/**
 * Splits a streamed body into the top-level JSON objects it carries, as they
 * arrive. The server streams progress in two shapes: NDJSON, one object per
 * line (maintenance kinds, face clustering), and a JSON array written element
 * by element with no newlines (minimal-API `IAsyncEnumerable`: index,
 * thumbnails, metadata, dates, duplicates, library scan and
 * `/api/tasks/{id}/stream`). Reading lines only ever saw the second shape as a
 * single line at the very end, which didn't decode, so those tasks showed no
 * progress at all.
 *
 * Both are a sequence of top-level objects, so this tracks balanced braces
 * outside strings and ignores the brackets, commas and newlines in between —
 * the same approach as the SPA's `JsonObjectSplitter`. It works on bytes:
 * every structural character is ASCII, and UTF-8 never reuses those values
 * inside a multi-byte sequence, so an object split across chunks decodes
 * whole.
 */
internal class JsonObjectSplitter {
    private var buffer = ByteArray(1024)
    private var size = 0
    private var depth = 0
    private var inString = false
    private var escaped = false

    /** Feeds [count] bytes of [chunk]; returns every object it completed. */
    fun push(chunk: ByteArray, count: Int = chunk.size): List<String> {
        val objects = mutableListOf<String>()
        for (i in 0 until count) {
            val byte = chunk[i]
            if (depth == 0) {
                // Between objects: only an opening brace matters.
                if (byte == OPEN_BRACE) {
                    size = 0
                    append(byte)
                    depth = 1
                }
                continue
            }
            append(byte)
            if (inString) {
                when {
                    escaped -> escaped = false
                    byte == BACKSLASH -> escaped = true
                    byte == QUOTE -> inString = false
                }
                continue
            }
            when (byte) {
                QUOTE -> inString = true
                OPEN_BRACE, OPEN_BRACKET -> depth++
                CLOSE_BRACE, CLOSE_BRACKET -> {
                    depth--
                    if (depth == 0) {
                        objects += buffer.decodeToString(0, size)
                        size = 0
                    }
                }
            }
        }
        return objects
    }

    private fun append(byte: Byte) {
        if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        buffer[size++] = byte
    }

    private companion object {
        const val OPEN_BRACE = '{'.code.toByte()
        const val CLOSE_BRACE = '}'.code.toByte()
        const val OPEN_BRACKET = '['.code.toByte()
        const val CLOSE_BRACKET = ']'.code.toByte()
        const val QUOTE = '"'.code.toByte()
        const val BACKSLASH = '\\'.code.toByte()
    }
}

/** Reads the channel to the end, handing over each JSON object as soon as it is complete. */
internal suspend fun ByteReadChannel.forEachJsonObject(onObject: suspend (String) -> Unit) {
    val splitter = JsonObjectSplitter()
    val chunk = ByteArray(8 * 1024)
    while (true) {
        val read = readAvailable(chunk, 0, chunk.size)
        if (read == -1) break
        if (read > 0) splitter.push(chunk, read).forEach { onObject(it) }
    }
}
