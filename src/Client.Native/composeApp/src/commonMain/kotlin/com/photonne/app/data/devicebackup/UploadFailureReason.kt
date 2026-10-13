package com.photonne.app.data.devicebackup

import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.api.serverCode
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
    /** Photonne returned 413 with its own `upload_too_large` body — the file
     *  is bigger than ServerSettings.MaxUploadSizeMb. */
    FileTooLarge,
    /** A 413 that Photonne didn't write: a reverse proxy in front of it (nginx's
     *  `client_max_body_size`, typically) answered with its HTML page or an
     *  empty body before the request reached the server. */
    ProxyTooLarge,
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
    /** The local file couldn't be read/hashed (deleted mid-scan, codec error,
     *  permission revoked). Not a server problem — retrying rarely helps, so
     *  the user's recourse is to skip it. */
    FileUnreadable,
    /** Any other 4xx (an empty file's 400, a 415…): the server refused this
     *  file and will refuse it again, so it isn't retried on every wake. */
    Rejected,
    /** Anything else: serialization, unknown HTTP code, programming bug. */
    Unknown,
}

/**
 * `ApiError.Code` of Photonne's own 413s: `upload_too_large` from the upload and
 * sync endpoints, `file_too_large` from the guest upload of a request link. Any
 * other 413 — an nginx HTML page, an empty body — came from something in between.
 */
private val PHOTONNE_TOO_LARGE_CODES = setOf("upload_too_large", "file_too_large")

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
            413 -> if (serverCode in PHOTONNE_TOO_LARGE_CODES) UploadFailureReason.FileTooLarge
                else UploadFailureReason.ProxyTooLarge
            408 -> UploadFailureReason.NetworkError
            429 -> UploadFailureReason.RateLimited
            in 400..499 -> UploadFailureReason.Rejected
            in 500..599 -> UploadFailureReason.ServerError
            else -> UploadFailureReason.Unknown
        }
    }
    // The gallery can't hand over the file (iOS asset deleted, no data
    // resource): the file's problem, not a transient one. As Unknown it was
    // retried, and its failure notification suppressed, on every wake.
    if (this is DeviceGalleryUnavailable) return UploadFailureReason.FileUnreadable
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
 * whatever the server said. Photonne answers `{ "error", "code" }` (already
 * read into [PhotonneApiException.serverMessage]) or RFC 7807 ProblemDetails,
 * so a JSON body surfaces its text instead of the raw payload; a proxy's HTML
 * error page is reduced to its `<title>` rather than dumped whole.
 */
fun Throwable.toUploadFailureDetail(): String? {
    val api = this as? PhotonneApiException ?: return message
    val serverSays = api.serverMessage ?: api.responseBody
        ?.takeIf { it.isNotBlank() }
        ?.let { raw ->
            runCatching {
                val problem = Json.parseToJsonElement(raw).jsonObject
                problem["detail"]?.jsonPrimitive?.contentOrNull
                    ?: problem["title"]?.jsonPrimitive?.contentOrNull
            }.getOrNull() ?: summarizeNonJsonBody(raw)
        }
    return listOfNotNull(api.message, serverSays)
        .distinct()
        .joinToString("\n")
        .ifBlank { null }
}

private val HTML_TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_H1 = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val HTML_TAG = Regex("<[^>]+>")
private val WHITESPACE = Regex("\\s+")

/**
 * A body that isn't JSON: an HTML page (nginx, Cloudflare…) collapses to its
 * `<title>` or first `<h1>`; plain text is kept, capped so a stray page or
 * trace never floods the dialog. `null` when nothing readable is left.
 */
internal fun summarizeNonJsonBody(raw: String): String? {
    val looksHtml = raw.trimStart().startsWith("<")
    val text = if (looksHtml) {
        (HTML_TITLE.find(raw) ?: HTML_H1.find(raw))?.groupValues?.get(1)
            ?.replace(HTML_TAG, " ")
            ?: return null
    } else raw
    val clean = text.replace(WHITESPACE, " ").trim()
    if (clean.isEmpty()) return null
    return if (clean.length > MAX_DETAIL_CHARS) clean.take(MAX_DETAIL_CHARS).trimEnd() + "…" else clean
}

private const val MAX_DETAIL_CHARS = 300

/**
 * Lote L11: fallos que no son del archivo sino de la cuenta (cuota llena,
 * sesión caducada). El siguiente archivo va a recibir el mismo rechazo, así
 * que la pasada se corta al primero; y no se tratan como permanentes: se
 * reintentan en cuanto se resuelve (ver [BackupPassBlock]).
 */
val UploadFailureReason.blocksPass: Boolean
    get() = this == UploadFailureReason.QuotaExceeded || this == UploadFailureReason.Unauthorized

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
        UploadFailureReason.ProxyTooLarge,
        UploadFailureReason.NotAllowed,
        UploadFailureReason.FileUnreadable,
        UploadFailureReason.Rejected,
        UploadFailureReason.Unauthorized -> false
    }
