@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.photonne.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.resources.action_logout
import com.photonne.app.resources.logout_confirm_message
import com.photonne.app.resources.member_remove_confirm_action
import com.photonne.app.resources.member_remove_confirm_message
import com.photonne.app.resources.member_remove_confirm_title
import com.photonne.app.resources.share_action_revoke
import com.photonne.app.resources.share_revoke_confirm_message
import com.photonne.app.resources.share_revoke_confirm_title
import com.photonne.app.resources.logout_confirm_pending_backup
import com.photonne.app.resources.logout_confirm_title
import com.photonne.app.resources.people_action_suggestions_accept_all
import com.photonne.app.resources.people_action_suggestions_dismiss_all
import com.photonne.app.resources.people_merge_done
import com.photonne.app.resources.people_suggestions_accept_all_message
import com.photonne.app.resources.people_suggestions_accept_all_title
import com.photonne.app.resources.people_suggestions_accepted_done
import com.photonne.app.resources.people_suggestions_dismiss_all_message
import com.photonne.app.resources.people_suggestions_dismiss_all_title
import com.photonne.app.resources.people_suggestions_dismissed_done
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_retry
import com.photonne.app.resources.action_save
import com.photonne.app.resources.album_action_edit
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.archive_action_unarchive_all_title
import com.photonne.app.resources.archive_action_unarchive_all_message
import com.photonne.app.resources.archive_action_unarchive_all
import com.photonne.app.resources.folder_action_edit
import com.photonne.app.resources.folder_action_new
import com.photonne.app.resources.folder_move_assets_title
import com.photonne.app.resources.folder_move_title
import com.photonne.app.resources.trash_action_delete_forever
import com.photonne.app.resources.trash_action_empty
import com.photonne.app.resources.trash_action_restore_all
import com.photonne.app.resources.trash_dialog_empty_message
import com.photonne.app.resources.trash_dialog_purge_message
import com.photonne.app.resources.trash_dialog_restore_all_message
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.album.AddToAlbumDialog
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumFormDialog
import com.photonne.app.ui.album.AlbumPermissionsViewModel
import com.photonne.app.ui.album.AlbumSharesViewModel
import com.photonne.app.ui.album.AlbumsViewModel
import com.photonne.app.data.models.AlbumShareLink
import com.photonne.app.ui.album.CreateShareDialog
import com.photonne.app.ui.album.EditShareDialog
import com.photonne.app.ui.album.DeleteAlbumDialog
import com.photonne.app.ui.album.InviteMemberDialog
import com.photonne.app.ui.album.LeaveAlbumDialog
import com.photonne.app.ui.album.ManagePermissionsDialog
import com.photonne.app.ui.album.ManageSharesDialog
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.photonne.app.ui.folder.DeleteFolderDialog
import com.photonne.app.ui.folder.FolderFormDialog
import com.photonne.app.ui.folder.FolderPermissionsViewModel
import com.photonne.app.ui.folder.FoldersViewModel
import com.photonne.app.ui.folder.InviteFolderMemberDialog
import com.photonne.app.ui.folder.ManageFolderPermissionsDialog
import com.photonne.app.ui.actions.AssetActionWorking
import com.photonne.app.ui.actions.DownloadFormatSheet
import com.photonne.app.ui.actions.ShareAssetsDialog
import com.photonne.app.ui.actions.ShareLinkResultDialog
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.main.MainScaffold
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.timeline.TimelineViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State

/**
 * Diálogos, hojas y overlays modales de [AuthenticatedApp], sacados a su propio
 * fichero para que el cuerpo de AuthenticatedApp no rebase el límite de 64 KB
 * por método de la JVM.
 *
 * El estado NO vive aquí: son los mismos [MutableState] que AuthenticatedApp
 * recuerda (y los [State] que recoge de sus ViewModels), expuestos como
 * propiedades delegadas para que el código de los diálogos lea y escriba
 * exactamente como cuando estaba en línea. Se reconstruye en cada composición
 * del padre, igual que antes se reevaluaba el bloque en línea.
 */
