package com.photonne.app.ui.main

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Canal único de feedback breve ("Guardado", "Movido a la papelera"…). Antes la
 * confirmación de una acción era un texto inline en unas pantallas, un toast en
 * otras y nada en la mayoría; esto centraliza un solo [SnackbarHostState] que
 * cualquier pantalla emite vía [LocalSnackbarController].
 *
 * `show` descarta el snackbar en curso antes de enseñar el siguiente para que
 * dos acciones seguidas no encolen mensajes viejos.
 */
@Stable
class SnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun show(message: String) {
        if (message.isBlank()) return
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }
}

/** `null` fuera del árbol de la app (previews, tests); `show` es entonces un no-op vía `?.`. */
val LocalSnackbarController = staticCompositionLocalOf<SnackbarController?> { null }

@Composable
fun rememberSnackbarController(): SnackbarController {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { SnackbarController(hostState, scope) }
}
