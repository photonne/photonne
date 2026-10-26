package com.photonne.app.ui.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Against the reference decoder (npm thumbhash 0.1.1), on the server's own test hashes. */
class ThumbHashDecoderTest {
    private fun ThumbHashImage.pixel(index: Int) =
        (0 until 4).map { rgba[index * 4 + it].toInt() and 0xFF }

    @Test
    fun `an opaque landscape hash decodes like the reference`() {
        val image = ThumbHashDecoder.decodeBase64("ofcFJYRA7JqD6iiwmzaaOQl4iMB/")!!
        assertEquals(32, image.w)
        assertEquals(23, image.h)
        assertEquals(listOf(123, 115, 161, 255), image.pixel(0))
        assertEquals(listOf(120, 111, 156, 255), image.pixel(1))
        assertEquals(listOf(100, 85, 102, 255), image.pixel(image.w * 5 + 7))
        assertEquals(listOf(122, 141, 195, 255), image.pixel(image.w * image.h - 1))
    }

    @Test
    fun `a portrait hash with transparency decodes like the reference`() {
        val image = ThumbHashDecoder.decodeBase64("HxiCFAQI2SB0OuWUewQcZrgPa4jMPbTGBA==")!!
        assertEquals(26, image.w)
        assertEquals(32, image.h)
        assertEquals(listOf(132, 97, 146, 136), image.pixel(0))
        assertEquals(listOf(134, 107, 134, 136), image.pixel(image.w * 5 + 7))
        assertEquals(listOf(149, 108, 118, 136), image.pixel(image.w * image.h - 1))
    }

    @Test
    fun `nothing or garbage decodes to nothing`() {
        assertNull(ThumbHashDecoder.decodeBase64(null))
        assertNull(ThumbHashDecoder.decodeBase64(""))
        assertNull(ThumbHashDecoder.decodeBase64("AA=="))
    }
}