internal class AuthenticatedDialogsHost(
    selectedTab: MutableState<MainTab>,
    moreSubscreen: MutableState<MoreSubscreen?>,
    selectedAlbum: MutableState<AlbumSummary?>,
    selectedFolder: MutableState<com.photonne.app.data.models.FolderSummary?>,
    selectedPerson: MutableState<com.photonne.app.data.models.Person?>,
    memoryDetail: MutableState<com.photonne.app.ui.memories.MemoryDetailContext?>,
    pendingJumpDate: MutableState<kotlin.time.Instant?>,
    pendingAddTarget: MutableState<PendingAddTarget?>,
    pendingAddAlbum: MutableState<AlbumSummary?>,
    pendingAssetAddSubmitting: MutableState<Boolean>,
    pendingAssetAddError: MutableState<String?>,
    pendingActionAlbum: MutableState<AlbumSummary?>,
    pendingActionFolder: MutableState<com.photonne.app.data.models.FolderSummary?>,
    editingShareLink: MutableState<AlbumShareLink?>,
    revokingShareToken: MutableState<String?>,
    revokingAlbumMember: MutableState<com.photonne.app.data.models.AlbumPermission?>,
    revokingFolderMember: MutableState<com.photonne.app.data.models.AlbumPermission?>,
    addToAlbum: MutableState<AddToAlbumState?>,
    bulkAddToAlbum: MutableState<Boolean>,
    bulkAddSource: MutableState<BulkAddSource?>,
    moveSelectionRequest: MutableState<MoveSelectionRequest?>,
    moveSelectionSubmitting: MutableState<Boolean>,
    moveSelectionError: MutableState<String?>,
    inboxReviewTarget: MutableState<String?>,
    organizeRuleSummary: MutableState<com.photonne.app.data.models.MoveOutcome?>,
    mergeSource: MutableState<com.photonne.app.data.models.Person?>,
    isMerging: MutableState<Boolean>,
    mergeError: MutableState<String?>,
    assetFacesRevision: MutableState<Int>,
    showCreateAlbum: MutableState<Boolean>,
    showAlbumTypeChooser: MutableState<Boolean>,
    showEditAlbum: MutableState<Boolean>,
    showDeleteAlbum: MutableState<Boolean>,
    showLeaveAlbum: MutableState<Boolean>,
    showBulkDeleteAlbums: MutableState<Boolean>,
    showBulkLeaveAlbums: MutableState<Boolean>,
    showShares: MutableState<Boolean>,
    showCreateShare: MutableState<Boolean>,
    showMembers: MutableState<Boolean>,
    showInviteMember: MutableState<Boolean>,
    showRenamePerson: MutableState<Boolean>,
    showMergePicker: MutableState<Boolean>,
    showAcceptAllSuggestions: MutableState<Boolean>,
    showDismissAllSuggestions: MutableState<Boolean>,
    showAssetFacesSheet: MutableState<Boolean>,
    showJumpToDate: MutableState<Boolean>,
    showCreateFolder: MutableState<Boolean>,
    showEditFolder: MutableState<Boolean>,
    showDeleteFolder: MutableState<Boolean>,
    showEditSubfolder: MutableState<Boolean>,
    showDeleteSubfolder: MutableState<Boolean>,
    showFolderMembers: MutableState<Boolean>,
    showInviteFolderMember: MutableState<Boolean>,
    showMoveFolder: MutableState<Boolean>,
    showBulkDeleteFolders: MutableState<Boolean>,
    showBulkMoveFolders: MutableState<Boolean>,
    showMoveSelectedAssets: MutableState<Boolean>,
    showMoveSelectedAssetsTimeline: MutableState<Boolean>,
    showMoveSelectedAssetsInbox: MutableState<Boolean>,
    showSearchFilters: MutableState<Boolean>,
    showAlbumsFilters: MutableState<Boolean>,
    showFoldersFilters: MutableState<Boolean>,
    showUnarchiveAll: MutableState<Boolean>,
    showRestoreAllTrash: MutableState<Boolean>,
    showEmptyTrash: MutableState<Boolean>,
    showPurgeSelected: MutableState<Boolean>,
    showLogoutConfirm: MutableState<Boolean>,
    timelineState: State<com.photonne.app.ui.timeline.TimelineUiState>,
    albumsState: State<com.photonne.app.ui.album.AlbumsUiState>,
    albumDetailState: State<com.photonne.app.ui.album.AlbumDetailUiState>,
    searchState: State<com.photonne.app.ui.search.SearchUiState>,
    foldersState: State<com.photonne.app.ui.folder.FoldersUiState>,
    folderDetailState: State<com.photonne.app.ui.folder.FolderDetailUiState>,
    folderPermissionsState: State<com.photonne.app.ui.folder.FolderPermissionsUiState>,
    albumSharesState: State<com.photonne.app.ui.album.AlbumSharesUiState>,
    albumPermissionsState: State<com.photonne.app.ui.album.AlbumPermissionsUiState>,
    archivedState: State<com.photonne.app.ui.library.ArchivedUiState>,
    trashState: State<com.photonne.app.ui.library.TrashUiState>,
    favoritesState: State<com.photonne.app.ui.library.FavoritesUiState>,
    organizeInboxState: State<com.photonne.app.ui.organize.OrganizeInboxUiState>,
    organizeRuleState: State<com.photonne.app.ui.organize.OrganizeRuleUiState>,
    peopleState: State<com.photonne.app.ui.people.PeopleUiState>,
    personDetailState: State<com.photonne.app.ui.people.PersonDetailUiState>,
    suggestionsState: State<com.photonne.app.ui.people.PersonSuggestionsUiState>,
    assetFacesState: State<com.photonne.app.ui.people.AssetFacesUiState>,
    uploadState: State<com.photonne.app.ui.upload.UploadUiState>,
    actionsState: State<com.photonne.app.ui.actions.AssetActionsUiState>,
    deviceBackupState: State<com.photonne.app.ui.devicebackup.DeviceBackupUiState>,
    memorySelectionState: State<com.photonne.app.ui.memories.MemorySelectionUiState>,
    sharedLinkState: State<com.photonne.app.ui.share.SharedLinkUiState>,
    recentDestinations: State<List<String>>,
    val authRepository: AuthRepository,
    val albumsRepository: AlbumsRepository,
    val peopleRepository: com.photonne.app.data.people.PeopleRepository,
    val foldersRepository: com.photonne.app.data.folder.FoldersRepository,
    val errorFactory: com.photonne.app.data.error.UiErrorFactory,
    val appVersionStore: com.photonne.app.data.version.AppVersionStore,
    val recentDestinationsStore: com.photonne.app.data.settings.RecentDestinationsStore,
    val timelineViewModel: TimelineViewModel,
    val albumsViewModel: AlbumsViewModel,
    val albumDetailViewModel: AlbumDetailViewModel,
    val searchViewModel: com.photonne.app.ui.search.SearchViewModel,
    val foldersViewModel: FoldersViewModel,
    val folderDetailViewModel: com.photonne.app.ui.folder.FolderDetailViewModel,
    val folderPermissionsViewModel: FolderPermissionsViewModel,
    val albumSharesViewModel: AlbumSharesViewModel,
    val albumPermissionsViewModel: AlbumPermissionsViewModel,
    val archivedViewModel: com.photonne.app.ui.library.ArchivedViewModel,
    val trashViewModel: com.photonne.app.ui.library.TrashViewModel,
    val favoritesViewModel: com.photonne.app.ui.library.FavoritesViewModel,
    val organizeInboxViewModel: com.photonne.app.ui.organize.OrganizeInboxViewModel,
    val organizeRuleViewModel: com.photonne.app.ui.organize.OrganizeRuleViewModel,
    val uploadViewModel: com.photonne.app.ui.upload.UploadViewModel,
    val actionsViewModel: com.photonne.app.ui.actions.AssetSelectionActionsViewModel,
    val mapViewModel: com.photonne.app.ui.map.MapViewModel,
    val peopleViewModel: com.photonne.app.ui.people.PeopleViewModel,
    val personDetailViewModel: com.photonne.app.ui.people.PersonDetailViewModel,
    val personSuggestionsViewModel: com.photonne.app.ui.people.PersonSuggestionsViewModel,
    val assetFacesViewModel: com.photonne.app.ui.people.AssetFacesViewModel,
    val memorySelectionViewModel: com.photonne.app.ui.memories.MemorySelectionViewModel,
    val sharedLinkViewModel: com.photonne.app.ui.share.SharedLinkViewModel,
    val snackbarController: com.photonne.app.ui.main.SnackbarController,
    val coroutineScope: kotlinx.coroutines.CoroutineScope,
    val apiBaseUrl: String,
    val sharedLinkOpen: Boolean,
    val showAddedToAlbumSnackbar: (count: Int, albumName: String) -> Unit,
    val showMovedToFolderSnackbar: (count: Int, folderName: String?) -> Unit,
    val folderBack: () -> Unit,
    val openPersonFromViewer: (personId: String) -> Unit,
    val organizeRuleMoved: () -> Unit
) {
    var selectedTab by selectedTab
    var moreSubscreen by moreSubscreen
    var selectedAlbum by selectedAlbum
    var selectedFolder by selectedFolder
    var selectedPerson by selectedPerson
    var memoryDetail by memoryDetail
    var pendingJumpDate by pendingJumpDate
    var pendingAddTarget by pendingAddTarget
    var pendingAddAlbum by pendingAddAlbum
    var pendingAssetAddSubmitting by pendingAssetAddSubmitting
    var pendingAssetAddError by pendingAssetAddError
    var pendingActionAlbum by pendingActionAlbum
    var pendingActionFolder by pendingActionFolder
    var editingShareLink by editingShareLink
    var revokingShareToken by revokingShareToken
    var revokingAlbumMember by revokingAlbumMember
    var revokingFolderMember by revokingFolderMember
    var addToAlbum by addToAlbum
    var bulkAddToAlbum by bulkAddToAlbum
    var bulkAddSource by bulkAddSource
    var moveSelectionRequest by moveSelectionRequest
    var moveSelectionSubmitting by moveSelectionSubmitting
    var moveSelectionError by moveSelectionError
    var inboxReviewTarget by inboxReviewTarget
    var organizeRuleSummary by organizeRuleSummary
    var mergeSource by mergeSource
    var isMerging by isMerging
    var mergeError by mergeError
    var assetFacesRevision by assetFacesRevision
    var showCreateAlbum by showCreateAlbum
    var showAlbumTypeChooser by showAlbumTypeChooser
    var showEditAlbum by showEditAlbum
    var showDeleteAlbum by showDeleteAlbum
    var showLeaveAlbum by showLeaveAlbum
    var showBulkDeleteAlbums by showBulkDeleteAlbums
    var showBulkLeaveAlbums by showBulkLeaveAlbums
    var showShares by showShares
    var showCreateShare by showCreateShare
    var showMembers by showMembers
    var showInviteMember by showInviteMember
    var showRenamePerson by showRenamePerson
    var showMergePicker by showMergePicker
    var showAcceptAllSuggestions by showAcceptAllSuggestions
    var showDismissAllSuggestions by showDismissAllSuggestions
    var showAssetFacesSheet by showAssetFacesSheet
    var showJumpToDate by showJumpToDate
    var showCreateFolder by showCreateFolder
    var showEditFolder by showEditFolder
    var showDeleteFolder by showDeleteFolder
    var showEditSubfolder by showEditSubfolder
    var showDeleteSubfolder by showDeleteSubfolder
    var showFolderMembers by showFolderMembers
    var showInviteFolderMember by showInviteFolderMember
    var showMoveFolder by showMoveFolder
    var showBulkDeleteFolders by showBulkDeleteFolders
    var showBulkMoveFolders by showBulkMoveFolders
    var showMoveSelectedAssets by showMoveSelectedAssets
    var showMoveSelectedAssetsTimeline by showMoveSelectedAssetsTimeline
    var showMoveSelectedAssetsInbox by showMoveSelectedAssetsInbox
    var showSearchFilters by showSearchFilters
    var showAlbumsFilters by showAlbumsFilters
    var showFoldersFilters by showFoldersFilters
    var showUnarchiveAll by showUnarchiveAll
    var showRestoreAllTrash by showRestoreAllTrash
    var showEmptyTrash by showEmptyTrash
    var showPurgeSelected by showPurgeSelected
    var showLogoutConfirm by showLogoutConfirm
    val timelineState by timelineState
    val albumsState by albumsState
    val albumDetailState by albumDetailState
    val searchState by searchState
    val foldersState by foldersState
    val folderDetailState by folderDetailState
    val folderPermissionsState by folderPermissionsState
    val albumSharesState by albumSharesState
    val albumPermissionsState by albumPermissionsState
    val archivedState by archivedState
    val trashState by trashState
    val favoritesState by favoritesState
    val organizeInboxState by organizeInboxState
    val organizeRuleState by organizeRuleState
    val peopleState by peopleState
    val personDetailState by personDetailState
    val suggestionsState by suggestionsState
    val assetFacesState by assetFacesState
    val uploadState by uploadState
    val actionsState by actionsState
    val deviceBackupState by deviceBackupState
    val memorySelectionState by memorySelectionState
    val sharedLinkState by sharedLinkState
    val recentDestinations by recentDestinations
}

/** Todos los modales de [AuthenticatedApp], en el orden en que se componían en línea. */
@Composable
internal fun AuthenticatedDialogs(host: AuthenticatedDialogsHost) {
    AlbumDialogs(host)
    FolderDialogs(host)
    SelectionDialogs(host)
    LibraryPeopleDialogs(host)
    GlobalOverlays(host)
}

