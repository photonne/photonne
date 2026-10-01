@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.photonne.app

import androidx.compose.runtime.Composable
import com.photonne.app.resources.organize_skipped_done
import com.photonne.app.resources.action_undo
import com.photonne.app.resources.album_trash_warning
import com.photonne.app.resources.album_trash_warning_shared
import com.photonne.app.resources.selection_trash_blocked_foreign
import com.photonne.app.resources.selection_move_blocked_read_only
import com.photonne.app.resources.selection_trash_blocked_folder
import com.photonne.app.resources.selection_removed_from_album_done
import com.photonne.app.resources.Res
import com.photonne.app.resources.album_bulk_delete_not_allowed
import com.photonne.app.resources.folder_bulk_delete_not_allowed
import com.photonne.app.resources.utilities_duplicates_action_delete
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.actions.AssetActionWorking
import com.photonne.app.ui.main.ArchiveMode
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.main.AssetSelectionBottomBar
import com.photonne.app.ui.main.MainTab
import kotlinx.coroutines.launch

/**
 * Barra inferior del Scaffold de [AuthenticatedApp], o null para la nav flotante
 * por defecto. Devuelve la lambda (no la pinta) para que MainScaffold siga
 * recibiendo el mismo `bottomBar` nullable que cuando el `when` estaba en línea.
 */
