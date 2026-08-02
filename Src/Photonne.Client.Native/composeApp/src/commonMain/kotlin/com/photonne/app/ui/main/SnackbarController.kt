package com.photonne.app.ui.main

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
    /**
     * Mensaje breve, opcionalmente con una acción.
     *
     * [actionLabel] + [onAction] son lo que permite ofrecer "Deshacer" en las
     * acciones reversibles. Sin ellos, la única forma de proteger un borrado
     * era un diálogo de confirmación PREVIO, que cobra fricción en cada acción
     * para cubrir el error ocasional; con deshacer, el reparto se invierte.
     *
     * Se usa [SnackbarDuration.Long] cuando hay acción: cuatro segundos es
     * poco para leer el mensaje y decidir.
     */
    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        if (message.isBlank()) return
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel.takeIf { onAction != null },
                withDismissAction = false,
                duration = if (onAction != null) SnackbarDuration.Long
                else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
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