/** Fecha, álbumes (alta, edición, borrado, salida), enlaces y miembros de álbum y carpeta, y añadir la selección del timeline a un álbum. */
@Composable
private fun AlbumDialogs(host: AuthenticatedDialogsHost) {
    with(host) {
        if (showJumpToDate) {
            com.photonne.app.ui.timeline.JumpToDateDialog(
                onDismiss = { showJumpToDate = false },
                onConfirm = { date ->
                    showJumpToDate = false
                    pendingJumpDate = date
                }
            )
        }

        if (showAlbumTypeChooser) {
            com.photonne.app.ui.album.smart.AlbumTypeChooserSheet(
                onDismiss = { showAlbumTypeChooser = false },
                onManual = {
                    showAlbumTypeChooser = false
                    showCreateAlbum = true
                },
                onSmart = {
                    showAlbumTypeChooser = false
                    moreSubscreen = MoreSubscreen.SmartAlbumEditor
                }
            )
        }

        if (showCreateAlbum) {
            val addTarget = pendingAddTarget
            val mapBulkState = mapViewModel.state.collectAsStateWithLifecycle().value
            // Estado del alta pendiente según el origen: el error se enseña en la
            // propia hoja del álbum, no en el banner de la pantalla de origen.
            val (addSubmitting, addError) = when (addTarget) {
                null -> false to null
                is PendingAddTarget.Asset -> pendingAssetAddSubmitting to pendingAssetAddError
                is PendingAddTarget.Selection -> when (addTarget.source) {
                    null -> timelineState.isBulkMutating to timelineState.error?.userMessage
                    BulkAddSource.Search -> searchState.isBulkMutating to searchState.error?.userMessage
                    BulkAddSource.Map -> mapBulkState.isBulkMutating to mapBulkState.error?.userMessage
                    BulkAddSource.Favorites -> favoritesState.isBulkMutating to favoritesState.error?.userMessage
                    BulkAddSource.People -> personDetailState.isBulkMutating to personDetailState.error?.userMessage
                    BulkAddSource.Folder -> folderDetailState.isBulkMutating to folderDetailState.error?.userMessage
                    BulkAddSource.Archive -> archivedState.isBulkMutating to archivedState.error?.userMessage
                    BulkAddSource.Album -> albumDetailState.isBulkMutating to albumDetailState.error?.userMessage
                    BulkAddSource.Inbox -> organizeInboxState.isBulkMutating to organizeInboxState.error?.userMessage
                    BulkAddSource.Upload -> uploadState.isBulkMutating to uploadState.error?.userMessage
                    BulkAddSource.Memory -> memorySelectionState.isBulkMutating to memorySelectionState.error?.userMessage
                }
            }
            fun clearPendingAddError(target: PendingAddTarget?) {
                when (target) {
                    null -> Unit
                    is PendingAddTarget.Asset -> pendingAssetAddError = null
                    is PendingAddTarget.Selection -> when (target.source) {
                        null -> timelineViewModel.clearError()
                        BulkAddSource.Search -> searchViewModel.clearError()
                        BulkAddSource.Map -> mapViewModel.clearError()
                        BulkAddSource.Favorites -> favoritesViewModel.clearError()
                        BulkAddSource.People -> personDetailViewModel.clearError()
                        BulkAddSource.Folder -> folderDetailViewModel.clearError()
                        BulkAddSource.Archive -> archivedViewModel.clearError()
                        BulkAddSource.Album -> albumDetailViewModel.clearError()
                        BulkAddSource.Inbox -> organizeInboxViewModel.clearError()
                        BulkAddSource.Upload -> uploadViewModel.clearError()
                        BulkAddSource.Memory -> memorySelectionViewModel.clearError()
                    }
                }
            }
            fun finishCreateFlow(album: AlbumSummary, addedCount: Int?) {
                val target = pendingAddTarget
                showCreateAlbum = false
                pendingAddTarget = null
                pendingAddAlbum = null
                if (target == null || target == PendingAddTarget.Selection(null)) {
                    // Timeline (y creación directa): se abre el álbum recién creado.
                    selectedTab = MainTab.Albums
                    selectedAlbum = album
                } else if (addedCount != null) {
                    // Desde otras pantallas o el visor se queda donde estaba.
                    showAddedToAlbumSnackbar(addedCount, album.name)
                }
            }
            fun addPendingTo(album: AlbumSummary) {
                val onAdded: (List<TimelineItem>) -> Unit = { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    finishCreateFlow(album, added.size)
                }
                when (val target = pendingAddTarget) {
                    null -> finishCreateFlow(album, null)
                    is PendingAddTarget.Asset -> {
                        pendingAssetAddSubmitting = true
                        pendingAssetAddError = null
                        coroutineScope.launch {
                            runCatching { albumsRepository.addAsset(album.id, target.item.id) }
                                .onSuccess {
                                    pendingAssetAddSubmitting = false
                                    albumsViewModel.applyAssetAdded(album.id)
                                    finishCreateFlow(album, 1)
                                }
                                .onFailure { error ->
                                    pendingAssetAddSubmitting = false
                                    pendingAssetAddError = error.message ?: "No se pudo añadir al álbum"
                                }
                        }
                    }
                    is PendingAddTarget.Selection -> when (target.source) {
                        null -> timelineViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Search -> searchViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Map -> mapViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Favorites -> favoritesViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.People -> personDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Folder -> folderDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Archive -> archivedViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Album -> albumDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Inbox -> organizeInboxViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Upload -> uploadViewModel.addBatchToAlbum(album.id, onAdded)
                        BulkAddSource.Memory -> memorySelectionViewModel.bulkAddToAlbum(
                            album.id, memoryDetail?.items.orEmpty(), onAdded
                        )
                    }
                }
            }
            LaunchedEffect(Unit) { clearPendingAddError(pendingAddTarget) }
            val createdAlbum = pendingAddAlbum
            AlbumFormDialog(
                title = stringResource(Res.string.album_action_new),
                // Álbum ya creado y alta fallida: el botón solo reintenta el alta.
                confirmLabel = stringResource(
                    if (createdAlbum != null) Res.string.action_retry else Res.string.action_create
                ),
                initialName = createdAlbum?.name.orEmpty(),
                initialDescription = createdAlbum?.description,
                isSubmitting = albumsState.isMutating || addSubmitting,
                errorMessage = albumsState.error?.userMessage ?: addError,
                onDismiss = {
                    clearPendingAddError(pendingAddTarget)
                    showCreateAlbum = false
                    pendingAddTarget = null
                    pendingAddAlbum = null
                    albumsViewModel.clearError()
                },
                onConfirm = { name, description ->
                    if (createdAlbum != null) {
                        addPendingTo(createdAlbum)
                    } else {
                        albumsViewModel.create(name, description) { newAlbum ->
                            if (pendingAddTarget != null) pendingAddAlbum = newAlbum
                            addPendingTo(newAlbum)
                        }
                    }
                }
            )
        }

        val openedAlbum = selectedAlbum
        if (showEditAlbum && openedAlbum != null) {
            AlbumFormDialog(
                title = stringResource(Res.string.album_action_edit),
                confirmLabel = stringResource(Res.string.action_save),
                initialName = albumDetailState.albumName ?: openedAlbum.name,
                initialDescription = albumDetailState.albumDescription ?: openedAlbum.description,
                isSubmitting = albumDetailState.isMutating,
                errorMessage = albumDetailState.error?.userMessage,
                onDismiss = {
                    showEditAlbum = false
                    albumDetailViewModel.clearError()
                },
                onConfirm = { name, description ->
                    albumDetailViewModel.rename(name, description) { updated ->
                        showEditAlbum = false
                        selectedAlbum = openedAlbum.copy(name = updated.name, description = updated.description)
                        albumsViewModel.applyUpdate(updated)
                    }
                }
            )
        } else if (showEditAlbum && pendingActionAlbum != null) {
            val target = pendingActionAlbum!!
            AlbumFormDialog(
                title = stringResource(Res.string.album_action_edit),
                confirmLabel = stringResource(Res.string.action_save),
                initialName = target.name,
                initialDescription = target.description,
                isSubmitting = albumsState.isMutating,
                errorMessage = albumsState.error?.userMessage,
                onDismiss = {
                    showEditAlbum = false
                    pendingActionAlbum = null
                    albumsViewModel.clearError()
                },
                onConfirm = { name, description ->
                    albumsViewModel.renameAlbum(target.id, name, description) {
                        showEditAlbum = false
                        pendingActionAlbum = null
                    }
                }
            )
        }

        if (showDeleteAlbum && openedAlbum != null) {
            DeleteAlbumDialog(
                albumName = albumDetailState.albumName ?: openedAlbum.name,
                isSubmitting = albumDetailState.isMutating,
                errorMessage = albumDetailState.error?.userMessage,
                onDismiss = {
                    showDeleteAlbum = false
                    albumDetailViewModel.clearError()
                },
                onConfirm = {
                    albumDetailViewModel.delete { albumId ->
                        showDeleteAlbum = false
                        albumsViewModel.applyDelete(albumId)
                        selectedAlbum = null
                    }
                }
            )
        } else if (showDeleteAlbum && pendingActionAlbum != null) {
            val target = pendingActionAlbum!!
            DeleteAlbumDialog(
                albumName = target.name,
                isSubmitting = albumsState.isMutating,
                errorMessage = albumsState.error?.userMessage,
                onDismiss = {
                    showDeleteAlbum = false
                    pendingActionAlbum = null
                    albumsViewModel.clearError()
                },
                onConfirm = {
                    albumsViewModel.deleteAlbum(target.id) {
                        showDeleteAlbum = false
                        pendingActionAlbum = null
                    }
                }
            )
        }

        if (showLeaveAlbum && openedAlbum != null) {
            LeaveAlbumDialog(
                albumName = albumDetailState.albumName ?: openedAlbum.name,
                isSubmitting = albumDetailState.isMutating,
                errorMessage = albumDetailState.error?.userMessage,
                onDismiss = {
                    showLeaveAlbum = false
                    albumDetailViewModel.clearError()
                },
                onConfirm = {
                    albumDetailViewModel.leave { albumId ->
                        showLeaveAlbum = false
                        albumsViewModel.applyDelete(albumId)
                        selectedAlbum = null
                    }
                }
            )
        } else if (showLeaveAlbum && pendingActionAlbum != null) {
            val target = pendingActionAlbum!!
            LeaveAlbumDialog(
                albumName = target.name,
                isSubmitting = albumsState.isMutating,
                errorMessage = albumsState.error?.userMessage,
                onDismiss = {
                    showLeaveAlbum = false
                    pendingActionAlbum = null
                    albumsViewModel.clearError()
                },
                onConfirm = {
                    albumsViewModel.leaveAlbum(target.id) {
                        showLeaveAlbum = false
                        pendingActionAlbum = null
                    }
                }
            )
        }

        // Borrar / salir de varios álbumes seleccionados en la lista.
        if (showBulkDeleteAlbums || showBulkLeaveAlbums) {
            com.photonne.app.ui.album.AlbumsBulkConfirmDialog(
                leaving = showBulkLeaveAlbums,
                state = albumsState,
                viewModel = albumsViewModel,
                snackbar = snackbarController,
                scope = coroutineScope,
                onClose = {
                    showBulkDeleteAlbums = false
                    showBulkLeaveAlbums = false
                }
            )
        }

        if (showShares && openedAlbum != null) {
            ManageSharesDialog(
                state = albumSharesState,
                onDismiss = {
                    showShares = false
                    albumSharesViewModel.clearError()
                },
                onCreate = { showCreateShare = true },
                onEdit = { link -> editingShareLink = link },
                // Revocar mata el enlace para todo el mundo: confirma, como en
                // "Mis enlaces".
                onRevoke = { token -> revokingShareToken = token }
            )
        }

        revokingShareToken?.let { token ->
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.share_revoke_confirm_title),
                message = stringResource(Res.string.share_revoke_confirm_message),
                confirmLabel = stringResource(Res.string.share_action_revoke),
                isDestructive = true,
                isSubmitting = albumSharesState.isMutating,
                errorMessage = albumSharesState.error?.userMessage,
                onDismiss = { revokingShareToken = null },
                onConfirm = {
                    albumSharesViewModel.revoke(token) { revokingShareToken = null }
                }
            )
        }

        revokingAlbumMember?.let { member ->
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.member_remove_confirm_title),
                message = stringResource(
                    Res.string.member_remove_confirm_message, member.username
                ),
                confirmLabel = stringResource(Res.string.member_remove_confirm_action),
                isDestructive = true,
                isSubmitting = albumPermissionsState.isMutating,
                errorMessage = albumPermissionsState.error?.userMessage,
                onDismiss = { revokingAlbumMember = null },
                onConfirm = {
                    albumPermissionsViewModel.revoke(member) { newCount ->
                        revokingAlbumMember = null
                        selectedAlbum?.let { album ->
                            val updated = album.copy(
                                isShared = newCount > 0,
                                sharedWithCount = newCount
                            )
                            selectedAlbum = updated
                            albumsViewModel.applyUpdate(updated)
                        }
                        pendingActionAlbum?.let { album ->
                            val updated = album.copy(
                                isShared = newCount > 0,
                                sharedWithCount = newCount
                            )
                            pendingActionAlbum = updated
                            albumsViewModel.applyUpdate(updated)
                        }
                    }
                }
            )
        }

        revokingFolderMember?.let { member ->
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.member_remove_confirm_title),
                message = stringResource(
                    Res.string.member_remove_confirm_message, member.username
                ),
                confirmLabel = stringResource(Res.string.member_remove_confirm_action),
                isDestructive = true,
                isSubmitting = folderPermissionsState.isMutating,
                errorMessage = folderPermissionsState.error?.userMessage,
                onDismiss = { revokingFolderMember = null },
                onConfirm = {
                    folderPermissionsViewModel.revoke(member) { newCount ->
                        revokingFolderMember = null
                        selectedFolder?.let { folder ->
                            val updated = folder.copy(
                                isShared = newCount > 0,
                                sharedWithCount = newCount
                            )
                            selectedFolder = updated
                            foldersViewModel.applyUpdate(updated)
                        }
                        pendingActionFolder?.let { folder ->
                            val updated = folder.copy(
                                isShared = newCount > 0,
                                sharedWithCount = newCount
                            )
                            pendingActionFolder = updated
                            foldersViewModel.applyUpdate(updated)
                        }
                    }
                }
            )
        }

        if (showMembers && (openedAlbum != null || pendingActionAlbum != null)) {
            ManagePermissionsDialog(
                state = albumPermissionsState,
                onDismiss = {
                    showMembers = false
                    if (openedAlbum == null) pendingActionAlbum = null
                    albumPermissionsViewModel.clearError()
                },
                onInvite = { showInviteMember = true },
                onChangeRole = { member, role -> albumPermissionsViewModel.changeRole(member, role) },
                onRevoke = { member -> revokingAlbumMember = member }
            )
        }

        if (showInviteMember && (openedAlbum != null || pendingActionAlbum != null)) {
            InviteMemberDialog(
                candidates = albumPermissionsState.invitableUsers,
                isSubmitting = albumPermissionsState.isMutating,
                errorMessage = albumPermissionsState.error?.userMessage,
                onDismiss = {
                    showInviteMember = false
                    albumPermissionsViewModel.clearError()
                },
                onInvite = { selectedUser, role ->
                    // Abierto hasta el resultado: si la invitación falla, el error
                    // se ve aquí mismo; antes el diálogo ya se había cerrado.
                    albumPermissionsViewModel.grant(
                        user = selectedUser,
                        role = role,
                        onMembershipChanged = { newCount ->
                            selectedAlbum?.let { album ->
                                val updated = album.copy(
                                    isShared = true,
                                    sharedWithCount = newCount
                                )
                                selectedAlbum = updated
                                albumsViewModel.applyUpdate(updated)
                            }
                            pendingActionAlbum?.let { album ->
                                val updated = album.copy(
                                    isShared = true,
                                    sharedWithCount = newCount
                                )
                                pendingActionAlbum = updated
                                albumsViewModel.applyUpdate(updated)
                            }
                        },
                        onSuccess = { showInviteMember = false }
                    )
                }
            )
        }

        if (showCreateShare && openedAlbum != null) {
            CreateShareDialog(
                isSubmitting = albumSharesState.isMutating,
                errorMessage = albumSharesState.error?.userMessage,
                onDismiss = {
                    showCreateShare = false
                    albumSharesViewModel.clearError()
                },
                onConfirm = { expiresAt, password, allowDownload, maxViews, allowUpload ->
                    albumSharesViewModel.createLink(
                        expiresAt = expiresAt,
                        password = password,
                        allowDownload = allowDownload,
                        maxViews = maxViews,
                        allowUpload = allowUpload
                    ) {
                        showCreateShare = false
                    }
                }
            )
        }

        editingShareLink?.let { link ->
            EditShareDialog(
                link = link,
                isSubmitting = albumSharesState.isMutating,
                errorMessage = albumSharesState.error?.userMessage,
                onDismiss = {
                    editingShareLink = null
                    albumSharesViewModel.clearError()
                },
                onConfirm = { expiresAt, password, allowDownload, maxViews, allowUpload ->
                    albumSharesViewModel.editLink(
                        token = link.token,
                        expiresAt = expiresAt,
                        password = password,
                        allowDownload = allowDownload,
                        maxViews = maxViews,
                        allowUpload = allowUpload
                    ) {
                        editingShareLink = null
                    }
                }
            )
        }

        if (bulkAddToAlbum) {
            // Un error viejo de otra acción no debe estrenar el diálogo.
            LaunchedEffect(Unit) { timelineViewModel.clearError() }
            AddToAlbumDialog(
                albums = albumsState.albums,
                isLoadingAlbums = albumsState.isLoading,
                isSubmitting = timelineState.isBulkMutating,
                errorMessage = timelineState.error?.userMessage,
                onCreateNew = {
                    bulkAddToAlbum = false
                    pendingAddTarget = PendingAddTarget.Selection(null)
                    showCreateAlbum = true
                },
                onAlbumSelected = { album ->
                    // El diálogo se queda abierto hasta el resultado: si falla,
                    // enseña el error y permite reintentar; antes se cerraba al
                    // instante y el fallo no se veía en ninguna parte.
                    timelineViewModel.bulkAddToAlbum(album.id) { added ->
                        albumsViewModel.applyAssetsAdded(album.id, added.size)
                        albumDetailViewModel.applyAssetsAdded(album.id, added)
                        bulkAddToAlbum = false
                        showAddedToAlbumSnackbar(added.size, album.name)
                    }
                },
                onDismiss = { bulkAddToAlbum = false }
            )
        }
    }
}

