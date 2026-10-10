package com.photonne.app.ui.asset

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BurstMode
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.add_to_album_title
import com.photonne.app.resources.archive_action_unarchive
import com.photonne.app.resources.asset_action_analyze
import com.photonne.app.resources.asset_action_archive
import com.photonne.app.resources.asset_action_delete_device
import com.photonne.app.resources.asset_action_details
import com.photonne.app.resources.asset_action_download
import com.photonne.app.resources.asset_action_edit_date
import com.photonne.app.resources.asset_action_edit_description
import com.photonne.app.resources.asset_action_faces
import com.photonne.app.resources.asset_action_favorite_add
import com.photonne.app.resources.asset_action_favorite_remove
import com.photonne.app.resources.asset_action_more
import com.photonne.app.resources.asset_action_pick_frame
import com.photonne.app.resources.asset_action_trash
import com.photonne.app.resources.selection_label_share
import com.photonne.app.resources.trash_action_delete_forever
import com.photonne.app.resources.trash_action_restore
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.PhotonneIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Una acción del visor. [inline] decide si va a la vista (icono) o al menú ⋮;
 * el orden de la lista es el orden en los dos sitios.
 */
internal class ViewerAction(
    val icon: ImageVector,
    val label: String,
    val inline: Boolean,
    val tint: Color = Color.White,
    val onClick: () -> Unit
)

/**
 * El modelo ÚNICO de acciones del visor (Lote N1). Antes la barra inferior
 * (vertical) y la cápsula superior (apaisado) listaban cada una las suyas y
 * se habían separado: Añadir a álbum a la vista en una y en el menú en la
 * otra, la papelera al revés, Info solo en apaisado… Ahora las dos
 * disposiciones pintan esta misma lista con [ViewerActionButtons]: mismas
 * acciones a la vista en el mismo orden y el resto en ⋮, en el mismo orden.
 *
 * Variantes por origen (Lote K): solo-dispositivo → Info y Eliminar del
 * dispositivo; Papelera → Restaurar y Eliminar definitivamente; Archivo →
 * Desarchivar en vez de Archivar. Las entradas con callback null (editar sin
 * permiso, analizar sin ser dueño o en un vídeo, elegir fotograma fuera de
 * una foto en movimiento o sin poder escribir en su carpeta) no aparecen.
 */
@Composable
internal fun viewerActions(
    item: TimelineItem,
    mode: AssetViewerMode,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onTrashRequest: () -> Unit,
    onShowInfo: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDownload: () -> Unit,
    onEditDescription: (() -> Unit)?,
    onEditDate: (() -> Unit)?,
    onOpenFaces: () -> Unit,
    onAnalyze: (() -> Unit)?,
    onPickFrame: (() -> Unit)?,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onPurgeRequest: () -> Unit,
    onDeleteFromDevice: (() -> Unit)?
): List<ViewerAction> {
    if (item.isLocalOnly) {
        return listOfNotNull(
            ViewerAction(PhotonneIcons.Info, stringResource(Res.string.asset_action_details), true, onClick = onShowInfo),
            // Straight to the platform flow — the OS shows its own
            // confirmation (see rememberDeviceMediaTrasher).
            onDeleteFromDevice?.let {
                ViewerAction(PhotonneIcons.Delete, stringResource(Res.string.asset_action_delete_device), true, onClick = it)
            }
        )
    }
    if (mode == AssetViewerMode.Trash) {
        // En la papelera la foto no se edita ni se marca: solo vuelve a su
        // sitio o se va para siempre (patrón de Google Fotos).
        return listOf(
            ViewerAction(Icons.Outlined.RestoreFromTrash, stringResource(Res.string.trash_action_restore), true, onClick = onRestore),
            ViewerAction(PhotonneIcons.DeletePermanent, stringResource(Res.string.trash_action_delete_forever), true, onClick = onPurgeRequest)
        )
    }
    val isArchiveMode = mode == AssetViewerMode.Archive
    return listOfNotNull(
        ViewerAction(
            icon = if (isFavorite) PhotonneIcons.FavoriteActive else PhotonneIcons.Favorite,
            label = stringResource(
                if (isFavorite) Res.string.asset_action_favorite_remove
                else Res.string.asset_action_favorite_add
            ),
            inline = true,
            tint = if (isFavorite) PhotonneColors.favorite else Color.White,
            onClick = onToggleFavorite
        ),
        ViewerAction(PhotonneIcons.Share, stringResource(Res.string.selection_label_share), true, onClick = onShare),
        ViewerAction(PhotonneIcons.Delete, stringResource(Res.string.asset_action_trash), true, onClick = onTrashRequest),
        ViewerAction(PhotonneIcons.Info, stringResource(Res.string.asset_action_details), true, onClick = onShowInfo),
        ViewerAction(PhotonneIcons.AddToAlbum, stringResource(Res.string.add_to_album_title), false, onClick = onAddToAlbum),
        ViewerAction(PhotonneIcons.Download, stringResource(Res.string.asset_action_download), false, onClick = onDownload),
        onEditDescription?.let {
            ViewerAction(PhotonneIcons.Edit, stringResource(Res.string.asset_action_edit_description), false, onClick = it)
        },
        onEditDate?.let {
            ViewerAction(Icons.Outlined.DateRange, stringResource(Res.string.asset_action_edit_date), false, onClick = it)
        },
        ViewerAction(Icons.Outlined.Face, stringResource(Res.string.asset_action_faces), false, onClick = onOpenFaces),
        onAnalyze?.let {
            ViewerAction(Icons.Outlined.AutoAwesome, stringResource(Res.string.asset_action_analyze), false, onClick = it)
        },
        onPickFrame?.let {
            ViewerAction(Icons.Outlined.BurstMode, stringResource(Res.string.asset_action_pick_frame), false, onClick = it)
        },
        ViewerAction(
            icon = if (isArchiveMode) PhotonneIcons.Unarchive else PhotonneIcons.Archive,
            label = stringResource(
                if (isArchiveMode) Res.string.archive_action_unarchive
                else Res.string.asset_action_archive
            ),
            inline = false,
            onClick = onArchive
        )
    )
}

/**
 * Pinta [actions]: las `inline` como iconos, en orden, y el resto bajo un ⋮
 * (solo si hay alguna). Sin contenedor propio: la barra inferior y la cápsula
 * superior ponen su Row.
 */
@Composable
internal fun ViewerActionButtons(
    actions: List<ViewerAction>,
    showOverflow: Boolean,
    onShowOverflowChange: (Boolean) -> Unit
) {
    actions.filter { it.inline }.forEach { action ->
        IconButton(onClick = action.onClick) {
            Icon(action.icon, contentDescription = action.label, tint = action.tint)
        }
    }
    val overflow = actions.filterNot { it.inline }
    if (overflow.isEmpty()) return
    Box {
        IconButton(onClick = { onShowOverflowChange(true) }) {
            Icon(
                PhotonneIcons.More,
                contentDescription = stringResource(Res.string.asset_action_more),
                tint = Color.White
            )
        }
        DropdownMenu(
            expanded = showOverflow,
            onDismissRequest = { onShowOverflowChange(false) }
        ) {
            overflow.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        onShowOverflowChange(false)
                        action.onClick()
                    }
                )
            }
        }
    }
}
