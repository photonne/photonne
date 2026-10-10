package com.photonne.app.ui.album

import androidx.compose.runtime.Composable
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_leave
import com.photonne.app.resources.album_bulk_delete_message
import com.photonne.app.resources.album_bulk_delete_title
import com.photonne.app.resources.album_bulk_deleted_done
import com.photonne.app.resources.album_bulk_deleted_partial
import com.photonne.app.resources.album_bulk_leave_message
import com.photonne.app.resources.album_bulk_leave_title
import com.photonne.app.resources.album_bulk_left_done
import com.photonne.app.resources.album_bulk_left_partial
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.main.SnackbarController
import com.photonne.app.ui.selection.bulkResultMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmación de borrar ([leaving] = false) o salir de los álbumes
 * seleccionados en la lista. Se queda abierta con el spinner mientras corre el
 * lote y al terminar el snackbar dice cuántos salieron bien.
 *
 * [scope] es el del host: el mensaje se resuelve después de cerrar el diálogo.
 */
@Composable
fun AlbumsBulkConfirmDialog(
    leaving: Boolean,
    state: AlbumsUiState,
    viewModel: AlbumsViewModel,
    snackbar: SnackbarController?,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val count = state.selectedAlbums.size
    ConfirmActionDialog(
        title = pluralStringResource(
            if (leaving) Res.plurals.album_bulk_leave_title
            else Res.plurals.album_bulk_delete_title,
            count, count
        ),
        message = pluralStringResource(
            if (leaving) Res.plurals.album_bulk_leave_message
            else Res.plurals.album_bulk_delete_message,
            count, count
        ),
        confirmLabel = stringResource(
            if (leaving) Res.string.action_leave else Res.string.action_delete
        ),
        isDestructive = true,
        isSubmitting = state.isMutating,
        onDismiss = onClose,
        onConfirm = {
            val onResult: (com.photonne.app.ui.selection.BulkOutcome) -> Unit = { outcome ->
                onClose()
                scope.launch {
                    snackbar?.show(
                        bulkResultMessage(
                            outcome,
                            done = if (leaving) Res.plurals.album_bulk_left_done
                            else Res.plurals.album_bulk_deleted_done,
                            partial = if (leaving) Res.string.album_bulk_left_partial
                            else Res.string.album_bulk_deleted_partial
                        )
                    )
                }
            }
            if (leaving) viewModel.leaveSelected(onResult)
            else viewModel.deleteSelected(onResult)
        }
    )
}