/** Carpetas (alta, edición, borrado, subcarpetas, miembros, mover) y hojas de filtros. */
@Composable
private fun FolderDialogs(host: AuthenticatedDialogsHost) {
    with(host) {
        if (showCreateFolder) {
            // When a folder is open, create inside it (a subfolder); shared-space
            // is a root-level concept only, so the option is hidden there.
            val createParent = selectedFolder
            FolderFormDialog(
                title = stringResource(Res.string.folder_action_new),
                confirmLabel = stringResource(Res.string.action_create),
                isSubmitting = foldersState.isMutating,
                errorMessage = foldersState.error?.userMessage,
                showSharedSpaceOption = createParent == null,
                onDismiss = {
                    showCreateFolder = false
                    foldersViewModel.clearError()
                },
                onConfirm = { name, isSharedSpace ->
                    foldersViewModel.create(
                        name = name,
                        parentFolderId = createParent?.id,
                        isSharedSpace = isSharedSpace
                    ) {
                        showCreateFolder = false
                        if (createParent != null) folderDetailViewModel.refresh()
                    }
                }
            )
        }

        val openedFolder = selectedFolder
        if (showEditFolder && openedFolder != null) {
            FolderFormDialog(
                title = stringResource(Res.string.folder_action_edit),
                confirmLabel = stringResource(Res.string.action_save),
                initialName = folderDetailState.folderName ?: openedFolder.name,
                isSubmitting = folderDetailState.isMutating,
                errorMessage = folderDetailState.error?.userMessage,
                onDismiss = {
                    showEditFolder = false
                    folderDetailViewModel.clearError()
                },
                onConfirm = { name, _ ->
                    folderDetailViewModel.rename(name) { updated ->
                        showEditFolder = false
                        selectedFolder = openedFolder.copy(name = updated.name, path = updated.path)
                        foldersViewModel.applyUpdate(updated)
                    }
                }
            )
        } else if (showEditFolder && pendingActionFolder != null) {
            val target = pendingActionFolder!!
            FolderFormDialog(
                title = stringResource(Res.string.folder_action_edit),
                confirmLabel = stringResource(Res.string.action_save),
                initialName = target.name,
                isSubmitting = foldersState.isMutating,
                errorMessage = foldersState.error?.userMessage,
                onDismiss = {
                    showEditFolder = false
                    pendingActionFolder = null
                    foldersViewModel.clearError()
                },
                onConfirm = { name, _ ->
                    foldersViewModel.renameFolder(target.id, name) {
                        showEditFolder = false
                        pendingActionFolder = null
                    }
                }
            )
        }

        if (showDeleteFolder && openedFolder != null) {
            DeleteFolderDialog(
                folderName = folderDetailState.folderName ?: openedFolder.name.ifBlank { openedFolder.path },
                isSubmitting = folderDetailState.isMutating,
                itemCount = openedFolder.assetCount,
                errorMessage = folderDetailState.error?.userMessage,
                onDismiss = {
                    showDeleteFolder = false
                    folderDetailViewModel.clearError()
                },
                onConfirm = {
                    folderDetailViewModel.delete { folderId ->
                        showDeleteFolder = false
                        foldersViewModel.applyDelete(folderId)
                        folderBack()
                    }
                }
            )
        } else if (showDeleteFolder && pendingActionFolder != null) {
            val target = pendingActionFolder!!
            DeleteFolderDialog(
                folderName = target.name.ifBlank { target.path },
                isSubmitting = foldersState.isMutating,
                itemCount = target.assetCount,
                errorMessage = foldersState.error?.userMessage,
                onDismiss = {
                    showDeleteFolder = false
                    pendingActionFolder = null
                    foldersViewModel.clearError()
                },
                onConfirm = {
                    foldersViewModel.deleteFolder(target.id) {
                        showDeleteFolder = false
                        pendingActionFolder = null
                    }
                }
            )
        }

        // Varias carpetas seleccionadas en la lista a la papelera.
        if (showBulkDeleteFolders) {
            com.photonne.app.ui.folder.FoldersBulkDeleteDialog(
                state = foldersState,
                viewModel = foldersViewModel,
                snackbar = snackbarController,
                scope = coroutineScope,
                onClose = { showBulkDeleteFolders = false }
            )
        }

        // Rename/delete for a selected subfolder inside the open folder. These mirror
        // the top-level folder selection actions but target a child of the open
        // folder via FolderDetailViewModel, which patches its subfolder list in place.
        val selectedSubfolder = folderDetailState.selectedSubfolder
        if (showEditSubfolder && selectedSubfolder != null) {
            FolderFormDialog(
                title = stringResource(Res.string.folder_action_edit),
                confirmLabel = stringResource(Res.string.action_save),
                initialName = selectedSubfolder.name.ifBlank { selectedSubfolder.path },
                isSubmitting = folderDetailState.isMutating,
                errorMessage = folderDetailState.error?.userMessage,
                onDismiss = {
                    showEditSubfolder = false
                    folderDetailViewModel.clearError()
                },
                onConfirm = { name, _ ->
                    folderDetailViewModel.renameSubfolder(selectedSubfolder.id, name) {
                        showEditSubfolder = false
                        foldersViewModel.refresh()
                    }
                }
            )
        }

        if (showDeleteSubfolder && selectedSubfolder != null) {
            DeleteFolderDialog(
                folderName = selectedSubfolder.name.ifBlank { selectedSubfolder.path },
                isSubmitting = folderDetailState.isMutating,
                itemCount = selectedSubfolder.assetCount,
                errorMessage = folderDetailState.error?.userMessage,
                onDismiss = {
                    showDeleteSubfolder = false
                    folderDetailViewModel.clearError()
                },
                onConfirm = {
                    folderDetailViewModel.deleteSubfolder(selectedSubfolder.id) {
                        showDeleteSubfolder = false
                        foldersViewModel.refresh()
                    }
                }
            )
        }

        if (showFolderMembers && (openedFolder != null || pendingActionFolder != null)) {
            ManageFolderPermissionsDialog(
                state = folderPermissionsState,
                onDismiss = {
                    showFolderMembers = false
                    if (openedFolder == null) pendingActionFolder = null
                    folderPermissionsViewModel.clearError()
                },
                onInvite = { showInviteFolderMember = true },
                onChangeRole = { member, role -> folderPermissionsViewModel.changeRole(member, role) },
                onRevoke = { member -> revokingFolderMember = member }
            )
        }

        if (showInviteFolderMember && (openedFolder != null || pendingActionFolder != null)) {
            InviteFolderMemberDialog(
                candidates = folderPermissionsState.invitableUsers,
                isSubmitting = folderPermissionsState.isMutating,
                errorMessage = folderPermissionsState.error?.userMessage,
                onDismiss = {
                    showInviteFolderMember = false
                    folderPermissionsViewModel.clearError()
                },
                onInvite = { selectedUser, role ->
                    // Abierto hasta el resultado, como el invite de álbum.
                    folderPermissionsViewModel.grant(
                        user = selectedUser,
                        role = role,
                        onMembershipChanged = { newCount ->
                            selectedFolder?.let { folder ->
                                val updated = folder.copy(
                                    isShared = true,
                                    sharedWithCount = newCount
                                )
                                selectedFolder = updated
                                foldersViewModel.applyUpdate(updated)
                            }
                            pendingActionFolder?.let { folder ->
                                val updated = folder.copy(
                                    isShared = true,
                                    sharedWithCount = newCount
                                )
                                pendingActionFolder = updated
                                foldersViewModel.applyUpdate(updated)
                            }
                        },
                        onSuccess = { showInviteFolderMember = false }
                    )
                }
            )
        }

        if (showMoveFolder && openedFolder != null) {
            com.photonne.app.ui.folder.FolderPickerDialog(
                title = stringResource(Res.string.folder_move_title),
                // Mismos destinos que los otros selectores (toda la profundidad,
                // personales y compartidas con escritura). Antes solo ofrecía las
                // personales de primer nivel. La propia carpeta y su subárbol los
                // poda excludeFolderId.
                folders = foldersState.moveDestinations,
                isSubmitting = folderDetailState.isMutating,
                errorMessage = folderDetailState.error?.userMessage,
                excludeFolderId = openedFolder.id,
                includeRoot = true,
                // Padre actual preseleccionado; si no es un destino listado (la raíz
                // personal), queda marcada la raíz.
                initialSelectionId = openedFolder.parentFolderId?.takeIf { parentId ->
                    foldersState.moveDestinations.any { it.id == parentId }
                },
                onDismiss = {
                    showMoveFolder = false
                    folderDetailViewModel.clearError()
                },
                onConfirm = { targetParentId, _ ->
                    folderDetailViewModel.move(targetParentId) { updated ->
                        showMoveFolder = false
                        selectedFolder = openedFolder.copy(
                            path = updated.path,
                            parentFolderId = updated.parentFolderId
                        )
                        foldersViewModel.applyUpdate(updated)
                    }
                }
            )
        }

        // Mover las carpetas seleccionadas en la lista (una o varias).
        if (showBulkMoveFolders) {
            com.photonne.app.ui.folder.FoldersBulkMoveDialog(
                state = foldersState,
                viewModel = foldersViewModel,
                snackbar = snackbarController,
                scope = coroutineScope,
                onClose = { showBulkMoveFolders = false }
            )
        }

        if (showAlbumsFilters) {
            com.photonne.app.ui.album.AlbumsFiltersSheet(
                state = albumsState,
                onDismiss = { showAlbumsFilters = false },
                onScopeChange = albumsViewModel::setScope,
                onSortChange = albumsViewModel::setSort,
                onDirectionChange = albumsViewModel::setDirection,
                onViewModeChange = albumsViewModel::setViewMode,
                onGroupByYearChange = albumsViewModel::setGroupByYear
            )
        }

        if (showFoldersFilters) {
            com.photonne.app.ui.folder.FoldersFiltersSheet(
                state = foldersState,
                onDismiss = { showFoldersFilters = false },
                onScopeChange = foldersViewModel::setScope,
                onSortChange = foldersViewModel::setSort,
                onDirectionChange = foldersViewModel::setDirection,
                onViewModeChange = foldersViewModel::setViewMode
            )
        }

        if (showSearchFilters) {
            com.photonne.app.ui.search.SearchFiltersSheet(
                state = searchState,
                onDismiss = { showSearchFilters = false },
                onDateRangeChange = searchViewModel::setDateRange,
                onOcrChange = searchViewModel::setOcrText,
                onToggleObject = searchViewModel::toggleObjectLabel,
                onToggleScene = searchViewModel::toggleSceneLabel,
                onTogglePerson = searchViewModel::togglePerson,
                onPeopleQueryChange = searchViewModel::setPeopleQuery,
                onClearAll = searchViewModel::clearAll
            )
        }
    }
}

