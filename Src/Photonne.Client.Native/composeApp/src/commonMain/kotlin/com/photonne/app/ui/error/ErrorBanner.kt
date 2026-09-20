package com.photonne.app.ui.error

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.photonne.app.data.error.UiError
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.resources.Res
import com.photonne.app.resources.error_banner_dismiss
import com.photonne.app.resources.error_banner_retry
import com.photonne.app.resources.error_banner_view_details
import com.photonne.app.resources.error_details_copied
import com.photonne.app.resources.error_details_copy
import com.photonne.app.resources.error_details_share_hint
import com.photonne.app.resources.error_details_title
import com.photonne.app.ui.theme.Spacing

/**
 * Banner de error reusable. Muestra el [UiError.userMessage] y, si hay
 * [UiError.technicalDetails], expone "Ver detalles" → abre un BottomSheet
 * con el bloque técnico completo y un botón "Copiar todo" que pone el
 * texto en el portapapeles para pegarlo en WhatsApp/email.
 *
 * @param error el error a renderizar. Si es null, no se renderiza nada.
 * @param onDismiss callback para descartar el banner. Si es null, no se
 *   muestra el botón "Cerrar" (errores no-cerrables).
 * @param onRetry callback para reintentar la acción que falló. Si es null,
 *   no se muestra el botón "Reintentar".
 * @param onCopied callback opcional para enseñar un snackbar/toast cuando
 *   se copia. Por defecto no hace nada — el sheet ya muestra confirmación.
 */
@Composable
fun ErrorBanner(
    error: UiError?,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onCopied: (() -> Unit)? = null,
) {
    if (error == null) return
    var detailsOpen by remember(error) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = Spacing.md, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = error.userMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(Res.string.error_banner_dismiss),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        if (error.technicalDetails != null || onRetry != null) {
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (error.technicalDetails != null) {
                    OutlinedButton(onClick = { detailsOpen = true }) {
                        Text(stringResource(Res.string.error_banner_view_details))
                    }
                }
                if (onRetry != null) {
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(Res.string.error_banner_retry))
                    }
                }
            }
        }
    }

    if (detailsOpen && error.technicalDetails != null) {
        ErrorDetailsSheet(
            error = error,
            onDismiss = { detailsOpen = false },
            onCopied = onCopied,
        )
    }
}

/**
 * Rama de error a pantalla completa: el banner (con reintento) dentro de una
 * columna CON scroll, para que `PullToRefreshBox` siga recibiendo el gesto y
 * el contenido nunca quede inalcanzable en pantallas bajas. El caller pasa la
 * reserva del cromo flotante en [modifier] cuando aplica.
 */
@Composable
fun FullScreenError(
    error: UiError?,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl)
    ) {
        ErrorBanner(error = error, onRetry = onRetry)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErrorDetailsSheet(
    error: UiError,
    onDismiss: () -> Unit,
    onCopied: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copiedAck by remember { mutableStateOf(false) }
    val copiedLabel = stringResource(Res.string.error_details_copied)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(PaddingValues(horizontal = 20.dp, vertical = Spacing.sm))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.error_details_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(error.toCopyableText()))
                    copiedAck = true
                    onCopied?.invoke()
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        copiedAck = false
                    }
                }) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (copiedAck) copiedLabel
                        else stringResource(Res.string.error_details_copy)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            HorizontalDivider()
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = error.userMessage,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = error.technicalDetails?.format().orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(Spacing.md)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(Res.string.error_details_share_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
