package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.PhotonneApiException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the failure classification the backup retry loop depends on. The
 * critical case is a DNS failure during a WiFi↔cellular switch: it surfaces as
 * an UnknownHostException ("Unable to resolve host …") whose class name carries
 * none of the IOException/Socket/Connect tokens, so it must be matched
 * explicitly — otherwise a transient "host not found" gets labelled Unknown.
 */
class UploadFailureReasonTest {

    // Mimics the platform exceptions Ktor surfaces; classification keys off the
    // simple class name and message, so a same-named stand-in is faithful.
    private class UnknownHostException(message: String) : Exception(message)
    private class SocketTimeoutException(message: String) : Exception(message)
    private class GenericIOException(message: String) : Exception(message)

    @Test
    fun unknownHostIsNetworkError() {
        val reason = UnknownHostException("Unable to resolve host \"photos.example\": No address associated with hostname")
            .toUploadFailureReason()
        assertEquals(UploadFailureReason.NetworkError, reason)
        assertTrue(reason.isRetryable, "a DNS failure must be retried, not given up on")
    }

    @Test
    fun socketAndIoErrorsAreNetworkErrors() {
        assertEquals(
            UploadFailureReason.NetworkError,
            SocketTimeoutException("timeout").toUploadFailureReason()
        )
        assertEquals(
            UploadFailureReason.NetworkError,
            GenericIOException("broken pipe").toUploadFailureReason()
        )
    }

    @Test
    fun resolveHostMessageIsNetworkErrorEvenWhenClassNameIsOpaque() {
        // Some engines wrap DNS failures in a class whose name gives nothing
        // away; the message still does.
        val opaque = RuntimeException("failed to connect: unable to resolve host")
        assertEquals(UploadFailureReason.NetworkError, opaque.toUploadFailureReason())
    }

    @Test
    fun httpStatusesMapToTypedReasons() {
        assertEquals(UploadFailureReason.QuotaExceeded, apiError(409).toUploadFailureReason())
        assertEquals(UploadFailureReason.Unauthorized, apiError(401).toUploadFailureReason())
        assertEquals(UploadFailureReason.ServerError, apiError(503).toUploadFailureReason())
    }

    @Test
    fun permanentReasonsAreNotRetried() {
        assertFalse(UploadFailureReason.QuotaExceeded.isRetryable)
        assertFalse(UploadFailureReason.FileTooLarge.isRetryable)
        assertFalse(UploadFailureReason.ProxyTooLarge.isRetryable)
        assertFalse(UploadFailureReason.NotAllowed.isRetryable)
        assertFalse(UploadFailureReason.Unauthorized.isRetryable)
        // A file we can't even read won't get better by retrying the upload —
        // the user's recourse is to skip it.
        assertFalse(UploadFailureReason.FileUnreadable.isRetryable)
        assertFalse(UploadFailureReason.Rejected.isRetryable)
    }

    @Test
    fun otherClientErrorsAreRejectedNotRetriedForever() {
        // An empty file's `no_file_uploaded` 400 used to land on Unknown, which
        // is retried: every wake spent three attempts on it and the failure
        // notification never fired.
        assertEquals(UploadFailureReason.Rejected, apiError(400).toUploadFailureReason())
        assertEquals(UploadFailureReason.Rejected, apiError(415).toUploadFailureReason())
        assertEquals(UploadFailureReason.NetworkError, apiError(408).toUploadFailureReason())
        assertEquals(
            UploadFailureReason.FileUnreadable,
            DeviceGalleryUnavailable("Asset not found for IMG_0001.HEIC").toUploadFailureReason()
        )
    }

    @Test
    fun photonne413WithItsCodeIsFileTooLarge() {
        val upload = apiError(413, PHOTONNE_413, serverMessage = "El archivo supera el tamaño máximo permitido (100 MB).")
        assertEquals(UploadFailureReason.FileTooLarge, upload.toUploadFailureReason())
        // The guest upload of a request link uses its own code for the same limit.
        val guest = apiError(413, """{"error":"El archivo supera el tamaño máximo permitido (5 MB).","code":"file_too_large"}""")
        assertEquals(UploadFailureReason.FileTooLarge, guest.toUploadFailureReason())
    }

    @Test
    fun proxy413WithHtmlPageIsProxyTooLarge() {
        assertEquals(UploadFailureReason.ProxyTooLarge, apiError(413, NGINX_413).toUploadFailureReason())
    }

    @Test
    fun proxy413WithEmptyBodyIsProxyTooLarge() {
        assertEquals(UploadFailureReason.ProxyTooLarge, apiError(413, "").toUploadFailureReason())
        assertEquals(UploadFailureReason.ProxyTooLarge, apiError(413, null).toUploadFailureReason())
    }

    @Test
    fun photonne413DetailIsItsMessageOnceNotTheRawJson() {
        val message = "El archivo supera el tamaño máximo permitido (100 MB)."
        val detail = apiError(413, PHOTONNE_413, serverMessage = message, message = message).toUploadFailureDetail()
        assertEquals(message, detail)
    }

    @Test
    fun proxyHtmlPageDetailIsReducedToItsTitle() {
        val detail = apiError(413, NGINX_413, message = "Upload failed (413)").toUploadFailureDetail()
        assertEquals("Upload failed (413)\n413 Request Entity Too Large", detail)
    }

    @Test
    fun htmlWithoutTitleOrHeadingAddsNothing() {
        val detail = apiError(413, "<html><body><p>nope</p></body></html>", message = "Upload failed (413)")
            .toUploadFailureDetail()
        assertEquals("Upload failed (413)", detail)
    }

    @Test
    fun longPlainTextBodyIsCapped() {
        val summary = summarizeNonJsonBody("x".repeat(1_000))
        assertEquals(301, summary?.length)
    }

    private fun apiError(
        status: Int,
        body: String? = null,
        serverMessage: String? = null,
        message: String = "HTTP $status",
    ) = PhotonneApiException(status = status, message = message, responseBody = body, serverMessage = serverMessage)

    private companion object {
        const val PHOTONNE_413 =
            """{"error":"El archivo supera el tamaño máximo permitido (100 MB).","code":"upload_too_large"}"""
        const val NGINX_413 =
            "<html>\r\n<head><title>413 Request Entity Too Large</title></head>\r\n<body>\r\n<center><h1>413 Request Entity Too Large</h1></center>\r\n<hr><center>nginx/1.25.3</center>\r\n</body>\r\n</html>\r\n"
    }
}
