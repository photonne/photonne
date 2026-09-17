package com.photonne.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.photonne.app.ui.theme.Spacing
import kotlinx.coroutines.delay
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

/**
 * Shows [message] once on the app's snackbar and tells the owner it was shown,
 * so a result ("Ajustes guardados", "Usuario eliminado") doesn't replay on the
 * next recomposition or the next visit. The bridge between a view model's
 * one-shot result in its state and [LocalSnackbarController].
 */
@Composable
fun ResultSnackbar(message: String?, onShown: () -> Unit) {
    val snackbar = LocalSnackbarController.current
    LaunchedEffect(message) {
        if (message != null) {
            snackbar?.show(message)
            onShown()
        }
    }
}

/**
 * The app's snackbar, dropping in from the top, under the floating chrome.
 *
 * It used to come up from the bottom, over the floating nav — which is also
 * where the button that caused it usually is, under the thumb that had just
 * pressed it, on a form that may be showing the keyboard. Up top it lands
 * where the eye goes after an action and covers nothing that is being used.
 *
 * Material's `SnackbarHost` is bottom-minded (it fades and scales in place),
 * so this draws the current [SnackbarData] itself: slide + fade from above,
 * and the timeout the stock host would have run, since the state only queues
 * and waits for someone to dismiss.
 */
@Composable
fun TopSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val current = hostState.currentSnackbarData
    // Keeps the last one composed while it slides out.
    var shown by remember { mutableStateOf<SnackbarData?>(null) }
    if (current != null) shown = current

    LaunchedEffect(current) {
        if (current != null) {
            delay(
                when (current.visuals.duration) {
                    SnackbarDuration.Short -> 4_000L
                    SnackbarDuration.Long -> 10_000L
                    SnackbarDuration.Indefinite -> Long.MAX_VALUE
                }
            )
            current.dismiss()
        }
    }

    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        shown?.let { data ->
            Snackbar(
                snackbarData = data,
                modifier = Modifier
                    .padding(horizontal = Spacing.md)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}
