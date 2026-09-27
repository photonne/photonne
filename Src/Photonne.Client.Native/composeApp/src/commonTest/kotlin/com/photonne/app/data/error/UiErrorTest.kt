package com.photonne.app.data.error

import com.photonne.app.data.api.PhotonneApiException
import com.photonne.app.data.api.SessionRefreshUnreachableConnectException
import com.photonne.app.data.api.extractServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UiErrorTest {

    @Test
    fun server_message_wins_on_validation_errors() {
        val ex = PhotonneApiException(
            status = 400,
            message = "La contraseña actual no es correcta",
            serverMessage = "La contraseña actual no es correcta",
        )
        assertEquals(
            "La contraseña actual no es correcta",
            ex.toUiError(fallback = "No se pudo cambiar la contraseña").userMessage
        )
    }

    @Test
    fun falls_back_without_server_message() {
        val ex = PhotonneApiException(status = 409, message = "Conflict (409)")
        assertEquals("No se pudo guardar", ex.toUiError(fallback = "No se pudo guardar").userMessage)
    }

    @Test
    fun refresh_that_could_not_reach_the_server_is_a_connection_error() {
        val message = SessionRefreshUnreachableConnectException().toUiError(fallback = "x").userMessage
        assertEquals(
            "No se pudo conectar con el servidor. Comprueba la conexión e inténtalo de nuevo.",
            message
        )
    }

    @Test
    fun extracts_only_readable_server_text() {
        assertEquals("Asset no encontrado.", extractServerMessage("""{"error":"Asset no encontrado."}"""))
        assertEquals("Carpeta ocupada", extractServerMessage("""{"title":"Conflict","detail":"Carpeta ocupada"}"""))
        assertNull(extractServerMessage("Key is required"))
        assertNull(extractServerMessage("""{"error":"System.InvalidOperationException: boom"}"""))
        assertNull(extractServerMessage("""{"error":{"code":1}}"""))
        assertNull(extractServerMessage("<html>Bad gateway</html>"))
    }
}