/** Añadir o mover una selección (cualquier pantalla), revisión del movimiento de la bandeja y por condiciones, y añadir el asset del visor a un álbum. */
@Composable
private fun SelectionDialogs(host: AuthenticatedDialogsHost) {
    with(host) {
        val openedFolder = selectedFolder
        // One add-to-album dialog for every screen's selection bar; [bulkAddSource]
        // says whose selection it adds.
        bulkAddSource?.let { source ->
            val (isSubmitting, errorMessage) = when (source) {
                BulkAddSource.Search -> searchState.isBulkMutating to searchState.error?.userMessage
                BulkAddSource.Map -> mapViewModel.state.collectAsStateWithLifecycle().value
                    .let { it.isBulkMutating to it.error?.userMessage }
                BulkAddSource.Favorites -> favoritesState.isBulkMutating to favoritesState.error?.userMessage
                BulkAddSource.People -> personDetailState.isBulkMutating to personDetailState.error?.userMessage
                BulkAddSource.Folder -> folderDetailState.isBulkMutating to folderDetailState.error?.userMessage
                BulkAddSource.Archive -> archivedState.isBulkMutating to archivedState.error?.userMessage
                BulkAddSource.Album -> albumDetailState.isBulkMutating to albumDetailState.error?.userMessage
                BulkAddSource.Inbox -> organizeInboxState.isBulkMutating to organizeInboxState.error?.userMessage
                BulkAddSource.Upload -> uploadState.isBulkMutating to uploadState.error?.userMessage
                BulkAddSource.Memory -> memorySelectionState.isBulkMutating to memorySelectionState.error?.userMessage
            }
            LaunchedEffect(source) {
                when (source) {
                    BulkAddSource.Search -> searchViewModel.clearError()
                    BulkAddSource.Map -> mapViewModel.clearError()
                    BulkAddSource.Favorites -> favoritesViewModel.clearError()
                    BulkAddSource.People -> personDetailViewModel.clearError()
                    BulkAddSource.Folder -> folderDetailViewModel.clearError()
                    BulkAddSource.Archive -> archivedViewModel.clearError()
                    BulkAddSource.Album -> albumDetailViewModel.clearError()
                    BulkAddSource.Inbox -> organizeInboxViewModel.clearError()
                    BulkAddSource.Upload -> uploadViewModel.clearError()
                    BulkAddSource.Memory -> memorySelectionViewModel.clearError()
                }
            }
            AddToAlbumDialog(
                albums = albumsState.albums,
                isLoadingAlbums = albumsState.isLoading,
                isSubmitting = isSubmitting,
                errorMessage = errorMessage,
                onCreateNew = {
                    bulkAddSource = null
                    pendingAddTarget = PendingAddTarget.Selection(source)
                    showCreateAlbum = true
                },
                onAlbumSelected = { album ->
                    val onAdded: (List<com.photonne.app.data.models.TimelineItem>) -> Unit = { added ->
                        albumsViewModel.applyAssetsAdded(album.id, added.size)
                        // Adding from inside an album never targets that same album's
                        // open list, so only other sources patch the detail view.
                        if (source != BulkAddSource.Album) {
                            albumDetailViewModel.applyAssetsAdded(album.id, added)
                        }
                        bulkAddSource = null
                        showAddedToAlbumSnackbar(added.size, album.name)
                    }
                    when (source) {
                        BulkAddSource.Search -> searchViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Map -> mapViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Favorites -> favoritesViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.People -> personDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Folder -> folderDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Archive -> archivedViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Album -> albumDetailViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Inbox -> organizeInboxViewModel.bulkAddToAlbum(album.id, onAdded)
                        BulkAddSource.Upload -> uploadViewModel.addBatchToAlbum(album.id, onAdded)
                        BulkAddSource.Memory -> memorySelectionViewModel.bulkAddToAlbum(
                            album.id, memoryDetail?.items.orEmpty(), onAdded
                        )
                    }
                },
                onDismiss = { bulkAddSource = null }
            )
        }

        if (showMoveSelectedAssets && openedFolder != null) {
            com.photonne.app.ui.folder.FolderPickerDialog(
                title = stringResource(Res.string.folder_move_assets_title),
                folders = foldersState.moveDestinations,
                isSubmitting = folderDetailState.isBulkMutating,
                errorMessage = folderDetailState.error?.userMessage,
                excludeFolderId = openedFolder.id,
                includeRoot = false,
                recentDestinationIds = recentDestinations,
                onDismiss = {
                    showMoveSelectedAssets = false
                    folderDetailViewModel.clearError()
                },
                onConfirm = { targetFolderId, _ ->
                    if (targetFolderId != null) {
                        recentDestinationsStore.record(targetFolderId)
                        val targetName = foldersState.moveDestinations
                            .firstOrNull { it.id == targetFolderId }?.name
                        folderDetailViewModel.moveSelectedAssets(targetFolderId) { movedIds ->
                            showMoveSelectedAssets = false
                            val moved = movedIds.size
                            if (moved > 0) {
                                selectedFolder = openedFolder.copy(
                                    assetCount = (openedFolder.assetCount - moved).coerceAtLeast(0)
                                )
                                foldersViewModel.refreshOrganizeCount()
                                showMovedToFolderSnackbar(moved, targetName)
                            }
                        }
                    }
                }
            )
        }

        // "Mover a carpeta" de una selección: el mismo diálogo para el timeline
        // (que refresca su store en su ViewModel) y para Álbum, Búsqueda,
        // Favoritos y Archivados, que solo necesitan los ids (Lote N6).
        @Composable
        fun MoveAssetsToFolderDialog(
            isSubmitting: Boolean,
            errorMessage: String?,
            onDismiss: () -> Unit,
            onMove: (targetFolderId: String, targetName: String?) -> Unit
        ) {
            com.photonne.app.ui.folder.FolderPickerDialog(
                title = stringResource(Res.string.folder_move_assets_title),
                folders = foldersState.moveDestinations,
                isSubmitting = isSubmitting,
                errorMessage = errorMessage,
                includeRoot = false,
                recentDestinationIds = recentDestinations,
                onDismiss = onDismiss,
                onConfirm = { targetFolderId, _ ->
                    if (targetFolderId != null) {
                        recentDestinationsStore.record(targetFolderId)
                        onMove(
                            targetFolderId,
                            foldersState.moveDestinations.firstOrNull { it.id == targetFolderId }?.name
                        )
                    }
                }
            )
        }

        if (showMoveSelectedAssetsTimeline) {
            MoveAssetsToFolderDialog(
                isSubmitting = timelineState.isBulkMutating,
                errorMessage = timelineState.error?.userMessage,
                onDismiss = {
                    showMoveSelectedAssetsTimeline = false
                    timelineViewModel.clearError()
                },
                onMove = { targetFolderId, targetName ->
                    timelineViewModel.moveSelectedAssets(targetFolderId) { movedIds ->
                        showMoveSelectedAssetsTimeline = false
                        foldersViewModel.refreshOrganizeCount()
                        showMovedToFolderSnackbar(movedIds.size, targetName)
                    }
                }
            )
        }

        moveSelectionRequest?.let { request ->
            MoveAssetsToFolderDialog(
                isSubmitting = moveSelectionSubmitting,
                errorMessage = moveSelectionError,
                onDismiss = {
                    if (!moveSelectionSubmitting) {
                        moveSelectionRequest = null
                        moveSelectionError = null
                    }
                },
                onMove = { targetFolderId, targetName ->
                    if (!moveSelectionSubmitting) {
                        moveSelectionSubmitting = true
                        moveSelectionError = null
                        coroutineScope.launch {
                            runCatching {
                                foldersRepository.moveAssets(
                                    sourceFolderId = null,
                                    targetFolderId = targetFolderId,
                                    assetIds = request.assetIds
                                )
                            }.onSuccess {
                                moveSelectionSubmitting = false
                                moveSelectionRequest = null
                                request.onMoved()
                                foldersViewModel.refreshOrganizeCount()
                                showMovedToFolderSnackbar(request.assetIds.size, targetName)
                            }.onFailure { error ->
                                moveSelectionSubmitting = false
                                moveSelectionError =
                                    errorFactory.from(error, "No se pudo mover").userMessage
                            }
                        }
                    }
                }
            )
        }

        if (showMoveSelectedAssetsInbox) {
            com.photonne.app.ui.folder.FolderPickerDialog(
                title = stringResource(Res.string.folder_move_assets_title),
                folders = foldersState.moveDestinations,
                isSubmitting = organizeInboxState.isBulkMutating,
                errorMessage = organizeInboxState.error?.userMessage,
                includeRoot = false,
                recentDestinationIds = recentDestinations,
                showOrganizeByDate = true,
                yearBreakdown = organizeInboxState.moveYearGroups.map {
                    com.photonne.app.data.models.YearCount(it.year, it.count)
                },
                onDismiss = {
                    showMoveSelectedAssetsInbox = false
                    organizeInboxViewModel.clearError()
                },
                onConfirm = { targetFolderId, organizeByYear ->
                    if (targetFolderId != null) {
                        recentDestinationsStore.record(targetFolderId)
                        showMoveSelectedAssetsInbox = false
                        // With year foldering, review the split before committing;
                        // otherwise (or if the preview failed to load) move straight away.
                        if (organizeByYear && organizeInboxState.moveYearGroups.isNotEmpty()) {
                            inboxReviewTarget = targetFolderId
                        } else {
                            organizeInboxViewModel.moveSelectedAssets(targetFolderId, organizeByYear) {
                                foldersViewModel.refreshOrganizeCount()
                            }
                        }
                    }
                }
            )
        }

        inboxReviewTarget?.let { target ->
            com.photonne.app.ui.organize.MoveReviewScreen(
                movedTotal = organizeInboxState.moveYearGroups.sumOf { it.count },
                groups = organizeInboxState.moveYearGroups,
                baseUrl = apiBaseUrl,
                isMoving = organizeInboxState.isBulkMutating,
                organizeByYear = true,
                onBack = { inboxReviewTarget = null },
                onConfirm = { keptIds ->
                    organizeInboxViewModel.moveSelectedAssets(
                        targetFolderId = target,
                        organizeByYear = true,
                        onlyIds = keptIds
                    ) {
                        inboxReviewTarget = null
                        foldersViewModel.refreshOrganizeCount()
                    }
                }
            )
        }

        // Misma rejilla de revisión para el flujo por condiciones, hospedada también
        // aquí FUERA del MainScaffold: dentro del contenido quedaba por debajo de la
        // nav flotante y su botón de confirmar era inalcanzable.
        if (moreSubscreen == MoreSubscreen.OrganizeRule) {
            organizeRuleState.reviewGroups?.let { groups ->
                com.photonne.app.ui.organize.MoveReviewScreen(
                    // El total sale de los grupos que se están revisando, NO del
                    // previewCount: si divergen, `keptTotal` (total − quitadas) se
                    // desmadra y puede deshabilitar el botón con fotos en pantalla.
                    movedTotal = groups.sumOf { it.count },
                    groups = groups,
                    baseUrl = apiBaseUrl,
                    isMoving = organizeRuleState.isMoving,
                    organizeByYear = organizeRuleState.organizeByYear,
                    onBack = organizeRuleViewModel::closeReview,
                    onConfirm = { keptIds ->
                        organizeRuleViewModel.move(onlyIds = keptIds) { outcome ->
                            // With a year split, confirm the distribution first; the
                            // navigation back happens when the summary is dismissed.
                            if (outcome.yearBreakdown.isNotEmpty()) organizeRuleSummary = outcome
                            else organizeRuleMoved()
                        }
                    }
                )
            }
        }

        organizeRuleSummary?.let { outcome ->
            com.photonne.app.ui.organize.MoveSummaryDialog(
                outcome = outcome,
                onDismiss = {
                    organizeRuleSummary = null
                    organizeRuleMoved()
                }
            )
        }

        organizeInboxState.lastMoveSummary?.let { outcome ->
            com.photonne.app.ui.organize.MoveSummaryDialog(
                outcome = outcome,
                onDismiss = organizeInboxViewModel::clearMoveSummary
            )
        }

        val addToAlbumState = addToAlbum
        if (addToAlbumState != null) {
            AddToAlbumDialog(
                albums = albumsState.albums,
                isLoadingAlbums = albumsState.isLoading,
                isSubmitting = addToAlbumState.isSubmitting,
                errorMessage = addToAlbumState.errorMessage,
                onCreateNew = {
                    addToAlbum = null
                    pendingAddTarget = PendingAddTarget.Asset(addToAlbumState.asset)
                    pendingAssetAddError = null
                    showCreateAlbum = true
                },
                onAlbumSelected = { album ->
                    addToAlbum = addToAlbumState.copy(isSubmitting = true, errorMessage = null)
                    coroutineScope.launch {
                        runCatching { albumsRepository.addAsset(album.id, addToAlbumState.asset.id) }
                            .onSuccess {
                                albumsViewModel.applyAssetAdded(album.id)
                                albumDetailViewModel.applyAssetAdded(album.id, addToAlbumState.asset)
                                addToAlbum = null
                                showAddedToAlbumSnackbar(1, album.name)
                            }
                            .onFailure { error ->
                                addToAlbum = addToAlbumState.copy(
                                    isSubmitting = false,
                                    errorMessage = error.message ?: "Failed to add to album"
                                )
                            }
                    }
                },
                onDismiss = { addToAlbum = null }
            )
        }
    }
}

