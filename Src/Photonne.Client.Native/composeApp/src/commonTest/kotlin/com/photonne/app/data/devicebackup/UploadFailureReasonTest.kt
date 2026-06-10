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
        assertEquals(UploadFailureReason.FileTooLarge, apiError(413).toUploadFailureReason())
        assertEquals(UploadFailureReason.Unauthorized, apiError(401).toUploadFailureReason())
        assertEquals(UploadFailureReason.ServerError, apiError(503).toUploadFailureReason())
    }

    @Test
    fun permanentReasonsAreNotRetried() {
        assertFalse(UploadFailureReason.QuotaExceeded.isRetryable)
        assertFalse(UploadFailureReason.FileTooLarge.isRetryable)
        assertFalse(UploadFailureReason.NotAllowed.isRetryable)
        assertFalse(UploadFailureReason.Unauthorized.isRetryable)
        // A file we can't even read won't get better by retrying the upload —
        // the user's recourse is to skip it.
        assertFalse(UploadFailureReason.FileUnreadable.isRetryable)
    }

    private fun apiError(status: Int) =
        PhotonneApiException(status = status, message = "HTTP $status")
}
