package com.photonne.app.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * OutlinedTextField defaults to ~56dp tall; primary action buttons that sit
 * underneath form fields look skinny next to them, and an inline
 * CircularProgressIndicator (which is 40dp by default) overflows the button.
 * Apply this modifier to buttons so they line up with the fields above.
 */
val ActionButtonHeight = 56.dp

fun Modifier.actionButtonHeight(): Modifier = heightIn(min = ActionButtonHeight)

/** Loading indicator sized to sit comfortably inside an [actionButtonHeight] button. */
@Composable
fun ButtonLoadingIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(24.dp),
        strokeWidth = 2.5.dp
    )
}

/**
 * The main button of a form or a task page ("Guardar", "Crear", "Iniciar"):
 * full width at [actionButtonHeight], with the spinner taking the label's
 * place while the request is out. Login drew it like this; Perfil, Seguridad
 * and every admin form had it end-aligned with a spinner beside it, so the
 * same action sat in a different place depending on the screen.
 *
 * [isLoading] disables the button on its own: callers don't have to fold it
 * into [enabled].
 */
@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors = colors,
        modifier = modifier.fillMaxWidth().actionButtonHeight()
    ) {
        if (isLoading) ButtonLoadingIndicator() else Text(label)
    }
}

/** The outlined counterpart for the action next to the main one ("Probar
 *  conexión", "Cancelar", "Limpiar caducados"). Same width and height, so a
 *  pair of them stacks as one block. */
@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.fillMaxWidth().actionButtonHeight()
    ) {
        if (isLoading) ButtonLoadingIndicator() else Text(label)
    }
}
