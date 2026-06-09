package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.PhotonneApiException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    // DNS failures during a WiFi↔cellular switch arrive as UnknownHostException
    // ("Unable to resolve host …") whose simple name contains none of the
    // IOException/Socket/Connect tokens, so it is matched explicitly — otherwise
    // a transient "host not found" would be labelled Unknown.
    val name = this::class.simpleName.orEmpty()
    val message = this.message.orEmpty()
    return when {
        name.contains("IOException", ignoreCase = true) -> UploadFailureReason.NetworkError
        name.contains("Timeout", ignoreCase = true) -> UploadFailureReason.NetworkError
        name.contains("Connect", ignoreCase = true) -> UploadFailureReason.NetworkError
        name.contains("Socket", ignoreCase = true) -> UploadFailureReason.NetworkError
        name.contains("UnknownHost", ignoreCase = true) -> UploadFailureReason.NetworkError
        name.contains("Host", ignoreCase = true) -> UploadFailureReason.NetworkError
        message.contains("resolve host", ignoreCase = true) -> UploadFailureReason.NetworkError
        message.contains("Network", ignoreCase = true) -> UploadFailureReason.NetworkError
        else -> UploadFailureReason.Unknown
    }
}

/**
 * Human-diagnosable detail for an upload failure: the exception message plus
 * whatever the server said. The API returns errors as RFC 7807 ProblemDetails
 * (`Results.Problem(ex.Message)`), so when the body parses as JSON we surface
 * its `detail`/`title` instead of the raw payload.
 */
fun Throwable.toUploadFailureDetail(): String? {
    val api = this as? PhotonneApiException ?: return message
    val serverSays = api.responseBody
        ?.takeIf { it.isNotBlank() }
        ?.let { raw ->
            runCatching {
                val problem = Json.parseToJsonElement(raw).jsonObject
                problem["detail"]?.jsonPrimitive?.contentOrNull
                    ?: problem["title"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: raw
        }
    return listOfNotNull(api.message, serverSays)
        .joinToString("\n")
        .ifBlank { null }
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