@Composable
internal fun buildBottomBar(host: AuthenticatedChromeHost): (@Composable () -> Unit)? {
    with(host) {
        // While any multi-asset selection is active, the bottom navigation is
        // replaced by an action bar so the primary actions sit within thumb
        // reach on mobile. The slim selection top bar above keeps just Close + count.
        val bottomBar: (@Composable () -> Unit)? = when {
            appState.selectedTab == MainTab.Timeline &&
                timelineState.isSelectionActive -> {
                {
                    AssetSelectionBottomBar(
                        selectedCount = timelineState.selection.size,
                        isMutating = timelineState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onShare = {
                            actionsViewModel.beginShare(timelineState.selection.toList())
                        },
                        onAddToAlbum = { appState.bulkAddToAlbum = true },
                        onMove = { appState.showMoveSelectedAssetsTimeline = true },
                        onDownload = {
                            actionsViewModel.download(timelineState.selection.toList())
                        },
                        onArchive = timelineViewModel::bulkArchive,
                        onTrash = timelineViewModel::bulkTrash,
                        selectedIds = { timelineState.selection.toList() },
                        allFavorite = selectionAllFavorite(timelineState.loadedItems, timelineState.selection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    timelineState.loadedItems, timelineState.selection, favoriteSnackbar,
                                    timelineViewModel::clearSelection
                                )
                            }
                        },
                        onUndo = { kind, ids ->
                            actionsViewModel.undoBulk(kind, ids)
                        },
                    )
                }
            }
            appState.selectedTab == MainTab.Albums && appState.selectedAlbum != null &&
                albumDetailState.isSelectionActive -> {
                {
                    // Textos del snackbar de "Quitar del álbum", resueltos en
                    // composición (en el callback ya no hay recursos).
                    val removeCount = albumDetailState.selection.size
                    val removedFromAlbumMessage = pluralStringResource(
                        Res.plurals.selection_removed_from_album_done, removeCount, removeCount
                    )
                    val removeUndoLabel = stringResource(Res.string.action_undo)
                    val removeSnackbar = LocalSnackbarController.current
                    // Un álbum compartido mezcla fotos de varios miembros: una ajena
                    // hacía fallar la papelera de todo el lote. Se desactiva y se
                    // explica. Y como mandar a la papelera saca la foto de TODOS los
                    // álbumes (y restaurarla no la devuelve), se avisa antes.
                    val untrashable = com.photonne.app.ui.actions.countUntrashable(
                        albumDetailState.items, albumDetailState.selection, user.user.username
                    )
                    val albumTrashBlocked = if (untrashable > 0) pluralStringResource(
                        Res.plurals.selection_trash_blocked_foreign, untrashable, untrashable
                    ) else null
                    val albumTrashWarning = pluralStringResource(
                        if (appState.selectedAlbum?.isShared == true) Res.plurals.album_trash_warning_shared
                        else Res.plurals.album_trash_warning,
                        removeCount,
                        removeCount
                    )
                    AssetSelectionBottomBar(
                        selectedCount = albumDetailState.selection.size,
                        trashDisabledReason = albumTrashBlocked,
                        trashConfirmMessage = albumTrashWarning,
                        isMutating = albumDetailState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onShare = {
                            actionsViewModel.beginShare(albumDetailState.selection.toList())
                        },
                        onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Album },
                        onDownload = {
                            actionsViewModel.download(albumDetailState.selection.toList())
                        },
                        onArchive = albumDetailViewModel::bulkArchive,
                        onTrash = albumDetailViewModel::bulkTrash,
                        selectedIds = { albumDetailState.selection.toList() },
                        allFavorite = selectionAllFavorite(albumDetailState.items, albumDetailState.selection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    albumDetailState.items, albumDetailState.selection, favoriteSnackbar,
                                    albumDetailViewModel::clearSelection
                                )
                            }
                        },
                        onUndo = { kind, ids ->
                            actionsViewModel.undoBulk(kind, ids)
                        },
                        onMove = run {
                            val unmovable = com.photonne.app.ui.actions.countUnmovable(
                                albumDetailState.items, albumDetailState.selection
                            )
                            val moveBlocked = if (unmovable > 0) pluralStringResource(
                                Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                            ) else null
                            val moveSnackbar = LocalSnackbarController.current
                            {
                                if (moveBlocked != null) {
                                    moveSnackbar?.show(moveBlocked)
                                } else {
                                    appState.moveSelectionError = null
                                    appState.moveSelectionRequest = MoveSelectionRequest(
                                        assetIds = albumDetailState.selection.toList(),
                                        onMoved = albumDetailViewModel::clearSelection
                                    )
                                }
                            }
                        },
                        // En un álbum inteligente el contenido lo deciden las
                        // reglas: quitar fotos no aplica.
                        onRemoveFromAlbum = if (appState.selectedAlbum?.isSmart != true &&
                            (appState.selectedAlbum?.canWrite == true ||
                                appState.selectedAlbum?.isOwner == true)
                        ) {
                            {
                                val albumId = appState.selectedAlbum?.id
                                albumDetailViewModel.bulkRemoveFromAlbum(
                                    onSuccess = { removed ->
                                        appState.selectedAlbum?.let {
                                            albumsViewModel.applyAssetsRemoved(it.id, removed)
                                        }
                                    },
                                    onResult = { removedIds, error ->
                                        if (error != null) {
                                            removeSnackbar?.show(error.userMessage)
                                        } else {
                                            removeSnackbar?.show(
                                                removedFromAlbumMessage,
                                                removeUndoLabel
                                            ) {
                                                if (albumId != null) {
                                                    coroutineScope.launch {
                                                        runCatching {
                                                            albumsRepository.addAssetsBatch(
                                                                albumId, removedIds
                                                            )
                                                        }.onSuccess {
                                                            albumDetailViewModel.refresh()
                                                            albumsViewModel.applyAssetsAdded(
                                                                albumId, removedIds.size
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        } else null,
                        // La portada sí aplica a un álbum inteligente: no cambia qué
                        // fotos entran, y el servidor valida que la foto cumpla la regla.
                        onSetAsCover = if (albumDetailState.selection.size == 1 &&
                            (appState.selectedAlbum?.canWrite == true || appState.selectedAlbum?.isOwner == true)
                        ) {
                            {
                                val assetId = albumDetailState.selection.first()
                                albumDetailViewModel.setCover(assetId) { updated ->
                                    albumsViewModel.applyUpdate(updated)
                                    appState.selectedAlbum = appState.selectedAlbum?.copy(
                                        coverThumbnailUrl = updated.coverThumbnailUrl
                                    )
                                    albumDetailViewModel.clearSelection()
                                }
                            }
                        } else null
                    )
                }
            }
            appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
                folderDetailState.isSubfolderSelectionActive -> {
                val subfolder = folderDetailState.selectedSubfolder
                if (subfolder != null) {
                    {
                        // Members management for a subfolder is reached by opening it
                        // and using the detail top bar; the selection bar stays focused
                        // on the rename/delete the user asked for.
                        // Mismos flags que el servidor (CanWrite/CanDelete) y nada
                        // de mutar una biblioteca externa, como en la lista.
                        val subfolderIsExternal = subfolder.externalLibraryId != null
                        com.photonne.app.ui.main.FolderCardSelectionBottomBar(
                            canManageMembers = false,
                            canRename = subfolder.canWrite && !subfolderIsExternal,
                            canDelete = subfolder.canDelete && !subfolderIsExternal,
                            isMutating = folderDetailState.isMutating,
                            onManageMembers = {},
                            onRename = { appState.showEditSubfolder = true },
                            onDelete = { appState.showDeleteSubfolder = true }
                        )
                    }
                } else null
            }
            appState.selectedTab == MainTab.Folders && appState.selectedFolder != null &&
                folderDetailState.isSelectionActive -> {
                {
                    // Sin CanDelete en la carpeta (o en una biblioteca externa) el
                    // servidor rechaza la papelera: se desactiva con el motivo.
                    val folderTrashBlocked = if (appState.selectedFolder?.canDelete == false ||
                        appState.selectedFolder?.externalLibraryId != null
                    ) stringResource(Res.string.selection_trash_blocked_folder) else null
                    AssetSelectionBottomBar(
                        selectedCount = folderDetailState.selection.size,
                        trashDisabledReason = folderTrashBlocked,
                        isMutating = folderDetailState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onShare = {
                            actionsViewModel.beginShare(folderDetailState.selection.toList())
                        },
                        onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Folder },
                        onDownload = {
                            actionsViewModel.download(folderDetailState.selection.toList())
                        },
                        onArchive = folderDetailViewModel::bulkArchive,
                        onTrash = folderDetailViewModel::bulkTrash,
                        selectedIds = { folderDetailState.selection.toList() },
                        allFavorite = selectionAllFavorite(folderDetailState.items, folderDetailState.selection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    folderDetailState.items, folderDetailState.selection, favoriteSnackbar,
                                    folderDetailViewModel::clearSelection
                                )
                            }
                        },
                        onUndo = { kind, ids ->
                            actionsViewModel.undoBulk(kind, ids)
                        },
                        // El servidor pide escritura en la carpeta de origen, no
                        // ser su dueño.
                        onMove = if (appState.selectedFolder?.canWrite == true &&
                            appState.selectedFolder?.externalLibraryId == null
                        ) {
                            { appState.showMoveSelectedAssets = true }
                        } else null
                    )
                }
            }
            appState.selectedTab == MainTab.Search && searchState.isSelectionActive -> {
                {
                    AssetSelectionBottomBar(
                        selectedCount = searchState.selection.size,
                        isMutating = searchState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onShare = {
                            actionsViewModel.beginShare(searchState.selection.toList())
                        },
                        onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Search },
                        onDownload = {
                            actionsViewModel.download(searchState.selection.toList())
                        },
                        onArchive = searchViewModel::bulkArchive,
                        onTrash = searchViewModel::bulkTrash,
                        selectedIds = { searchState.selection.toList() },
                        allFavorite = selectionAllFavorite(searchState.results, searchState.selection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    searchState.results, searchState.selection, favoriteSnackbar,
                                    searchViewModel::clearSelection
                                )
                            }
                        },
                        onUndo = { kind, ids ->
                            actionsViewModel.undoBulk(kind, ids)
                        },
                        onMove = run {
                            val unmovable = com.photonne.app.ui.actions.countUnmovable(
                                searchState.results, searchState.selection
                            )
                            val moveBlocked = if (unmovable > 0) pluralStringResource(
                                Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                            ) else null
                            val moveSnackbar = LocalSnackbarController.current
                            {
                                if (moveBlocked != null) {
                                    moveSnackbar?.show(moveBlocked)
                                } else {
                                    appState.moveSelectionError = null
                                    appState.moveSelectionRequest = MoveSelectionRequest(
                                        assetIds = searchState.selection.toList(),
                                        onMoved = searchViewModel::clearSelection
                                    )
                                }
                            }
                        },
                    )
                }
            }
            appState.moreSubscreen == MoreSubscreen.People &&
                appState.selectedPerson != null && personDetailState.isSelectionActive -> {
                {
                    AssetSelectionBottomBar(
                        selectedCount = personDetailState.selection.size,
                        isMutating = personDetailState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onShare = {
                            actionsViewModel.beginShare(personDetailState.selection.toList())
                        },
                        onAddToAlbum = { appState.bulkAddSource = BulkAddSource.People },
                        onDownload = {
                            actionsViewModel.download(personDetailState.selection.toList())
                        },
                        onArchive = personDetailViewModel::bulkArchive,
                        onTrash = personDetailViewModel::bulkTrash,
                        selectedIds = { personDetailState.selection.toList() },
                        allFavorite = selectionAllFavorite(personDetailState.items, personDetailState.selection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    personDetailState.items, personDetailState.selection, favoriteSnackbar,
                                    personDetailViewModel::clearSelection
                                )
                            }
                        },
                        onUndo = { kind, ids ->
                            actionsViewModel.undoBulk(kind, ids)
                        },
                        onUnlink = {
                            personDetailViewModel.bulkUnlinkFromPerson { detached ->
                                // Local fan-out: faces removed from a person also
                                // shrink that person's face count in the list.
                                appState.selectedPerson?.let { p ->
                                    val newCount = (p.faceCount - detached).coerceAtLeast(0)
                                    appState.selectedPerson = p.copy(faceCount = newCount)
                                }
                            }
                        }
                    )
                }
            }
            appState.moreSubscreen == MoreSubscreen.OrganizeInbox &&
                organizeInboxState.isSelectionActive -> {
                {
                    // Los textos se resuelven aquí, en composición: la acción de
                    // apartar corre en un callback y allí ya no hay recursos.
                    val skippedCount = organizeInboxState.selection.size
                    val skippedMessage = pluralStringResource(
                        Res.plurals.organize_skipped_done, skippedCount, skippedCount
                    )
                    val undoLabel = stringResource(Res.string.action_undo)
                    val snackbar = LocalSnackbarController.current
                    AssetSelectionBottomBar(
                        selectedCount = organizeInboxState.selection.size,
                        isMutating = organizeInboxState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onShare = {
                            actionsViewModel.beginShare(organizeInboxState.selection.toList())
                        },
                        onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Inbox },
                        onDownload = {
                            actionsViewModel.download(organizeInboxState.selection.toList())
                        },
                        onArchive = organizeInboxViewModel::bulkArchive,
                        onTrash = organizeInboxViewModel::bulkTrash,
                        selectedIds = { organizeInboxState.selection.toList() },
                        allFavorite = selectionAllFavorite(organizeInboxState.items, organizeInboxState.selection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    organizeInboxState.items, organizeInboxState.selection, favoriteSnackbar,
                                    organizeInboxViewModel::clearSelection
                                )
                            }
                        },
                        onUndo = { kind, ids ->
                            actionsViewModel.undoBulk(kind, ids) { organizeInboxViewModel.refresh() }
                        },
                        onMove = {
                            appState.showMoveSelectedAssetsInbox = true
                            organizeInboxViewModel.loadMoveYearBreakdown()
                        },
                        onExcludeFromOrganize = {
                            organizeInboxViewModel.excludeSelected { ids ->
                                foldersViewModel.refreshOrganizeCount()
                                snackbar?.show(
                                    message = skippedMessage,
                                    actionLabel = undoLabel
                                ) {
                                    organizeInboxViewModel.includeAgain(ids) {
                                        foldersViewModel.refreshOrganizeCount()
                                    }
                                }
                            }
                        }
                    )
                }
            }
            appState.moreSubscreen == MoreSubscreen.Favorites &&
                favoritesState.isSelectionActive -> {
                {
                    AssetSelectionBottomBar(
                        selectedCount = favoritesState.selection.size,
                        isMutating = favoritesState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        onShare = {
                            actionsViewModel.beginShare(favoritesState.selection.toList())
                        },
                        onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Favorites },
                        onDownload = {
                            actionsViewModel.download(favoritesState.selection.toList())
                        },
                        onArchive = favoritesViewModel::bulkArchive,
                        onTrash = favoritesViewModel::bulkTrash,
                        selectedIds = { favoritesState.selection.toList() },
                        allFavorite = selectionAllFavorite(favoritesState.items, favoritesState.selection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    favoritesState.items, favoritesState.selection, favoriteSnackbar,
                                    favoritesViewModel::clearSelection
                                )
                            }
                        },
                        onUndo = { kind, ids ->
                            actionsViewModel.undoBulk(kind, ids)
                        },
                        onMove = run {
                            val unmovable = com.photonne.app.ui.actions.countUnmovable(
                                favoritesState.items, favoritesState.selection
                            )
                            val moveBlocked = if (unmovable > 0) pluralStringResource(
                                Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                            ) else null
                            val moveSnackbar = LocalSnackbarController.current
                            {
                                if (moveBlocked != null) {
                                    moveSnackbar?.show(moveBlocked)
                                } else {
                                    appState.moveSelectionError = null
                                    appState.moveSelectionRequest = MoveSelectionRequest(
                                        assetIds = favoritesState.selection.toList(),
                                        onMoved = favoritesViewModel::clearSelection
                                    )
                                }
                            }
                        },
                    )
                }
            }
            // Duplicados: con algo marcado, la cápsula de confirmar borrar sustituye
            // a la nav, como la de "Mover" en Para organizar (antes era un FAB).
            appState.moreSubscreen == MoreSubscreen.UtilitiesDuplicates &&
                utilitiesDuplicatesState.totalSelectedCount > 0 -> {
                {
                    com.photonne.app.ui.main.ConfirmCapsule(
                        label = stringResource(
                            Res.string.utilities_duplicates_action_delete,
                            utilitiesDuplicatesState.totalSelectedCount,
                            com.photonne.app.ui.format.humanBytes(utilitiesDuplicatesState.totalSelectedBytes)
                        ),
                        enabled = !utilitiesDuplicatesState.isDeleting,
                        isWorking = utilitiesDuplicatesState.isDeleting,
                        icon = com.photonne.app.ui.theme.PhotonneIcons.Delete,
                        destructive = true,
                        onClick = { appState.showDuplicatesConfirm = true }
                    )
                }
            }
            appState.moreSubscreen == MoreSubscreen.Archived &&
                archivedState.isSelectionActive -> {
                {
                    AssetSelectionBottomBar(
                        selectedCount = archivedState.selection.size,
                        isMutating = archivedState.isBulkMutating ||
                            actionsState.working != AssetActionWorking.Idle,
                        archiveMode = ArchiveMode.Unarchive,
                        onShare = {
                            actionsViewModel.beginShare(archivedState.selection.toList())
                        },
                        onAddToAlbum = { appState.bulkAddSource = BulkAddSource.Archive },
                        onDownload = {
                            actionsViewModel.download(archivedState.selection.toList())
                        },
                        onArchive = { done -> archivedViewModel.bulkUnarchive(onResult = done) },
                        onTrash = archivedViewModel::bulkTrash,
                        selectedIds = { archivedState.selection.toList() },
                        allFavorite = selectionAllFavorite(archivedState.items, archivedState.selection),
                        onToggleFavorite = run {
                            val favoriteSnackbar = LocalSnackbarController.current
                            {
                                runBulkFavorite(
                                    archivedState.items, archivedState.selection, favoriteSnackbar,
                                    archivedViewModel::clearSelection
                                )
                            }
                        },
                        onUndo = { kind, ids ->
                            actionsViewModel.undoBulk(kind, ids)
                        },
                        onMove = run {
                            val unmovable = com.photonne.app.ui.actions.countUnmovable(
                                archivedState.items, archivedState.selection
                            )
                            val moveBlocked = if (unmovable > 0) pluralStringResource(
                                Res.plurals.selection_move_blocked_read_only, unmovable, unmovable
                            ) else null
                            val moveSnackbar = LocalSnackbarController.current
                            {
                                if (moveBlocked != null) {
                                    moveSnackbar?.show(moveBlocked)
                                } else {
                                    appState.moveSelectionError = null
                                    appState.moveSelectionRequest = MoveSelectionRequest(
                                        assetIds = archivedState.selection.toList(),
                                        onMoved = archivedViewModel::clearSelection
                                    )
                                }
                            }
                        },
                    )
                }
            }
            appState.selectedTab == MainTab.Albums && albumsState.isSelectionActive -> {
                val selected = albumsState.selectedAlbums
                // Una sola tarjeta: sus acciones de siempre. Varias: solo las que
                // valen para todas (albumSelectionActions), en bloque.
                val target = selected.singleOrNull()
                val allowed = com.photonne.app.ui.album.albumSelectionActions(selected)
                if (selected.isNotEmpty()) {
                    {
                        val deleteBlocked = stringResource(Res.string.album_bulk_delete_not_allowed)
                        val shortcutSnackbar = LocalSnackbarController.current
                        fun requestDelete() {
                            if (target != null) {
                                appState.pendingActionAlbum = target
                                appState.showDeleteAlbum = true
                            } else {
                                appState.showBulkDeleteAlbums = true
                            }
                        }
                        // Escritorio: Supr con tarjetas seleccionadas = Eliminar.
                        com.photonne.app.ui.selection.SelectionShortcutsHandler(
                            onDelete = {
                                when {
                                    albumsState.isMutating -> Unit
                                    !allowed.canDelete -> shortcutSnackbar?.show(deleteBlocked)
                                    else -> requestDelete()
                                }
                            }
                        )
                        val togglePin = com.photonne.app.ui.album.rememberAlbumPinToggle(albumsViewModel)
                        com.photonne.app.ui.main.AlbumCardSelectionBottomBar(
                            canPin = allowed.canPin,
                            isPinned = target?.isPinned == true,
                            onTogglePin = {
                                target?.let { togglePin(it.id); albumsViewModel.clearSelection() }
                            },
                            canManageMembers = allowed.canManageMembers,
                            canEdit = allowed.canEdit,
                            canLeave = allowed.canLeave,
                            canDelete = allowed.canDelete,
                            isMutating = albumsState.isMutating,
                            onManageMembers = {
                                if (target != null) {
                                    appState.pendingActionAlbum = target
                                    albumPermissionsViewModel.open(target.id)
                                    appState.showMembers = true
                                }
                            },
                            onEdit = {
                                // Un álbum inteligente propio se edita entero (condiciones
                                // incluidas) en su editor; el resto, nombre y descripción.
                                if (target == null) {
                                    Unit
                                } else if (target.isSmart && target.isOwner) {
                                    albumsViewModel.clearSelection()
                                    appState.editingSmartAlbum = target
                                    appState.moreSubscreen = MoreSubscreen.SmartAlbumEditor
                                } else {
                                    appState.pendingActionAlbum = target
                                    appState.showEditAlbum = true
                                }
                            },
                            onLeave = {
                                if (target != null) {
                                    appState.pendingActionAlbum = target
                                    appState.showLeaveAlbum = true
                                } else {
                                    appState.showBulkLeaveAlbums = true
                                }
                            },
                            onDelete = ::requestDelete
                        )
                    }
                } else null
            }
            appState.selectedTab == MainTab.Folders && foldersState.isSelectionActive -> {
                val selected = foldersState.selectedFolders
                val target = selected.singleOrNull()
                // Permisos por tarjeta y sin tocar bibliotecas externas: ver
                // folderSelectionActions. Con varias, solo lo que vale para todas.
                val allowed = com.photonne.app.ui.folder.folderSelectionActions(selected)
                if (selected.isNotEmpty()) {
                    {
                        val deleteBlocked = stringResource(Res.string.folder_bulk_delete_not_allowed)
                        val shortcutSnackbar = LocalSnackbarController.current
                        fun requestDelete() {
                            if (target != null) {
                                appState.pendingActionFolder = target
                                appState.showDeleteFolder = true
                            } else {
                                appState.showBulkDeleteFolders = true
                            }
                        }
                        com.photonne.app.ui.selection.SelectionShortcutsHandler(
                            onDelete = {
                                when {
                                    foldersState.isMutating -> Unit
                                    !allowed.canDelete -> shortcutSnackbar?.show(deleteBlocked)
                                    else -> requestDelete()
                                }
                            }
                        )
                        com.photonne.app.ui.main.FolderCardSelectionBottomBar(
                            canManageMembers = allowed.canManageMembers,
                            canRename = allowed.canRename,
                            canDelete = allowed.canDelete,
                            isMutating = foldersState.isMutating,
                            onManageMembers = {
                                if (target != null) {
                                    appState.pendingActionFolder = target
                                    folderPermissionsViewModel.open(target.id)
                                    appState.showFolderMembers = true
                                }
                            },
                            onRename = {
                                if (target != null) {
                                    appState.pendingActionFolder = target
                                    appState.showEditFolder = true
                                }
                            },
                            onDelete = ::requestDelete,
                            canToggleTimeline = allowed.canToggleTimeline,
                            excludedFromDiscovery = target?.excludedFromDiscovery ?: false,
                            onToggleTimeline = {
                                if (target != null) {
                                    foldersViewModel.setTimelineIncluded(
                                        folderId = target.id,
                                        included = target.excludedFromDiscovery
                                    )
                                }
                            },
                            canMove = allowed.canMove,
                            onMove = { appState.showBulkMoveFolders = true }
                        )
                    }
                } else null
            }
            else -> null
        }
        return bottomBar
    }
}
