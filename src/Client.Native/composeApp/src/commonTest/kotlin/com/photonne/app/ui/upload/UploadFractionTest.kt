package com.photonne.app.ui.upload

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UploadFractionTest {
    private fun item(size: Long, sent: Long) = UploadItem(
        id = 1, name = "a.mp4", mimeType = "video/mp4", sizeBytes = size, content = null,
        status = UploadStatus.Uploading, sentBytes = sent
    )

    @Test
    fun `the bar follows the bytes sent`() {
        assertEquals(0.25f, uploadFraction(item(size = 400, sent = 100)))
    }

    @Test
    fun `unknown until there is a size and something sent`() {
        assertNull(uploadFraction(item(size = 0, sent = 100)))
        assertNull(uploadFraction(item(size = 400, sent = 0)))
    }
}
