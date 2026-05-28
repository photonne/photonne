package com.photonne.app.data.error

import com.photonne.app.PhotonneVersion
import com.photonne.app.data.api.PhotonneApiException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Error legible que un ViewModel expone a la UI. Combina un mensaje corto
 * pensado para el usuario final con un bloque opcional de [ErrorDetails]
 * que el usuario puede desplegar y copiar para enviárselo al admin.
 */
data class UiError(
    val userMessage: String,
    val technicalDetails: ErrorDetails? = null,
) {
    /** Texto plano formateado pensado para pegar en WhatsApp/email. */
    fun toCopyableText(): String = buildString {
        appendLine(userMessage)
        if (technicalDetails != null) {
            appendLine()
            appendLine("---")
            append(technicalDetails.format())
        }
    }
}

data class ErrorDetails(
    val timestamp: Instant,
    val serverBaseUrl: String?,
    val requestMethod: String?,
    val requestPath: String?,
    val httpStatus: Int?,
    val responseBody: String?,
    val clientVersion: String,
    val serverVersion: String?,
    val exceptionClass: String,
    val stackTraceHead: String?,
) {
    fun format(): String = buildString {
        appendLine("Timestamp: $timestamp")
        serverBaseUrl?.let { appendLine("Servidor:  $it") }
        if (!requestPath.isNullOrBlank()) {
            appendLine("Endpoint:  ${requestMethod ?: "?"} $requestPath")
        }
        httpStatus?.let { appendLine("HTTP:      $it") }
        appendLine("Cliente:   v$clientVersion")
        serverVersion?.let { appendLine("Versión servidor: v$it") }
        appendLine("Excepción: $exceptionClass")
        if (!responseBody.isNullOrBlank()) {
            appendLine("Respuesta:")
            appendLine(responseBody.take(2000))
        }
        if (!stackTraceHead.isNullOrBlank()) {
            appendLine("Trace:")
            append(stackTraceHead.take(1500))
        }
    }
}

/**
 * Convierte una excepción a [UiError]. [fallback] se usa como `userMessage`
 * cuando no se puede derivar uno más específico a partir del tipo concreto.
 *
 * [serverBaseUrl] y [serverVersion] los inyecta el ViewModel — esta función
 * no debería leer estado global para mantenerse pura.
 */
fun Throwable.toUiError(
    fallback: String,
    serverBaseUrl: String? = null,
    serverVersion: String? = null,
    timestamp: Instant = Clock.System.now(),
): UiError {
    val apiEx = this as? PhotonneApiException
    val requestPath = apiEx?.url?.let { extractPath(it, serverBaseUrl) }
    val userMessage = userMessageFor(apiEx, fallback)
    val details = ErrorDetails(
        timestamp = timestamp,
        serverBaseUrl = serverBaseUrl,
        requestMethod = apiEx?.method,
        requestPath = requestPath,
        httpStatus = apiEx?.status,
        responseBody = apiEx?.responseBody,
        clientVersion = PhotonneVersion,
        serverVersion = serverVersion,
        exceptionClass = this::class.simpleName ?: "Throwable",
        stackTraceHead = stackTraceFirstLines(this, limit = 10),
    )
    return UiError(userMessage = userMessage, technicalDetails = details)
}

private fun userMessageFor(api: PhotonneApiException?, fallback: String): String =
    when (api?.status) {
        null -> fallback
        401 -> "Sesión expirada. Vuelve a iniciar sesión."
        403 -> "No tienes permiso para esta acción."
        404 -> fallback
        in 500..599 -> "El servidor no pudo procesar la petición."
        else -> fallback
    }

private fun extractPath(url: String, baseUrl: String?): String {
    if (!baseUrl.isNullOrBlank() && url.startsWith(baseUrl)) {
        return url.removePrefix(baseUrl).ifBlank { "/" }
    }
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return url
    val pathStart = url.indexOf('/', startIndex = schemeEnd + 3)
    return if (pathStart < 0) "/" else url.substring(pathStart)
}

private fun stackTraceFirstLines(throwable: Throwable, limit: Int): String? {
    val raw = throwable.stackTraceToString()
    if (raw.isBlank()) return null
    return raw.lineSequence().take(limit).joinToString("\n")
}
