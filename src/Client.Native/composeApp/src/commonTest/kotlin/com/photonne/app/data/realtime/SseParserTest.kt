package com.photonne.app.data.realtime

import kotlin.test.Test
import kotlin.test.assertEquals

class SseParserTest {
    @Test
    fun `names each event once it ends across chunks and skips comments`() {
        val parser = SseParser()
        assertEquals(emptyList(), parser.push(": connected\n\nevent: lib".encodeToByteArray()))
        assertEquals(
            listOf("library", "notifications"),
            parser.push("rary\ndata: {}\n\n: ping\n\nevent: notifications\r\ndata: {}\r\n\r\n".encodeToByteArray())
        )
    }
}
