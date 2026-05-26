package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.PhotonneApiException

/**
 * Categorizes why an upload failed. The viewmodel/UI map each value to a
 * localized message so the user sees something concrete instead of an HTTP
 * status code. New reasons should be added here as the server grows.
 */
enum class UploadFailureReason {
    /** Server returned 409 — user is out of storage quota. */
    QuotaExceeded,
    /** Server returned 413 — file is bigger than ServerSettings.MaxUploadSizeMb. */
    FileTooLarge,
    /** Server returned 403 — user not allowed to write here (path restriction). */
    NotAllowed,
    /** Server returned 401 — session expired, user has to re-login. */
    Unauthorized,
    /** Server returned 429 — rate limited; retries already exhausted. */
    RateLimited,
    /** Server returned 5xx — backend error, may be temporary. */
    ServerError,
    /** Network exception — no response, timeout, DNS, etc. */
    NetworkError,
    /** Anything else: serialization, unknown HTTP code, programming bug. */
    Unknown,
}

/**
 * Maps a [Throwable] from the upload pipeline to a typed reason. Inspects
 * [PhotonneApiException.status] when available; falls back to NetworkError
 * for I/O exceptions and Unknown for anything else.
 */
fun Throwable.toUploadFailureReason(): UploadFailureReason {
    if (this is PhotonneApiException) {
        return when (status) {
            401 -> UploadFailureReason.Unauthorized
            403 -> UploadFailureReason.NotAllowed
            409 -> UploadFailureReason.QuotaExceeded
            413 -> UploadFailureReason.FileTooLarge
            429 -> UploadFailureReason.RateLimited
            in 500..599 -> UploadFailureReason.ServerError
            else -> UploadFailureReason.Unknown
        }
    }
    // I/O / timeout / DNS / socket errors all surface through Ktor as various
    // exceptions; match by simple class-name heuristic to stay platform-neutral.
    val name = this::class.simpleName.orEmpty()
    return when {
        name.contains("IOException", ignoreCase = true) -> UploadFailureReason.NetworkError
        name.contains("Timeout", ignoreCase = true) -> UploadFailureReason.NetworkError
        name.contains("Connect", ignoreCase = true) -> UploadFailureReason.NetworkError
        name.contains("Socket", ignoreCase = true) -> UploadFailureReason.NetworkError
        else -> UploadFailureReason.Unknown
    }
}

/**
 * Permanent reasons stop the retry loop immediately. Transient ones get
 * retried with backoff.
 */
val UploadFailureReason.isRetryable: Boolean
    get() = when (this) {
        UploadFailureReason.NetworkError,
        UploadFailureReason.ServerError,
        UploadFailureReason.RateLimited,
        UploadFailureReason.Unknown -> true
        UploadFailureReason.QuotaExceeded,
        UploadFailureReason.FileTooLarge,
        UploadFailureReason.NotAllowed,
        UploadFailureReason.Unauthorized -> false
    }
