package com.photonne.app.ui.folder

import androidx.compose.runtime.Composable
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.folder_bulk_delete_message
import com.photonne.app.resources.folder_bulk_delete_title
import com.photonne.app.resources.folder_bulk_deleted_done
import com.photonne.app.resources.folder_bulk_deleted_partial
import com.photonne.app.resources.folder_bulk_move_title
import com.photonne.app.resources.folder_bulk_moved_done
import com.photonne.app.resources.folder_bulk_moved_partial
import com.photonne.app.resources.folder_move_title
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.main.SnackbarController
import com.photonne.app.ui.selection.bulkResultMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Manda a la papelera varias carpetas seleccionadas en la lista: misma llamada
 * que el borrado de una (y, como ella, sin Deshacer), una a una. Cuenta solo
 * las de más arriba: las anidadas en otra seleccionada caen con su padre.
 */
@Composable
fun FoldersBulkDeleteDialog(
    state: FoldersUiState,
    viewModel: FoldersViewModel,
    snackbar: SnackbarController?,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val count = topmostFolders(state.selectedFolders).size
    ConfirmActionDialog(
        title = pluralStringResource(Res.plurals.folder_bulk_delete_title, count, count),
        message = pluralStringResource(Res.plurals.folder_bulk_delete_message, count, count),
        confirmLabel = stringResource(Res.string.action_delete),
        isDestructive = true,
        isSubmitting = state.isMutating,
        onDismiss = onClose,
        onConfirm = {
            viewModel.deleteSelected { outcome ->
                onClose()
                scope.launch {
                    snackbar?.show(
                        bulkResultMessage(
                            outcome,
                            done = Res.plurals.folder_bulk_deleted_done,
                            partial = Res.string.folder_bulk_deleted_partial
                        )
                    )
                }
            }
        }
    )
}

/**
 * Mueve las carpetas seleccionadas en la lista (una o varias) con el mismo
 * selector que el detalle de carpeta; se poda el subárbol de cada una para que
 * ninguna acabe dentro de sí misma.
 */
@Composable
fun FoldersBulkMoveDialog(
    state: FoldersUiState,
    viewModel: FoldersViewModel,
    snackbar: SnackbarController?,
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    val sources = topmostFolders(state.selectedFolders)
    val count = sources.size
    // Si todas cuelgan del mismo padre y es un destino listado, queda
    // preseleccionado, como al mover una desde su detalle.
    val commonParent = sources.map { it.parentFolderId }.distinct().singleOrNull()
    FolderPickerDialog(
        title = if (count == 1) stringResource(Res.string.folder_move_title)
        else pluralStringResource(Res.plurals.folder_bulk_move_title, count, count),
        folders = state.moveDestinations,
        isSubmitting = state.isMutating,
        excludeFolderIds = sources.mapTo(mutableSetOf()) { it.id },
        includeRoot = true,
        initialSelectionId = commonParent?.takeIf { parentId ->
            state.moveDestinations.any { it.id == parentId }
        },
        onDismiss = onClose,
        onConfirm = { targetParentId, _ ->
            viewModel.moveSelected(targetParentId) { outcome ->
                onClose()
                scope.launch {
                    snackbar?.show(
                        bulkResultMessage(
                            outcome,
                            done = Res.plurals.folder_bulk_moved_done,
                            partial = Res.string.folder_bulk_moved_partial
                        )
                    )
                }
            }
        }
    )
}