/** Cierre de sesión, sugerencias de personas, archivados, papelera, personas, compartir/descargar y caras del asset. */
@Composable
private fun LibraryPeopleDialogs(host: AuthenticatedDialogsHost) {
    with(host) {
        if (showLogoutConfirm) {
            val pendingBackup = if (deviceBackupState.isBackupEnabled) {
                deviceBackupState.pendingEntries.size
            } else 0
            val baseMessage = stringResource(Res.string.logout_confirm_message)
            val message = if (pendingBackup > 0) {
                baseMessage + "\n\n" + pluralStringResource(
                    Res.plurals.logout_confirm_pending_backup, pendingBackup, pendingBackup
                )
            } else baseMessage
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.logout_confirm_title),
                message = message,
                confirmLabel = stringResource(Res.string.action_logout),
                isDestructive = true,
                isSubmitting = false,
                onDismiss = { showLogoutConfirm = false },
                onConfirm = {
                    showLogoutConfirm = false
                    authRepository.logout()
                }
            )
        }

        if (showAcceptAllSuggestions) {
            LaunchedEffect(Unit) { personSuggestionsViewModel.clearError() }
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.people_suggestions_accept_all_title),
                message = pluralStringResource(
                    Res.plurals.people_suggestions_accept_all_message,
                    suggestionsState.total,
                    suggestionsState.total
                ),
                confirmLabel = stringResource(Res.string.people_action_suggestions_accept_all),
                isDestructive = false,
                isSubmitting = suggestionsState.isBulkMutating,
                errorMessage = suggestionsState.error?.userMessage,
                onDismiss = { showAcceptAllSuggestions = false },
                onConfirm = {
                    personSuggestionsViewModel.acceptAll { affected ->
                        showAcceptAllSuggestions = false
                        selectedPerson?.let { personDetailViewModel.open(it.id, it.name) }
                        peopleViewModel.refresh()
                        coroutineScope.launch {
                            snackbarController.show(
                                org.jetbrains.compose.resources.getPluralString(
                                    Res.plurals.people_suggestions_accepted_done,
                                    affected, affected
                                )
                            )
                        }
                    }
                }
            )
        }

        if (showDismissAllSuggestions) {
            LaunchedEffect(Unit) { personSuggestionsViewModel.clearError() }
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.people_suggestions_dismiss_all_title),
                message = pluralStringResource(
                    Res.plurals.people_suggestions_dismiss_all_message,
                    suggestionsState.total,
                    suggestionsState.total
                ),
                confirmLabel = stringResource(Res.string.people_action_suggestions_dismiss_all),
                isDestructive = true,
                isSubmitting = suggestionsState.isBulkMutating,
                errorMessage = suggestionsState.error?.userMessage,
                onDismiss = { showDismissAllSuggestions = false },
                onConfirm = {
                    personSuggestionsViewModel.dismissAll { affected ->
                        showDismissAllSuggestions = false
                        coroutineScope.launch {
                            snackbarController.show(
                                org.jetbrains.compose.resources.getPluralString(
                                    Res.plurals.people_suggestions_dismissed_done,
                                    affected, affected
                                )
                            )
                        }
                    }
                }
            )
        }

        if (showUnarchiveAll) {
            LaunchedEffect(Unit) { archivedViewModel.clearError() }
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.archive_action_unarchive_all_title),
                message = stringResource(Res.string.archive_action_unarchive_all_message),
                confirmLabel = stringResource(Res.string.archive_action_unarchive_all),
                isDestructive = false,
                isSubmitting = archivedState.isBulkMutating,
                errorMessage = archivedState.error?.userMessage,
                onDismiss = { showUnarchiveAll = false },
                onConfirm = {
                    archivedViewModel.unarchiveAll {
                        showUnarchiveAll = false
                        timelineViewModel.refresh()
                    }
                }
            )
        }

        if (showRestoreAllTrash) {
            LaunchedEffect(Unit) { trashViewModel.clearError() }
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.trash_action_restore_all),
                message = stringResource(Res.string.trash_dialog_restore_all_message),
                confirmLabel = stringResource(Res.string.trash_action_restore_all),
                isDestructive = false,
                isSubmitting = trashState.isBulkMutating,
                errorMessage = trashState.error?.userMessage,
                onDismiss = { showRestoreAllTrash = false },
                onConfirm = {
                    trashViewModel.restoreAll {
                        showRestoreAllTrash = false
                        timelineViewModel.refresh()
                    }
                }
            )
        }

        if (showEmptyTrash) {
            LaunchedEffect(Unit) { trashViewModel.clearError() }
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.trash_action_empty),
                message = stringResource(Res.string.trash_dialog_empty_message),
                confirmLabel = stringResource(Res.string.trash_action_empty),
                isDestructive = true,
                isSubmitting = trashState.isBulkMutating,
                errorMessage = trashState.error?.userMessage,
                onDismiss = { showEmptyTrash = false },
                onConfirm = {
                    trashViewModel.emptyTrash { showEmptyTrash = false }
                }
            )
        }

        if (showPurgeSelected) {
            val count = trashState.selection.size
            LaunchedEffect(Unit) { trashViewModel.clearError() }
            com.photonne.app.ui.library.ConfirmActionDialog(
                title = stringResource(Res.string.trash_action_delete_forever),
                message = pluralStringResource(Res.plurals.trash_dialog_purge_message, count, count),
                confirmLabel = stringResource(Res.string.trash_action_delete_forever),
                isDestructive = true,
                isSubmitting = trashState.isBulkMutating,
                errorMessage = trashState.error?.userMessage,
                onDismiss = { showPurgeSelected = false },
                onConfirm = {
                    trashViewModel.bulkPurge { showPurgeSelected = false }
                }
            )
        }

        val activePerson = selectedPerson
        if (showRenamePerson && activePerson != null) {
            com.photonne.app.ui.people.RenamePersonDialog(
                initialName = personDetailState.personName ?: activePerson.name,
                isSubmitting = peopleState.isMutating,
                errorMessage = peopleState.error?.userMessage,
                onDismiss = {
                    showRenamePerson = false
                    peopleViewModel.clearError()
                },
                onConfirm = { name ->
                    peopleViewModel.rename(activePerson.id, name) {
                        personDetailViewModel.applyRename(name)
                        selectedPerson = activePerson.copy(name = name)
                        showRenamePerson = false
                    }
                }
            )
        }

        if (showMergePicker && activePerson != null) {
            // El selector debe poder encontrar a CUALQUIERA: se cargan todas las
            // páginas al abrir (con el paginado perezoso las no cargadas eran
            // infusionables).
            LaunchedEffect(Unit) { peopleViewModel.loadAllPages() }
            com.photonne.app.ui.people.PersonPickerDialog(
                people = peopleState.people,
                baseUrl = apiBaseUrl,
                excludeId = activePerson.id,
                isLoading = peopleState.isAppending,
                onDismiss = { showMergePicker = false },
                onSelect = { other ->
                    showMergePicker = false
                    mergeSource = other
                    mergeError = null
                }
            )
        }

        val mergeSourcePerson = mergeSource
        if (mergeSourcePerson != null && activePerson != null) {
            com.photonne.app.ui.people.ConfirmMergeDialog(
                target = activePerson,
                source = mergeSourcePerson,
                baseUrl = apiBaseUrl,
                isSubmitting = isMerging,
                errorMessage = mergeError,
                onDismiss = {
                    mergeSource = null
                    mergeError = null
                },
                onConfirm = {
                    isMerging = true
                    mergeError = null
                    coroutineScope.launch {
                        // The current person absorbs the picked one's faces.
                        // Mirror the PWA: target is the receiving person,
                        // `other` is the one that gets dropped.
                        runCatching {
                            peopleRepository.merge(
                                targetPersonId = activePerson.id,
                                sourcePersonId = mergeSourcePerson.id
                            )
                        }.onSuccess {
                            isMerging = false
                            mergeSource = null
                            peopleViewModel.refresh()
                            // The current detail might now contain more faces.
                            personDetailViewModel.open(activePerson.id, activePerson.name)
                            snackbarController.show(
                                org.jetbrains.compose.resources.getString(
                                    Res.string.people_merge_done
                                )
                            )
                        }.onFailure { error ->
                            // El diálogo sigue abierto con el error: antes el fallo
                            // era silencio absoluto (runCatching sin onFailure).
                            isMerging = false
                            mergeError = errorFactory
                                .from(error, "No se pudo fusionar")
                                .userMessage
                        }
                    }
                }
            )
        }

        val shareIds = actionsState.shareChooserIds
        if (shareIds != null) {
            ShareAssetsDialog(
                selectedCount = shareIds.size,
                onDismiss = actionsViewModel::cancelShare,
                onShareDirectly = { actionsViewModel.shareDirectly(shareIds) },
                onCreateLink = { name -> actionsViewModel.createPhotonneLink(shareIds, name) }
            )
        }

        val formatChooser = actionsState.formatChooser
        if (formatChooser != null) {
            DownloadFormatSheet(
                chooser = formatChooser,
                onDismiss = actionsViewModel::cancelFormatChoice,
                onChoose = actionsViewModel::chooseFormat
            )
        }

        val createdLink = actionsState.createdLink
        if (createdLink != null) {
            ShareLinkResultDialog(
                url = createdLink,
                onDismiss = actionsViewModel::dismissLink
            )
        }

        if (showAssetFacesSheet && assetFacesState.assetId != null) {
            com.photonne.app.ui.people.AssetFacesSheet(
                state = assetFacesState,
                baseUrl = apiBaseUrl,
                onDismiss = {
                    showAssetFacesSheet = false
                    assetFacesViewModel.close()
                    assetFacesRevision++
                },
                onAcceptSuggestion = assetFacesViewModel::acceptSuggestion,
                onDismissSuggestion = assetFacesViewModel::dismissSuggestion,
                onAssign = assetFacesViewModel::startAssigning,
                onAssignToPerson = assetFacesViewModel::assignToPerson,
                onAssignToNewPerson = { faceId, name ->
                    assetFacesViewModel.assignToNewPerson(faceId, name)
                    peopleViewModel.refresh()
                },
                onUnassign = assetFacesViewModel::unassign,
                onReject = assetFacesViewModel::reject,
                onSetCover = { personId, faceId ->
                    assetFacesViewModel.setAsCover(personId, faceId) {
                        peopleViewModel.refresh()
                    }
                },
                onCancelAssign = assetFacesViewModel::cancelAssigning,
                onPickerQueryChange = assetFacesViewModel::setPickerQuery,
                onOpenPerson = { personId ->
                    showAssetFacesSheet = false
                    assetFacesViewModel.close()
                    assetFacesRevision++
                    openPersonFromViewer(personId)
                }
            )
        }
    }
}

