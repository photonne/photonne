package com.photonne.app.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteFormatTest {
    @Test
    fun bytesBelowOneKilobyteStayInBytes() {
        assertEquals("0 B", humanBytes(0))
        assertEquals("1023 B", humanBytes(1023))
    }

    @Test
    fun oneDecimalWithCommaBelowOneHundred() {
        assertEquals("1,5 KB", humanBytes(1536))
        assertEquals("2,4 GB", humanBytes((2.37 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun wholeValuesDropTheDecimal() {
        assertEquals("1 KB", humanBytes(1024))
        assertEquals("2 GB", humanBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun noDecimalFromOneHundredUp() {
        assertEquals("512 MB", humanBytes(512L * 1024 * 1024))
        assertEquals("150 MB", humanBytes((149.6 * 1024 * 1024).toLong()))
    }

    @Test
    fun capsAtTerabytes() {
        assertEquals("2048 TB", humanBytes(2048L * 1024 * 1024 * 1024 * 1024))
    }
}
