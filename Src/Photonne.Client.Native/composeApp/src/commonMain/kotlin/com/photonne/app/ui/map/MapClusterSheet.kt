package com.photonne.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.grid.AssetGridCell
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.data.models.MapPoint
import com.photonne.app.resources.Res
import com.photonne.app.resources.asset_trash_title
import com.photonne.app.resources.map_cluster_sheet_title
import com.photonne.app.resources.selection_action_add_to_album
import com.photonne.app.resources.selection_action_archive
import com.photonne.app.resources.selection_archive_confirm_message
import com.photonne.app.resources.selection_archive_confirm_title
import com.photonne.app.resources.selection_archive_done
import com.photonne.app.resources.selection_action_close
import com.photonne.app.resources.selection_action_trash
import com.photonne.app.resources.selection_count
import com.photonne.app.resources.selection_trash_confirm_message
import com.photonne.app.resources.selection_trash_done
import com.photonne.app.resources.selection_deleted_permanently_done
import com.photonne.app.resources.trash_disabled_delete_confirm
import com.photonne.app.resources.trash_disabled_delete_message
import com.photonne.app.resources.trash_disabled_delete_title
import com.photonne.app.resources.selection_action_deselect_all
import com.photonne.app.resources.selection_action_select_all
import com.photonne.app.ui.main.LocalSnackbarController
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.SheetHeader

/**
 * Bottom sheet that drops in when the user taps a cluster marker. Mirrors
 * the PWA `Map.razor` drawer: the app's common thumbnail grid cell, long-press
 * to enter selection mode, header that swaps into a bulk-action toolbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapClusterSheet(
    points: List<MapPoint>,
    baseUrl: String,
    selectedIds: Set<String>,
    isMutating: Boolean,
    onDismiss: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    if (points.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSelectionActive = selectedIds.isNotEmpty()
    val items = remember(points) { points.map { it.toSyntheticItem() } }

    // El mapa tiene su propio camino de selección (no pasa por AssetSelectionBottomBar),
    // así que la papelera en bloque confirma aquí igual que en el resto de pantallas.
    var showTrashConfirm by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarController.current
    val trashDoneMessage = pluralStringResource(
        Res.plurals.selection_trash_done,
        selectedIds.size,
        selectedIds.size
    )
    // Archivar en bloque también confirma: aquí no hay barra con Deshacer.
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val archiveDoneMessage = pluralStringResource(
        Res.plurals.selection_archive_done,
        selectedIds.size,
        selectedIds.size
    )
    // Papelera apagada en el servidor ⇒ el borrado es definitivo: dilo.
    val trashEnabled = com.photonne.app.ui.actions.rememberServerTrashEnabled()
    val deletedDoneMessage = pluralStringResource(
        Res.plurals.selection_deleted_permanently_done,
        selectedIds.size,
        selectedIds.size
    )
    if (showTrashConfirm) {
        ConfirmActionDialog(
            title = stringResource(
                if (trashEnabled) Res.string.asset_trash_title
                else Res.string.trash_disabled_delete_title
            ),
            message = pluralStringResource(
                if (trashEnabled) Res.plurals.selection_trash_confirm_message
                else Res.plurals.trash_disabled_delete_message,
                selectedIds.size,
                selectedIds.size
            ),
            confirmLabel = stringResource(
                if (trashEnabled) Res.string.selection_action_trash
                else Res.string.trash_disabled_delete_confirm
            ),
            isDestructive = true,
            isSubmitting = isMutating,
            onDismiss = { showTrashConfirm = false },
            onConfirm = {
                showTrashConfirm = false
                onTrash()
                snackbar?.show(if (trashEnabled) trashDoneMessage else deletedDoneMessage)
            }
        )
    }
    if (showArchiveConfirm) {
        ConfirmActionDialog(
            title = stringResource(Res.string.selection_archive_confirm_title),
            message = pluralStringResource(
                Res.plurals.selection_archive_confirm_message,
                selectedIds.size,
                selectedIds.size
            ),
            confirmLabel = stringResource(Res.string.selection_action_archive),
            isDestructive = false,
            isSubmitting = isMutating,
            onDismiss = { showArchiveConfirm = false },
            onConfirm = {
                showArchiveConfirm = false
                onArchive()
                snackbar?.show(archiveDoneMessage)
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isMutating) onDismiss() },
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
            if (isSelectionActive) {
                SelectionHeader(
                    selectedCount = selectedIds.size,
                    totalCount = points.size,
                    isMutating = isMutating,
                    onExit = onExitSelection,
                    onSelectAll = onSelectAll,
                    onAddToAlbum = onAddToAlbum,
                    onArchive = { showArchiveConfirm = true },
                    onTrash = { showTrashConfirm = true }
                )
            } else {
                SheetHeader(
                    title = pluralStringResource(Res.plurals.map_cluster_sheet_title, points.size, points.size),
                    modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md)
                )
            }

            // La misma celda que el resto de rejillas (AssetGridCell): check de
            // selección, halo y encogido animado, clic derecho y Ctrl/Cmd+clic en
            // escritorio. No es `AssetGrid` entera porque esa ocupa todo el alto
            // disponible y aquí la hoja se ciñe al número de fotos.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    AssetGridCell(
                        modifier = Modifier.animateItem(),
                        asset = item,
                        baseUrl = baseUrl,
                        onClick = {
                            if (isSelectionActive) onToggleSelection(item.id)
                            else onPhotoClick(index)
                        },
                        onLongClick = { onToggleSelection(item.id) },
                        onToggleClick = { onToggleSelection(item.id) },
                        isSelected = item.id in selectedIds
                    )
                }
            }
        }
    }
}

/**
 * Cabecera de selección dentro de la hoja. No puede ser la barra flotante de
 * [com.photonne.app.ui.main.AssetSelectionTopBar] (la hoja tapa la pantalla),
 * pero copia su forma: cerrar, recuento en `titleMedium` y el mismo botón de
 * seleccionar/deseleccionar todo; las acciones van a continuación.
 */
@Composable
private fun SelectionHeader(
    selectedCount: Int,
    totalCount: Int,
    isMutating: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit, enabled = !isMutating) {
            Icon(
                PhotonneIcons.Close,
                contentDescription = stringResource(Res.string.selection_action_close)
            )
        }
        Text(
            text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Spacing.xs)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll, enabled = !isMutating) {
            Icon(
                PhotonneIcons.SelectAll,
                contentDescription = stringResource(
                    if (allSelected) Res.string.selection_action_deselect_all
                    else Res.string.selection_action_select_all
                ),
                tint = if (allSelected) MaterialTheme.colorScheme.primary
                else LocalContentColor.current
            )
        }
        IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
            Icon(
                PhotonneIcons.AddToAlbum,
                contentDescription = stringResource(Res.string.selection_action_add_to_album)
            )
        }
        IconButton(onClick = onArchive, enabled = !isMutating) {
            Icon(
                PhotonneIcons.Archive,
                contentDescription = stringResource(Res.string.selection_action_archive)
            )
        }
        IconButton(onClick = onTrash, enabled = !isMutating) {
            Icon(
                PhotonneIcons.Delete,
                contentDescription = stringResource(Res.string.selection_action_trash),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