/** Enlace compartido abierto desde fuera, host del snackbar con las píldoras de conectividad y compatibilidad, y píldora de operación masiva. */
@Composable
private fun GlobalOverlays(host: AuthenticatedDialogsHost) {
    with(host) {
        // Enlace compartido abierto desde fuera: sobre todo lo demás (el visor se
        // cierra al abrirlo), bajo el snackbar.
        if (sharedLinkOpen) {
            com.photonne.app.ui.share.SharedLinkScreen(
                state = sharedLinkState,
                onBack = sharedLinkViewModel::close,
                onSubmitPassword = sharedLinkViewModel::submitPassword,
                onRetry = sharedLinkViewModel::retry
            )
        }

        // Host del snackbar sobre todo lo demás. Entra por arriba, bajo el cromo
        // flotante (barra de estado + cápsula): abajo caía sobre la nav flotante y
        // sobre el botón que lo acababa de provocar.
        // Encima, la píldora "Sin conexión": el snackbar entra justo debajo de ella.
        val connectivityMonitor: com.photonne.app.data.api.ConnectivityMonitor = koinInject()
        val connectivity by connectivityMonitor.status.collectAsStateWithLifecycle()
        // Y la de incompatibilidad app↔servidor, que explica fallos sueltos igual.
        val compatibility by appVersionStore.compatibility.collectAsStateWithLifecycle()
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.padding(top = com.photonne.app.ui.main.subscreenChromeReservedTop()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                com.photonne.app.ui.main.ConnectivityPill(
                    status = connectivity,
                    onRetry = connectivityMonitor::retry
                )
                com.photonne.app.ui.main.CompatibilityPill(compatibility = compatibility)
                com.photonne.app.ui.main.TopSnackbarHost(hostState = snackbarController.hostState)
            }
        }

        // Píldora de operación masiva (descarga/ZIP/compartir/enlace) con Cancelar,
        // por encima de la nav flotante. Antes el único indicio era la barra de
        // selección atenuada, sin forma de abortar.
        if (actionsState.working != AssetActionWorking.Idle) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                com.photonne.app.ui.actions.WorkingPill(
                    working = actionsState.working,
                    onCancel = actionsViewModel::cancelWorking,
                    modifier = Modifier.padding(
                        bottom = com.photonne.app.ui.main.floatingNavBarReservedHeight() + 16.dp
                    )
                )
            }
        }
    }
}
