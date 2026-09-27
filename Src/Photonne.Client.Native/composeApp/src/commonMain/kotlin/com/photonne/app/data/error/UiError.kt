package com.photonne.app.data.error

import com.photonne.app.PhotonneVersion
import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.api.SessionRefreshUnreachableConnectException
import kotlin.time.Clock
import kotlin.time.Instant

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
 * Mensajes de respaldo de las acciones masivas que se repiten en varios
 * ViewModels. Un único punto evita que las copias diverjan; cuando se decida
 * el patrón de i18n de los ViewModels (punto 43 del roadmap) bastará con
 * migrar este objeto.
 */
object ErrorMessages {
    const val ARCHIVE_FAILED = "No se pudo archivar"
    const val TRASH_FAILED = "No se pudo mover a la papelera"
    const val RESTORE_FAILED = "No se pudo restaurar"
    const val UNARCHIVE_FAILED = "No se pudo desarchivar"
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
    val userMessage = if (this is SessionRefreshUnreachableConnectException) {
        // Un 401 cuyo refresco no llegó al servidor: la sesión sigue viva, lo
        // que falla es la conexión. Nunca "Sesión expirada".
        CONNECTION_FAILED_MESSAGE
    } else {
        userMessageFor(apiEx, fallback)
    }
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

private const val CONNECTION_FAILED_MESSAGE =
    "No se pudo conectar con el servidor. Comprueba la conexión e inténtalo de nuevo."

/**
 * En los errores de validación/estado (400, 404, 409, 422) el servidor suele
 * explicar el motivo exacto ("La contraseña actual no es correcta"); ese texto
 * gana al genérico de la pantalla. [PhotonneApiException.serverMessage] ya
 * viene filtrado: si el cuerpo no traía un texto legible es `null` y se usa
 * [fallback] como siempre.
 */
private fun userMessageFor(api: PhotonneApiException?, fallback: String): String =
    when (api?.status) {
        null -> fallback
        401 -> "Sesión expirada. Vuelve a iniciar sesión."
        403 -> "No tienes permiso para esta acción."
        400, 404, 409, 422 -> api.serverMessage ?: fallback
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
