package com.photonne.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * El destructivo de una pantalla ("Borrar usuario", "Borrar biblioteca",
 * "Borrar cuenta"): el mismo [SecondaryActionButton] con contorno y texto en
 * `error`. Antes cada pantalla lo pintaba a mano, unas a 56 dp y otras a la
 * altura por defecto de Material, debajo de un principal de 56 dp.
 */
@Composable
fun DestructiveActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val error = MaterialTheme.colorScheme.error
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = error),
        border = BorderStroke(1.dp, if (enabled && !isLoading) error else error.copy(alpha = 0.38f)),
        modifier = modifier.fillMaxWidth().actionButtonHeight()
    ) {
        if (isLoading) ButtonLoadingIndicator() else Text(label)
    }
}

/*
 * Botones compactos: los del pie de una hoja (SheetActions) y los de un
 * diálogo. Píldora a la altura estándar de Material (40 dp), como los
 * `.b-pill` de la guía Cristal y los botones del Dialog de la SPA: la acción
 * rellena (oro, o rojo si es irreversible) y la de cerrar con contorno. Antes
 * las hojas con un campo llevaban un botón de 56 dp a todo el ancho, las de
 * gestión una fila de TextButton que parecían enlaces y los diálogos dos
 * TextButton que solo se distinguían por el color del texto.
 */

/** Spinner de un botón compacto: el del tamaño de un icono de píldora. */
@Composable
private fun CompactLoadingIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(IconSize.chip),
        strokeWidth = 2.dp,
        color = LocalContentColor.current
    )
}

/**
 * Contenido de un botón compacto. Con [isLoading] el spinner se pinta encima
 * de la etiqueta transparente, así el botón no cambia de ancho al enviar.
 */
@Composable
private fun CompactButtonContent(label: String, icon: ImageVector?, isLoading: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (isLoading) Modifier.alpha(0f) else Modifier
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.chip))
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(label)
        }
        if (isLoading) CompactLoadingIndicator()
    }
}

/**
 * La acción de una hoja o un diálogo ("Guardar", "Crear", "Nuevo enlace",
 * "Borrar"). [destructive] la rellena de `error` para lo que no se puede
 * deshacer. [isLoading] la deshabilita por sí solo.
 */
@Composable
fun CompactPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        } else {
            ButtonDefaults.buttonColors()
        },
        contentPadding = if (icon != null) ButtonDefaults.ButtonWithIconContentPadding
        else ButtonDefaults.ContentPadding,
        modifier = modifier
    ) {
        CompactButtonContent(label, icon, isLoading)
    }
}

/** La de al lado de [CompactPrimaryButton]: "Cancelar", "Cerrar", "Ignorar". */
@Composable
fun CompactSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        contentPadding = if (icon != null) ButtonDefaults.ButtonWithIconContentPadding
        else ButtonDefaults.ContentPadding,
        modifier = modifier
    ) {
        CompactButtonContent(label, icon, isLoading)
    }
}
