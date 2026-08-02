package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * Diálogo de confirmación único de la app.
 *
 * Lo usan las acciones que NO se pueden deshacer: vaciar la papelera, borrar
 * para siempre, borrar un álbum o una carpeta, borrar un usuario. Lo
 * reversible (papelera, archivar) ya no pregunta — se ejecuta y ofrece
 * deshacer en el snackbar.
 *
 * [errorMessage] es lo que antes obligaba a media docena de pantallas a
 * escribir su propio `AlertDialog`: un borrado que falla tiene que contarlo
 * SIN cerrarse, para poder reintentar sin volver a navegar hasta la acción.
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    isDestructive: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    errorMessage: String? = null
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(message)
                if (errorMessage != null) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting) {
                Text(
                    confirmLabel,
                    color = if (isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
