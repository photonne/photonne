@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.photonne.app

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photonne.app.resources.notifications_no_screen
import com.photonne.app.resources.people_recluster_done
import com.photonne.app.resources.people_recluster_done_none
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_system_enrichment_failures
import com.photonne.app.resources.account_section_appearance
import com.photonne.app.resources.account_section_profile
import com.photonne.app.resources.account_section_security
import com.photonne.app.resources.account_section_storage
import com.photonne.app.resources.account_section_connection
import com.photonne.app.resources.account_settings_title
import com.photonne.app.resources.admin_section_libraries
import com.photonne.app.resources.admin_shared_trash
import com.photonne.app.resources.admin_section_settings
import com.photonne.app.resources.admin_section_stats
import com.photonne.app.resources.admin_section_system
import com.photonne.app.resources.admin_section_users
import com.photonne.app.resources.admin_libraries_action_new
import com.photonne.app.resources.admin_libraries_edit_title
import com.photonne.app.resources.admin_user_action_new
import com.photonne.app.resources.admin_user_edit_title
import com.photonne.app.resources.admin_settings_face_recognition
import com.photonne.app.resources.admin_settings_image
import com.photonne.app.resources.admin_settings_image_embedding
import com.photonne.app.resources.admin_settings_metadata
import com.photonne.app.resources.admin_settings_nightly
import com.photonne.app.resources.admin_settings_notifications
import com.photonne.app.resources.admin_settings_object_detection
import com.photonne.app.resources.admin_settings_scene_classification
import com.photonne.app.resources.admin_settings_server
import com.photonne.app.resources.admin_settings_text_recognition
import com.photonne.app.resources.admin_settings_trash
import com.photonne.app.resources.admin_settings_user_defaults
import com.photonne.app.resources.admin_settings_version
import com.photonne.app.resources.admin_system_backup
import com.photonne.app.resources.admin_system_duplicates
import com.photonne.app.resources.admin_system_run_tasks
import com.photonne.app.resources.administration_title
import com.photonne.app.resources.organize_rule_title
import com.photonne.app.resources.notifications_title
import com.photonne.app.resources.backup_pending_screen_title
import com.photonne.app.resources.enrichment_screen_title
import com.photonne.app.resources.device_backup_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.utilities_section_duplicates
import com.photonne.app.resources.utilities_section_large_files
import com.photonne.app.resources.utilities_section_locations
import com.photonne.app.resources.utilities_title
import com.photonne.app.resources.my_links_title
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.data.auth.AuthState
import com.photonne.app.ui.album.AlbumDetailScreen
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumPermissionsViewModel
import com.photonne.app.ui.album.AlbumSharesViewModel
import com.photonne.app.ui.album.AlbumsListScreen
import com.photonne.app.ui.album.AlbumsViewModel
import com.photonne.app.ui.folder.FolderPermissionsViewModel
import com.photonne.app.ui.folder.FoldersViewModel
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import dev.chrisbanes.haze.HazeState
import com.photonne.app.ui.main.FolderDetailChromeActions
import com.photonne.app.ui.library.TrashChromeActions
import com.photonne.app.ui.main.toMoreBackupStatus
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.main.MoreScreen
import com.photonne.app.ui.timeline.TimelineScreen
import com.photonne.app.ui.timeline.TimelineViewModel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue

/**
 * Lo que necesita el contenido principal de [AuthenticatedApp] (el pager de las
 * cuatro pestañas y la capa de overlay con los detalles, Buscar y las
 * subpantallas de Más), sacado a este fichero para que el cuerpo de
 * AuthenticatedApp no rebase el límite de 64 KB por método de la JVM.
 *
 * Mismo patrón que [AuthenticatedDialogsHost]: [AuthenticatedAppState] compartido,
 * [State] del padre como propiedades delegadas (sin colectores duplicados) y
 * los valores derivados que el padre ya calcula (inmersivos, pager) por valor.
 * Se reconstruye en cada composición del padre.
 */
internal class AuthenticatedContentHost(
    val appState: AuthenticatedAppState,
    val user: AuthState.Authenticated,
    timelineState: State<com.photonne.app.ui.timeline.TimelineUiState>,
    albumsState: State<com.photonne.app.ui.album.AlbumsUiState>,
    albumDetailState: State<com.photonne.app.ui.album.AlbumDetailUiState>,
    searchState: State<com.photonne.app.ui.search.SearchUiState>,
    foldersState: State<com.photonne.app.ui.folder.FoldersUiState>,
    folderDetailState: State<com.photonne.app.ui.folder.FolderDetailUiState>,
    archivedState: State<com.photonne.app.ui.library.ArchivedUiState>,
    trashState: State<com.photonne.app.ui.library.TrashUiState>,
    favoritesState: State<com.photonne.app.ui.library.FavoritesUiState>,
    organizeInboxState: State<com.photonne.app.ui.organize.OrganizeInboxUiState>,
    organizeExcludedState: State<com.photonne.app.ui.organize.OrganizeExcludedUiState>,
    organizeRuleState: State<com.photonne.app.ui.organize.OrganizeRuleUiState>,
    peopleState: State<com.photonne.app.ui.people.PeopleUiState>,
    personDetailState: State<com.photonne.app.ui.people.PersonDetailUiState>,
    suggestionsState: State<com.photonne.app.ui.people.PersonSuggestionsUiState>,
    deviceBackupState: State<com.photonne.app.ui.devicebackup.DeviceBackupUiState>,
    uploadState: State<com.photonne.app.ui.upload.UploadUiState>,
    unsupportedFilesState: State<com.photonne.app.ui.library.UnsupportedFilesUiState>,
    memoriesState: State<com.photonne.app.ui.timeline.MemoriesUiState>,
    notificationsState: State<com.photonne.app.ui.notifications.NotificationsUiState>,
    attributions: State<List<com.photonne.app.data.models.Attribution>>,
    activityNotificationsEnabled: State<Boolean>,
    val timelineViewModel: com.photonne.app.ui.timeline.TimelineViewModel,
    val albumsViewModel: com.photonne.app.ui.album.AlbumsViewModel,
    val albumDetailViewModel: com.photonne.app.ui.album.AlbumDetailViewModel,
    val searchViewModel: com.photonne.app.ui.search.SearchViewModel,
    val foldersViewModel: com.photonne.app.ui.folder.FoldersViewModel,
    val folderDetailViewModel: com.photonne.app.ui.folder.FolderDetailViewModel,
    val folderPermissionsViewModel: com.photonne.app.ui.folder.FolderPermissionsViewModel,
    val albumSharesViewModel: com.photonne.app.ui.album.AlbumSharesViewModel,
    val albumPermissionsViewModel: com.photonne.app.ui.album.AlbumPermissionsViewModel,
    val archivedViewModel: com.photonne.app.ui.library.ArchivedViewModel,
    val trashViewModel: com.photonne.app.ui.library.TrashViewModel,
    val favoritesViewModel: com.photonne.app.ui.library.FavoritesViewModel,
    val unsupportedFilesViewModel: com.photonne.app.ui.library.UnsupportedFilesViewModel,
    val organizeInboxViewModel: com.photonne.app.ui.organize.OrganizeInboxViewModel,
    val organizeExcludedViewModel: com.photonne.app.ui.organize.OrganizeExcludedViewModel,
    val organizeRuleViewModel: com.photonne.app.ui.organize.OrganizeRuleViewModel,
    val uploadViewModel: com.photonne.app.ui.upload.UploadViewModel,
    val deviceBackupViewModel: com.photonne.app.ui.devicebackup.DeviceBackupViewModel,
    val enrichmentStatusViewModel: com.photonne.app.ui.devicebackup.EnrichmentStatusViewModel,
    val utilitiesDuplicatesViewModel: com.photonne.app.ui.utilities.UtilitiesDuplicatesViewModel,
    val utilitiesLargeFilesViewModel: com.photonne.app.ui.utilities.UtilitiesLargeFilesViewModel,
    val utilitiesLocationsViewModel: com.photonne.app.ui.utilities.UtilitiesLocationsViewModel,
    val exploreFacetsViewModel: com.photonne.app.ui.explore.ExploreFacetsViewModel,
    val memoriesViewModel: com.photonne.app.ui.timeline.MemoriesViewModel,
    val memoryFeedViewModel: com.photonne.app.ui.memories.MemoryFeedViewModel,
    val notificationsViewModel: com.photonne.app.ui.notifications.NotificationsViewModel,
    val actionsViewModel: com.photonne.app.ui.actions.AssetSelectionActionsViewModel,
    val mapViewModel: com.photonne.app.ui.map.MapViewModel,
    val peopleViewModel: com.photonne.app.ui.people.PeopleViewModel,
    val personDetailViewModel: com.photonne.app.ui.people.PersonDetailViewModel,
    val personSuggestionsViewModel: com.photonne.app.ui.people.PersonSuggestionsViewModel,
    val accountProfileViewModel: com.photonne.app.ui.settings.AccountProfileViewModel,
    val accountSecurityViewModel: com.photonne.app.ui.settings.AccountSecurityViewModel,
    val accountStorageViewModel: com.photonne.app.ui.settings.AccountStorageViewModel,
    val appearanceViewModel: com.photonne.app.ui.settings.AppearanceViewModel,
    val adminUsersViewModel: com.photonne.app.ui.admin.AdminUsersViewModel,
    val adminLibrariesViewModel: com.photonne.app.ui.admin.AdminLibrariesViewModel,
    val adminStatsViewModel: com.photonne.app.ui.admin.AdminStatsViewModel,
    val adminVersionViewModel: com.photonne.app.ui.admin.AdminServerViewModel,
    val adminImageSettingsViewModel: com.photonne.app.ui.admin.AdminImageSettingsViewModel,
    val adminMetadataSettingsViewModel: com.photonne.app.ui.admin.AdminMetadataSettingsViewModel,
    val adminNightlySettingsViewModel: com.photonne.app.ui.admin.AdminNightlySettingsViewModel,
    val adminNotificationSettingsViewModel: com.photonne.app.ui.admin.AdminNotificationSettingsViewModel,
    val adminServerSettingsViewModel: com.photonne.app.ui.admin.AdminServerSettingsViewModel,
    val deviceConnectionViewModel: com.photonne.app.ui.admin.DeviceConnectionViewModel,
    val adminTrashSettingsViewModel: com.photonne.app.ui.admin.AdminTrashSettingsViewModel,
    val adminSharedTrashViewModel: com.photonne.app.ui.admin.AdminSharedTrashViewModel,
    val adminUserDefaultsViewModel: com.photonne.app.ui.admin.AdminUserDefaultsViewModel,
    val adminDuplicatesViewModel: com.photonne.app.ui.admin.AdminDuplicatesViewModel,
    val adminBackupViewModel: com.photonne.app.ui.admin.AdminBackupViewModel,
    val activityNotifications: com.photonne.app.data.notifications.ActivityNotifications,
    val deviceGallery: com.photonne.app.data.devicebackup.DeviceGallery,
    val foldersRepository: com.photonne.app.data.folder.FoldersRepository,
    val snackbarController: com.photonne.app.ui.main.SnackbarController,
    val coroutineScope: kotlinx.coroutines.CoroutineScope,
    val apiBaseUrl: String,
    val navTabs: List<MainTab>,
    val mainPagerState: androidx.compose.foundation.pager.PagerState,
    val canSwipeTabs: Boolean,
    val albumsImmersive: Boolean,
    val foldersImmersive: Boolean,
    val albumDetailImmersive: Boolean,
    val folderDetailImmersive: Boolean,
    val onLogout: () -> Unit,
    val albumBack: () -> Unit,
) {
    val timelineState by timelineState
    val albumsState by albumsState
    val albumDetailState by albumDetailState
    val searchState by searchState
    val foldersState by foldersState
    val folderDetailState by folderDetailState
    val archivedState by archivedState
    val trashState by trashState
    val favoritesState by favoritesState
    val organizeInboxState by organizeInboxState
    val organizeExcludedState by organizeExcludedState
    val organizeRuleState by organizeRuleState
    val peopleState by peopleState
    val personDetailState by personDetailState
    val suggestionsState by suggestionsState
    val deviceBackupState by deviceBackupState
    val uploadState by uploadState
    val unsupportedFilesState by unsupportedFilesState
    val memoriesState by memoriesState
    val notificationsState by notificationsState
    val attributions by attributions
    val activityNotificationsEnabled by activityNotificationsEnabled
}

/** Capa base: las cuatro pestañas principales en un HorizontalPager. */
@Composable
internal fun AuthenticatedTabsPager(host: AuthenticatedContentHost) {
    with(host) {
        // Base layer: the four primary tabs live in a HorizontalPager so a
        // left/right swipe glides between Fotos · Álbumes · Carpetas · Más
        // (continuous drag; the neighbour page peeks in under the finger).
        HorizontalPager(
            state = mainPagerState,
            userScrollEnabled = canSwipeTabs,
            // Keep the immediate-neighbour pages composed so a swipe (or a
            // return to a tab) doesn't dispose + rebuild the heavy Timeline
            // grid — that rebuild is what reset the chrome and re-fetched
            // buckets, reading as a jump + flash.
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (navTabs.getOrNull(page)) {
                MainTab.Timeline -> TimelineScreen(
                    state = timelineState,
                    scrollToTopTick = appState.timelineScrollToTopTick,
                    // El pager principal compone esta página también como
                    // vecina: la tira de Recuerdos solo anima cuando Fotos
                    // es de verdad la pestaña visible.
                    memoriesAutoPlay = appState.selectedTab == MainTab.Timeline &&
                        appState.assetDetail == null,
                    onOpenAsset = { mergedItems, mergedIndex, feed ->
                        appState.assetDetail = AssetDetailContext(
                            items = mergedItems,
                            // The pager starts on the contiguous loaded
                            // bucket run TimelineScreen handed us; the feed
                            // appends older months as the viewer reaches
                            // the end of it.
                            startIndex = mergedIndex,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = timelineViewModel::setFavorite,
                            feed = feed
                        )
                    },
                    onBucketsVisible = timelineViewModel::ensureVisible,
                    onEnsureYearSummaries = timelineViewModel::ensureYearSummaries,
                    // El pull-to-refresh de Fotos también trae la tira
                    // de Recuerdos (lote L9).
                    onRefresh = {
                        timelineViewModel.refresh()
                        memoriesViewModel.refresh()
                    },
                    onDismissError = timelineViewModel::clearError,
                    onToggleSelection = timelineViewModel::toggleSelection,
                    onSetSelected = timelineViewModel::setSelected,
                    onApplySelection = timelineViewModel::applySelection,
                    onOpenUpload = {
                        appState.selectedTab = MainTab.More
                        appState.moreSubscreen = MoreSubscreen.Upload
                    },
                    backupPendingCount = if (deviceBackupState.isBackupEnabled) {
                        deviceBackupState.pendingEntries.size
                    } else 0,
                    onOpenBackup = {
                        appState.selectedTab = MainTab.More
                        appState.moreSubscreen = MoreSubscreen.DeviceBackup
                    },
                    onJumpToDate = { appState.showJumpToDate = true },
                    onOpenSearch = { appState.selectedTab = MainTab.Search },
                    onChromeVisibleChange = { appState.timelineChromeVisible = it },
                    pendingJumpDate = appState.pendingJumpDate,
                    onJumpHandled = { appState.pendingJumpDate = null },
                    memories = memoriesState.items,
                    onOpenMemory = { memory -> appState.memoryDetail = memory },
                    onSeeAllMemories = { appState.moreSubscreen = MoreSubscreen.Memories }
                )
                MainTab.Albums -> Column(modifier = Modifier.fillMaxSize()) {
                    // La búsqueda va DENTRO de la cápsula flotante que dibuja
                    // AlbumsListScreen (campo en titleContent), como el buscador;
                    // ya no hay barra acoplada aquí.
                    Box(modifier = Modifier.weight(1f)) {
                        AlbumsListScreen(
                            onAlbumClick = { album ->
                                if (albumsState.isSelectionActive) {
                                    // Con la selección abierta, tocar suma o
                                    // quita; al quitar la última se cierra.
                                    albumsViewModel.toggleAlbumSelection(album.id)
                                } else {
                                    appState.selectedAlbum = album
                                }
                            },
                            onAlbumLongPress = { album ->
                                albumsViewModel.selectAlbum(album.id)
                            },
                            onCreateAlbum = { appState.showAlbumTypeChooser = true },
                            // Explorar cards open their screen as a modal layer
                            // over the Albums tab (no tab switch) so back
                            // returns here and the bottom nav stays on Álbumes.
                            onOpenPeople = {
                                appState.selectedPerson = null
                                appState.moreSubscreen = MoreSubscreen.People
                            },
                            onOpenMap = { appState.moreSubscreen = MoreSubscreen.Map },
                            onOpenScenes = { appState.moreSubscreen = MoreSubscreen.ExploreScenes },
                            onOpenObjects = { appState.moreSubscreen = MoreSubscreen.ExploreObjects },
                            onOpenFilters = { appState.showAlbumsFilters = true },
                            immersive = albumsImmersive,
                            onChromeVisibleChange = { appState.albumsChromeVisible = it },
                            scrollToTopTick = appState.albumsScrollToTopTick
                        )
                    }
                }
                MainTab.Folders -> Column(modifier = Modifier.fillMaxSize()) {
                    // La búsqueda va DENTRO de la cápsula flotante que dibuja
                    // FoldersListScreen (campo en titleContent), como el buscador;
                    // ya no hay barra acoplada aquí.
                    val foldersCreate = if (
                        foldersState.scope !=
                            com.photonne.app.ui.folder.FoldersScope.External
                    ) {
                        { appState.showCreateFolder = true }
                    } else null
                    Box(modifier = Modifier.weight(1f)) {
                        com.photonne.app.ui.folder.FoldersListScreen(
                            onFolderClick = { folder ->
                                if (foldersState.isSelectionActive) {
                                    foldersViewModel.toggleFolderSelection(folder.id)
                                } else {
                                    appState.selectedFolder = folder
                                }
                            },
                            onFolderLongPress = { folder ->
                                foldersViewModel.selectFolder(folder.id)
                            },
                            onOpenOrganize = { appState.moreSubscreen = MoreSubscreen.OrganizeInbox },
                            // Como People/Map desde Álbumes: capa modal sobre la
                            // pestaña, sin cambiar de tab. La tarjeta solo se
                            // muestra donde hay buckets, así que el callback
                            // puede ser incondicional.
                            onOpenDeviceFolders = {
                                appState.moreSubscreen = MoreSubscreen.DeviceFolders
                            },
                            onOpenFilters = { appState.showFoldersFilters = true },
                            onCreateFolder = foldersCreate,
                            immersive = foldersImmersive,
                            onChromeVisibleChange = { appState.foldersChromeVisible = it },
                            scrollToTopTick = appState.foldersScrollToTopTick
                        )
                    }
                }
                // Más pinta su propio cromo flotante dentro de la pantalla
                // (título + acción Subir), como Fotos: nada de barra acoplada.
                else -> MoreScreen(
                    user = user.user,
                    onLogout = onLogout,
                    onOpenFavorites = { appState.moreSubscreen = MoreSubscreen.Favorites },
                    onOpenArchived = { appState.moreSubscreen = MoreSubscreen.Archived },
                    onOpenTrash = {
                        appState.trashTab = com.photonne.app.ui.library.TrashTab.Personal
                        appState.moreSubscreen = MoreSubscreen.Trash
                    },
                    onOpenUtilities = { appState.moreSubscreen = MoreSubscreen.Utilities },
                    onOpenMyLinks = { appState.moreSubscreen = MoreSubscreen.MyLinks },
                    onOpenProfile = {
                        appState.profileOpenedFromMore = true
                        appState.moreSubscreen = MoreSubscreen.AccountProfile
                    },
                    onOpenDeviceBackup = { appState.moreSubscreen = MoreSubscreen.DeviceBackup },
                    backupStatus = remember(deviceBackupState) {
                        deviceBackupState.toMoreBackupStatus()
                    },
                    onOpenNotifications = {
                        appState.moreSubscreen = MoreSubscreen.Notifications
                    },
                    notificationsUnreadCount = notificationsState.unreadCount,
                    onOpenAccountSettings = {
                        appState.moreSubscreen = MoreSubscreen.AccountSettings
                    },
                    onOpenAdministration = if (
                        user.user.role.equals("Admin", ignoreCase = true)
                    ) {
                        { appState.moreSubscreen = MoreSubscreen.Administration }
                    } else {
                        null
                    },
                    onOpenUpload = { appState.moreSubscreen = MoreSubscreen.Upload },
                    onChromeVisibleChange = { appState.moreChromeVisible = it },
                    attributions = attributions
                )
            }
        }
    }
}

/**
 * Destino de la capa de overlay (detalle de álbum o carpeta, Buscar o una
 * subpantalla de Más). Se compone en el mismo sitio que el `when` en línea, dentro
 * del Box animado que remonta `key(overlayKey)`.
 */
@Composable
internal fun AuthenticatedOverlayDestination(host: AuthenticatedContentHost) {
    with(host) {
        when {
            appState.selectedTab == MainTab.Timeline && appState.moreSubscreen == null -> {
                // shown by the pager base layer
            }
            appState.selectedTab == MainTab.Albums && appState.moreSubscreen == null -> {
                val openedAlbum = appState.selectedAlbum
                if (openedAlbum != null) {
                    AlbumDetailScreen(
                        album = openedAlbum,
                        onItemClick = { index ->
                            appState.assetDetail = AssetDetailContext(
                                // Grid renders the re-sorted displayItems, so
                                // the tapped index is into that list — not the
                                // raw server-order items.
                                items = albumDetailState.displayItems,
                                startIndex = index,
                                source = AssetDetailContext.Source.Album,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = { id, isFav ->
                                    albumDetailViewModel.setFavorite(id, isFav)
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                        },
                        onBack = albumBack,
                        onShare = {
                            albumSharesViewModel.open(openedAlbum.id)
                            appState.showShares = true
                        },
                        onEdit = {
                            if (openedAlbum.isSmart && openedAlbum.isOwner) {
                                appState.editingSmartAlbum = openedAlbum.copy(
                                    name = albumDetailState.albumName ?: openedAlbum.name,
                                    description = albumDetailState.albumDescription ?: openedAlbum.description,
                                )
                                appState.moreSubscreen = MoreSubscreen.SmartAlbumEditor
                            } else {
                                appState.showEditAlbum = true
                            }
                        },
                        onDelete = { appState.showDeleteAlbum = true },
                        onManageMembers = {
                            albumPermissionsViewModel.open(openedAlbum.id)
                            appState.showMembers = true
                        },
                        onLeave = { appState.showLeaveAlbum = true },
                        viewModel = albumDetailViewModel,
                        immersive = albumDetailImmersive,
                        onChromeVisibleChange = { appState.albumDetailChromeVisible = it }
                    )
                }
            }
            appState.selectedTab == MainTab.Folders && appState.moreSubscreen == null -> {
                val openedFolder = appState.selectedFolder
                if (openedFolder != null) {
                    com.photonne.app.ui.folder.FolderDetailScreen(
                        folderId = openedFolder.id,
                        folderName = openedFolder.name.ifBlank { openedFolder.path },
                        parentFolderId = openedFolder.parentFolderId,
                        title = (folderDetailState.folderName ?: openedFolder.name)
                            .ifBlank { openedFolder.path },
                        onBack = { appState.folderBack() },
                        onItemClick = { index ->
                            if (folderDetailState.isSelectionActive) {
                                folderDetailState.items.getOrNull(index)?.let {
                                    folderDetailViewModel.toggleSelection(it.id)
                                }
                            } else {
                                val folderState = folderDetailViewModel.state.value
                                appState.assetDetail = AssetDetailContext(
                                    items = folderState.items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = false,
                                    onLoadMore = {},
                                    onFavoriteChanged = { id, isFav ->
                                        folderDetailViewModel.setFavorite(id, isFav)
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        },
                        onItemLongClick = { index ->
                            folderDetailState.items.getOrNull(index)?.let {
                                folderDetailViewModel.toggleSelection(it.id)
                            }
                        },
                        onSubfolderClick = { subfolder ->
                            if (folderDetailState.isSubfolderSelectionActive) {
                                folderDetailViewModel.toggleSubfolderSelection(subfolder.id)
                            } else {
                                appState.folderBackStack.add(openedFolder)
                                appState.selectedFolder = subfolder
                            }
                        },
                        onSubfolderLongPress = { subfolder ->
                            folderDetailViewModel.selectSubfolder(subfolder.id)
                        },
                        viewModel = folderDetailViewModel,
                        actions = {
                            // Cada acción con el flag que comprueba el
                            // servidor (antes todo colgaba de isOwner) y sin
                            // tocar bibliotecas externas, como en la lista.
                            val openedIsExternal = openedFolder.externalLibraryId != null
                            FolderDetailChromeActions(
                                canEdit = openedFolder.canWrite && !openedIsExternal,
                                canDelete = openedFolder.canDelete && !openedIsExternal,
                                canManageMembers = openedFolder.isOwner && !openedIsExternal,
                                canMove = openedFolder.canWrite && !openedIsExternal,
                                onEdit = { appState.showEditFolder = true },
                                onMove = { appState.showMoveFolder = true },
                                onDelete = { appState.showDeleteFolder = true },
                                onManageMembers = {
                                    folderPermissionsViewModel.open(openedFolder.id)
                                    appState.showFolderMembers = true
                                },
                                canToggleTimeline = openedFolder.isShared &&
                                    openedFolder.externalLibraryId == null,
                                excludedFromDiscovery = openedFolder.excludedFromDiscovery,
                                onToggleTimeline = {
                                    val nextIncluded = openedFolder.excludedFromDiscovery
                                    foldersViewModel.setTimelineIncluded(
                                        openedFolder.id, included = nextIncluded
                                    )
                                    appState.selectedFolder = openedFolder.copy(
                                        excludedFromDiscovery = !nextIncluded
                                    )
                                },
                                onCreateSubfolder = if (!openedIsExternal && openedFolder.canWrite) {
                                    { appState.showCreateFolder = true }
                                } else null
                            )
                        },
                        immersive = folderDetailImmersive,
                        onChromeVisibleChange = { appState.folderDetailChromeVisible = it }
                    )
                }
            }
            appState.selectedTab == MainTab.Search && appState.moreSubscreen == null ->
                com.photonne.app.ui.search.SearchScreen(
                viewModel = searchViewModel,
                onOpenFilters = { appState.showSearchFilters = true },
                onBack = { appState.searchBack() },
                onChromeVisibleChange = { appState.searchChromeVisible = it },
                onItemClick = { index ->
                    if (searchState.isSelectionActive) {
                        searchState.results.getOrNull(index)?.let {
                            searchViewModel.toggleSelection(it.id)
                        }
                    } else {
                        appState.assetDetail = AssetDetailContext(
                            items = searchState.results,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = searchState.hasMore,
                            onLoadMore = searchViewModel::loadMore,
                            // La búsqueda pagina: el visor sigue a la lista
                            // viva en vez de pararse en la primera página.
                            feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                items = { searchState.results },
                                hasMore = { searchState.hasMore },
                                loadMore = searchViewModel::loadMore
                            ),
                            onFavoriteChanged = { id, isFav ->
                                searchViewModel.setFavorite(id, isFav)
                                timelineViewModel.setFavorite(id, isFav)
                            }
                        )
                    }
                },
                onItemLongClick = { index ->
                    searchState.results.getOrNull(index)?.let {
                        searchViewModel.toggleSelection(it.id)
                    }
                }
            )
            else -> MoreSubscreenOverlay(host)
        }
    }
}

/** Subpantallas de Más: biblioteca, organizar, utilidades, explorar y personas. */
@Composable
private fun MoreSubscreenOverlay(host: AuthenticatedContentHost) {
    with(host) {
        when (appState.moreSubscreen) {
            null -> {
                // The More grid is shown by the pager base layer; a
                // non-null subscreen renders its screen on top.
            }
            MoreSubscreen.SmartAlbumEditor -> com.photonne.app.ui.album.smart.SmartAlbumEditorScreen(
                editAlbum = appState.editingSmartAlbum,
                onBack = {
                    appState.moreSubscreen = null
                    appState.editingSmartAlbum = null
                },
                onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                onSaved = { saved ->
                    val wasEditing = appState.editingSmartAlbum != null
                    appState.moreSubscreen = null
                    appState.editingSmartAlbum = null
                    if (wasEditing) {
                        // La respuesta del PUT no trae portada ni miniaturas:
                        // se recarga la lista en vez de pisar la tarjeta.
                        albumsViewModel.refresh()
                        // Nuevas condiciones = otro contenido: si el álbum está
                        // abierto, se recarga al volver a él.
                        appState.selectedAlbum?.takeIf { it.id == saved.id }?.let { opened ->
                            appState.selectedAlbum = opened.copy(
                                name = saved.name,
                                description = saved.description,
                                assetCount = saved.assetCount,
                            )
                            albumDetailViewModel.refresh()
                        }
                    } else {
                        albumsViewModel.refresh()
                        appState.selectedTab = MainTab.Albums
                        appState.selectedAlbum = saved
                    }
                }
            )
            MoreSubscreen.Upload -> com.photonne.app.ui.upload.UploadScreen(
                state = uploadState,
                onPicked = { files ->
                    uploadViewModel.enqueue(files) { timelineViewModel.refresh() }
                },
                onPickerError = uploadViewModel::pickerErrorRaised,
                onRetry = { id ->
                    uploadViewModel.retry(id) { timelineViewModel.refresh() }
                },
                onRemove = uploadViewModel::remove,
                onCancelAll = uploadViewModel::cancelAll,
                onClearFinished = uploadViewModel::clearFinished,
                onDismissPickerError = uploadViewModel::clearPickerError,
                onViewBatch = {
                    uploadState.lastBatch?.takeIf { it.isNotEmpty() }?.let { batch ->
                        appState.assetDetail = AssetDetailContext(
                            items = batch,
                            startIndex = 0,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = { id, isFav ->
                                timelineViewModel.setFavorite(id, isFav)
                            }
                        )
                    }
                },
                onAddBatchToAlbum = { appState.bulkAddSource = BulkAddSource.Upload },
                onDismissBatch = uploadViewModel::dismissBatchSummary
            )
            MoreSubscreen.DeviceBackup ->
                com.photonne.app.ui.devicebackup.BackupScreen(
                    title = stringResource(Res.string.device_backup_title),
                    onBack = { appState.moreSubscreen = null },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = deviceBackupViewModel,
                    enrichmentViewModel = enrichmentStatusViewModel,
                    gallery = deviceGallery,
                    onOpenPending = {
                        appState.moreSubscreen = MoreSubscreen.DeviceBackupPending
                    },
                    onOpenEnrichment = {
                        appState.moreSubscreen = MoreSubscreen.EnrichmentStatus
                    }
                )
            MoreSubscreen.DeviceBackupPending ->
                com.photonne.app.ui.devicebackup.BackupPendingScreen(
                    title = stringResource(Res.string.backup_pending_screen_title),
                    onBack = { appState.moreSubscreen = MoreSubscreen.DeviceBackup },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = deviceBackupViewModel,
                    gallery = deviceGallery,
                    onOpenAsset = { item ->
                        appState.assetDetail = AssetDetailContext(
                            items = listOf(item),
                            startIndex = 0,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = { id, isFav ->
                                timelineViewModel.setFavorite(id, isFav)
                            }
                        )
                    }
                )
            MoreSubscreen.EnrichmentStatus ->
                com.photonne.app.ui.devicebackup.EnrichmentStatusScreen(
                    title = stringResource(Res.string.enrichment_screen_title),
                    onBack = { appState.moreSubscreen = MoreSubscreen.DeviceBackup },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = enrichmentStatusViewModel
                )
            MoreSubscreen.MyLinks ->
                com.photonne.app.ui.album.MyLinksScreen(
                    title = stringResource(Res.string.my_links_title),
                    onBack = { appState.moreSubscreen = null },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.UnsupportedFiles ->
                com.photonne.app.ui.library.UnsupportedFilesScreen(
                    state = unsupportedFilesState,
                    onLoad = unsupportedFilesViewModel::ensureLoaded,
                    onRefresh = unsupportedFilesViewModel::refresh,
                    onLoadMore = unsupportedFilesViewModel::loadMore,
                    onDownload = unsupportedFilesViewModel::download,
                    onDelete = unsupportedFilesViewModel::delete,
                    onClearDeleteError = unsupportedFilesViewModel::clearDeleteError,
                    onBack = { appState.moreSubscreen = MoreSubscreen.Utilities },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.OrganizeInbox ->
                com.photonne.app.ui.organize.OrganizeInboxScreen(
                    state = organizeInboxState,
                    onLoad = organizeInboxViewModel::ensureLoaded,
                    onRefresh = organizeInboxViewModel::refresh,
                    onLoadMore = organizeInboxViewModel::loadMore,
                    onItemClick = { index ->
                        if (organizeInboxState.isSelectionActive) {
                            organizeInboxState.items.getOrNull(index)?.let {
                                organizeInboxViewModel.toggleSelection(it.id)
                            }
                        } else {
                            appState.assetDetail = AssetDetailContext(
                                items = organizeInboxViewModel.state.value.items,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = organizeInboxState.hasMore,
                                onLoadMore = organizeInboxViewModel::loadMore,
                                feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                    items = { organizeInboxState.items },
                                    hasMore = { organizeInboxState.hasMore },
                                    loadMore = organizeInboxViewModel::loadMore
                                ),
                                onFavoriteChanged = { id, isFav ->
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                        }
                    },
                    onItemLongClick = { index ->
                        organizeInboxState.items.getOrNull(index)?.let {
                            organizeInboxViewModel.toggleSelection(it.id)
                        }
                    },
                    onBack = {
                        appState.moreSubscreen = null
                        foldersViewModel.refreshOrganizeCount()
                    },
                    onOpenRules = { appState.moreSubscreen = MoreSubscreen.OrganizeRule },
                    onPickSuggestion = organizeInboxViewModel::selectSuggestion,
                    onSeeAllItems = organizeInboxViewModel::showAllItems,
                    onBackToSuggestions = organizeInboxViewModel::showSuggestions,
                    onApplySelection = organizeInboxViewModel::applySelection,
                    onOpenExcluded = {
                        organizeExcludedViewModel.refresh()
                        appState.moreSubscreen = MoreSubscreen.OrganizeExcluded
                    },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.OrganizeExcluded ->
                com.photonne.app.ui.organize.OrganizeExcludedScreen(
                    state = organizeExcludedState,
                    onLoad = organizeExcludedViewModel::ensureLoaded,
                    onRefresh = organizeExcludedViewModel::refresh,
                    onLoadMore = organizeExcludedViewModel::loadMore,
                    onItemClick = { index ->
                        if (organizeExcludedState.isSelectionActive) {
                            organizeExcludedState.items.getOrNull(index)?.let {
                                organizeExcludedViewModel.toggleSelection(it.id)
                            }
                        } else {
                            appState.assetDetail = AssetDetailContext(
                                items = organizeExcludedViewModel.state.value.items,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = organizeExcludedState.hasMore,
                                onLoadMore = organizeExcludedViewModel::loadMore,
                                feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                    items = { organizeExcludedState.items },
                                    hasMore = { organizeExcludedState.hasMore },
                                    loadMore = organizeExcludedViewModel::loadMore
                                ),
                                onFavoriteChanged = { id, isFav ->
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                        }
                    },
                    onItemLongClick = { index ->
                        organizeExcludedState.items.getOrNull(index)?.let {
                            organizeExcludedViewModel.toggleSelection(it.id)
                        }
                    },
                    onBack = { appState.moreSubscreen = MoreSubscreen.OrganizeInbox },
                    onApplySelection = organizeExcludedViewModel::applySelection,
                    onErrorShown = organizeExcludedViewModel::clearError,
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.OrganizeRule ->
                com.photonne.app.ui.organize.OrganizeRuleScreen(
                    title = stringResource(Res.string.organize_rule_title),
                    onBack = { appState.moreSubscreen = MoreSubscreen.OrganizeInbox },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    destinations = foldersState.moveDestinations,
                    viewModel = organizeRuleViewModel,
                    reviewOpen = organizeRuleState.reviewGroups != null
                )
            MoreSubscreen.Utilities ->
                com.photonne.app.ui.utilities.UtilitiesHubScreen(
                    title = stringResource(Res.string.utilities_title),
                    onBack = { appState.moreSubscreen = null },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    onOpen = { entry ->
                        appState.moreSubscreen = when (entry) {
                            com.photonne.app.ui.utilities.UtilitiesEntry.Duplicates ->
                                MoreSubscreen.UtilitiesDuplicates
                            com.photonne.app.ui.utilities.UtilitiesEntry.LargeFiles ->
                                MoreSubscreen.UtilitiesLargeFiles
                            com.photonne.app.ui.utilities.UtilitiesEntry.Locations ->
                                MoreSubscreen.UtilitiesLocations
                            com.photonne.app.ui.utilities.UtilitiesEntry.UnsupportedFiles ->
                                MoreSubscreen.UnsupportedFiles
                        }
                    }
                )
            MoreSubscreen.UtilitiesDuplicates ->
                com.photonne.app.ui.utilities.UtilitiesDuplicatesScreen(
                    title = stringResource(Res.string.utilities_section_duplicates),
                    onBack = { appState.moreSubscreen = MoreSubscreen.Utilities },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = utilitiesDuplicatesViewModel,
                    baseUrl = apiBaseUrl,
                    confirmOpen = appState.showDuplicatesConfirm,
                    onConfirmOpenChange = { appState.showDuplicatesConfirm = it },
                    onUndoTrash = { ids ->
                        actionsViewModel.undoBulk(
                            com.photonne.app.ui.actions.BulkUndoKind.Trash,
                            ids
                        ) {
                            utilitiesDuplicatesViewModel.refresh()
                            timelineViewModel.refresh()
                        }
                    },
                    onOpenAsset = { index, items ->
                        appState.assetDetail = AssetDetailContext(
                            items = items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = { id, isFav ->
                                timelineViewModel.setFavorite(id, isFav)
                            }
                        )
                    }
                )
            MoreSubscreen.UtilitiesLargeFiles ->
                com.photonne.app.ui.utilities.UtilitiesLargeFilesScreen(
                    title = stringResource(Res.string.utilities_section_large_files),
                    onBack = { appState.moreSubscreen = MoreSubscreen.Utilities },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = utilitiesLargeFilesViewModel,
                    baseUrl = apiBaseUrl,
                    onAssetClick = { index, items ->
                        appState.assetDetail = AssetDetailContext(
                            items = items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = { id, isFav ->
                                timelineViewModel.setFavorite(id, isFav)
                            }
                        )
                    }
                )
            MoreSubscreen.UtilitiesLocations ->
                com.photonne.app.ui.utilities.UtilitiesLocationsScreen(
                    title = stringResource(Res.string.utilities_section_locations),
                    onBack = { appState.moreSubscreen = MoreSubscreen.Utilities },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = utilitiesLocationsViewModel,
                    onFolderClick = { node ->
                        coroutineScope.launch {
                            // El resumen real trae los permisos (escribir,
                            // borrar); sin red se abre con lo que da el
                            // árbol, en solo lectura, y el detalle pinta
                            // su propio error con Reintentar.
                            val folder = runCatching { foldersRepository.get(node.id) }
                                .getOrElse {
                                    com.photonne.app.data.models.FolderSummary(
                                        id = node.id,
                                        path = node.path,
                                        name = node.name,
                                        parentFolderId = node.parentFolderId,
                                        createdAt = kotlin.time.Clock.System.now(),
                                        assetCount = node.assetCount,
                                        isShared = node.isShared,
                                        isOwner = node.isOwner,
                                        canWrite = false,
                                        canDelete = false,
                                        externalLibraryId = node.externalLibraryId
                                    )
                                }
                            appState.folderReturnTo = appState.selectedTab to MoreSubscreen.UtilitiesLocations
                            appState.folderBackStack.clear()
                            appState.selectedFolder = folder
                            appState.moreSubscreen = null
                            appState.selectedTab = MainTab.Folders
                        }
                    }
                )
            MoreSubscreen.Memories ->
                com.photonne.app.ui.memories.MemoriesScreen(
                    viewModel = memoryFeedViewModel,
                    baseUrl = apiBaseUrl,
                    onOpenMemory = { detail ->
                        appState.memoryDetail = com.photonne.app.ui.memories.MemoryDetailContext(
                            title = detail.title,
                            subtitle = detail.subtitle,
                            coverAssetId = detail.coverAssetId,
                            items = detail.assets
                        )
                    },
                    onBack = { appState.moreSubscreen = null },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.ExploreScenes ->
                com.photonne.app.ui.explore.ExploreScenesScreen(
                    viewModel = exploreFacetsViewModel,
                    // Tapping a scene jumps to the Search tab pre-filtered
                    // by that label — same flow as the PWA, where Explorar
                    // is just a deep-linking surface for the search engine.
                    onSceneClick = { label ->
                        searchViewModel.showResultsForSceneLabel(label)
                        appState.searchReturnTo = appState.selectedTab to MoreSubscreen.ExploreScenes
                        appState.moreSubscreen = null
                        appState.selectedTab = MainTab.Search
                    },
                    onBack = { appState.moreSubscreen = null },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.ExploreObjects ->
                com.photonne.app.ui.explore.ExploreObjectsScreen(
                    viewModel = exploreFacetsViewModel,
                    onObjectClick = { label ->
                        searchViewModel.showResultsForObjectLabel(label)
                        appState.searchReturnTo = appState.selectedTab to MoreSubscreen.ExploreObjects
                        appState.moreSubscreen = null
                        appState.selectedTab = MainTab.Search
                    },
                    onBack = { appState.moreSubscreen = null },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.Map -> com.photonne.app.ui.map.MapScreen(
                viewModel = mapViewModel,
                onPointOpen = { visiblePoints, index ->
                    // Single-marker tap → open the asset viewer
                    // seeded with every point visible in the
                    // viewport (by date), starting at the tapped
                    // one, so it swipes like a cluster does. The
                    // viewer re-fetches asset detail on display, so
                    // synthetic TimelineItems are enough.
                    appState.assetDetail = AssetDetailContext(
                        items = visiblePoints.map { it.toSyntheticTimelineItem() },
                        startIndex = index,
                        source = AssetDetailContext.Source.Timeline,
                        hasMore = false,
                        onLoadMore = {},
                        onFavoriteChanged = { id, isFav ->
                            timelineViewModel.setFavorite(id, isFav)
                        }
                    )
                },
                onSheetPhotoOpen = { sheetPoints, index ->
                    // Thumbnail tap in the persistent sheet → open
                    // the viewer seeded with the sheet's whole list
                    // (viewport or tapped cluster) so it swipes.
                    val items = sheetPoints.map { it.toSyntheticTimelineItem() }
                    appState.assetDetail = AssetDetailContext(
                        items = items,
                        startIndex = index,
                        source = AssetDetailContext.Source.Timeline,
                        hasMore = false,
                        onLoadMore = {},
                        onFavoriteChanged = { id, isFav ->
                            timelineViewModel.setFavorite(id, isFav)
                        }
                    )
                },
                onBulkAddToAlbum = { appState.bulkAddSource = BulkAddSource.Map },
                onBack = { appState.moreSubscreen = null }
            )
            MoreSubscreen.PeopleSuggestions ->
                com.photonne.app.ui.people.PersonSuggestionsScreen(
                    state = suggestionsState,
                    title = (suggestionsState.personName ?: appState.selectedPerson?.name).orEmpty(),
                    isBulkMutating = suggestionsState.isBulkMutating,
                    onAccept = personSuggestionsViewModel::acceptFace,
                    onDismissFace = personSuggestionsViewModel::dismissFace,
                    onLoadMore = personSuggestionsViewModel::loadMore,
                    onOpen = {
                        appState.selectedPerson?.let {
                            personSuggestionsViewModel.open(it.id, it.name)
                        }
                    },
                    onBack = { appState.moreSubscreen = MoreSubscreen.People },
                    // Actúan también sobre las páginas no cargadas, así
                    // que primero confirman con el recuento del servidor.
                    onAcceptAll = { appState.showAcceptAllSuggestions = true },
                    onDismissAll = { appState.showDismissAllSuggestions = true },
                    onRefresh = personSuggestionsViewModel::refresh,
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.People -> {
                val person = appState.selectedPerson
                if (person == null) {
                    com.photonne.app.ui.people.PeopleScreen(
                        state = peopleState,
                        onPersonClick = { picked ->
                            appState.selectedPerson = picked
                            personDetailViewModel.open(picked.id, picked.name)
                        },
                        onLoadMore = peopleViewModel::loadMore,
                        onLoad = peopleViewModel::ensureLoaded,
                        onRefresh = peopleViewModel::refresh,
                        onBack = { appState.moreSubscreen = null },
                        onToggleSearch = peopleViewModel::toggleSearch,
                        onSearchChange = peopleViewModel::setSearch,
                        onSortChange = peopleViewModel::setSort,
                        onRecluster = {
                            // El servidor devuelve cuántas personas nuevas
                            // salieron del reagrupado; antes se descartaba.
                            peopleViewModel.recluster { created ->
                                coroutineScope.launch {
                                    snackbarController.show(
                                        if (created > 0) {
                                            org.jetbrains.compose.resources.getPluralString(
                                                Res.plurals.people_recluster_done,
                                                created, created
                                            )
                                        } else {
                                            org.jetbrains.compose.resources.getString(
                                                Res.string.people_recluster_done_none
                                            )
                                        }
                                    )
                                }
                            }
                        },
                        onToggleHidden = peopleViewModel::toggleShowHidden,
                        onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                    )
                } else {
                    com.photonne.app.ui.people.PersonDetailScreen(
                        state = personDetailState,
                        title = personDetailState.personName ?: person.name.orEmpty(),
                        isHidden = person.isHidden,
                        onRetry = { personDetailViewModel.open(person.id, person.name) },
                        onRefresh = personDetailViewModel::refresh,
                        onItemClick = { index ->
                            if (personDetailState.isSelectionActive) {
                                personDetailState.items.getOrNull(index)?.let {
                                    personDetailViewModel.toggleSelection(it.id)
                                }
                            } else {
                                appState.assetDetail = AssetDetailContext(
                                    items = personDetailState.items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = personDetailState.hasMore,
                                    onLoadMore = personDetailViewModel::loadMore,
                                    feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                        items = { personDetailState.items },
                                        hasMore = { personDetailState.hasMore },
                                        loadMore = personDetailViewModel::loadMore
                                    ),
                                    onFavoriteChanged = { id, isFav ->
                                        personDetailViewModel.setFavorite(id, isFav)
                                        timelineViewModel.setFavorite(id, isFav)
                                    }
                                )
                            }
                        },
                        onItemLongClick = { index ->
                            personDetailState.items.getOrNull(index)?.let {
                                personDetailViewModel.toggleSelection(it.id)
                            }
                        },
                        onLoadMore = personDetailViewModel::loadMore,
                        onApplySelection = personDetailViewModel::applySelection,
                        onBack = appState::personBack,
                        onRename = { appState.showRenamePerson = true },
                        onSuggestions = {
                            personSuggestionsViewModel.open(person.id, person.name)
                            appState.moreSubscreen = MoreSubscreen.PeopleSuggestions
                        },
                        onMerge = { appState.showMergePicker = true },
                        onToggleHidden = {
                            if (person.isHidden) {
                                peopleViewModel.unhide(person.id) {
                                    appState.selectedPerson = person.copy(isHidden = false)
                                }
                            } else {
                                peopleViewModel.hide(person.id) {
                                    appState.selectedPerson = null
                                }
                            }
                        },
                        onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                    )
                }
            }
            MoreSubscreen.DeviceFolders -> com.photonne.app.ui.folder.DeviceFoldersScreen(
                backedUpUris = remember(deviceBackupState.folders) {
                    deviceBackupState.folders.map { it.uri }.toSet()
                },
                onAddToBackup = deviceBackupViewModel::onFolderPicked,
                onOpenBucket = { bucket ->
                    appState.deviceFolderBucket = bucket
                    appState.moreSubscreen = MoreSubscreen.DeviceFolderDetail
                },
                onBack = { appState.moreSubscreen = null },
                onChromeVisibleChange = { appState.subscreenChromeVisible = it }
            )
            MoreSubscreen.DeviceFolderDetail -> appState.deviceFolderBucket?.let { bucket ->
                com.photonne.app.ui.folder.DeviceFolderDetailScreen(
                    bucket = bucket,
                    onOpenAsset = { items, index ->
                        appState.assetDetail = AssetDetailContext(
                            items = items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = { _, _ -> }
                        )
                    },
                    onBack = { appState.moreSubscreen = MoreSubscreen.DeviceFolders },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            }
            MoreSubscreen.Favorites -> com.photonne.app.ui.library.FavoritesScreen(
                state = favoritesState,
                onItemClick = { index ->
                    if (favoritesState.isSelectionActive) {
                        favoritesState.items.getOrNull(index)?.let {
                            favoritesViewModel.toggleSelection(it.id)
                        }
                    } else {
                        appState.assetDetail = AssetDetailContext(
                            items = favoritesState.items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = favoritesState.hasMore,
                            onLoadMore = favoritesViewModel::loadMore,
                            feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                items = { favoritesState.items },
                                hasMore = { favoritesState.hasMore },
                                loadMore = favoritesViewModel::loadMore
                            ),
                            onFavoriteChanged = { id, isFav ->
                                favoritesViewModel.setFavorite(id, isFav)
                                timelineViewModel.setFavorite(id, isFav)
                            }
                        )
                    }
                },
                onItemLongClick = { index ->
                    favoritesState.items.getOrNull(index)?.let {
                        favoritesViewModel.toggleSelection(it.id)
                    }
                },
                onLoadMore = favoritesViewModel::loadMore,
                onLoad = favoritesViewModel::ensureLoaded,
                onRefresh = favoritesViewModel::refresh,
                onApplySelection = favoritesViewModel::applySelection,
                onBack = { appState.moreSubscreen = null },
                onChromeVisibleChange = { appState.subscreenChromeVisible = it }
            )
            MoreSubscreen.Archived -> com.photonne.app.ui.library.ArchivedScreen(
                state = archivedState,
                onItemClick = { index ->
                    if (archivedState.isSelectionActive) {
                        archivedState.items.getOrNull(index)?.let {
                            archivedViewModel.toggleSelection(it.id)
                        }
                    } else {
                        appState.assetDetail = AssetDetailContext(
                            items = archivedState.items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Archive,
                            hasMore = archivedState.hasMore,
                            onLoadMore = archivedViewModel::loadMore,
                            feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                items = { archivedState.items },
                                hasMore = { archivedState.hasMore },
                                loadMore = archivedViewModel::loadMore
                            ),
                            onFavoriteChanged = { id, isFav ->
                                archivedViewModel.setFavorite(id, isFav)
                                timelineViewModel.setFavorite(id, isFav)
                            }
                        )
                    }
                },
                onItemLongClick = { index ->
                    archivedState.items.getOrNull(index)?.let {
                        archivedViewModel.toggleSelection(it.id)
                    }
                },
                onLoadMore = archivedViewModel::loadMore,
                onApplySelection = archivedViewModel::applySelection,
                onLoad = archivedViewModel::ensureLoaded,
                onRefresh = archivedViewModel::refresh,
                onBack = { appState.moreSubscreen = null },
                onUnarchiveAll = { appState.showUnarchiveAll = true },
                onChromeVisibleChange = { appState.subscreenChromeVisible = it }
            )
            MoreSubscreen.Trash -> Box(modifier = Modifier.fillMaxSize()) {
                // La pantalla reserva siempre el hueco del cromo flotante:
                // sin selección lo dibuja ella misma y, con selección, lo
                // ocupa la cápsula de selección (rama del topBar).
                val trashSelecting = trashState.isSelectionActive
                // La rejilla de la papelera personal (hermana Haze +
                // fuente de scroll) para que el cromo se acople en reposo.
                val trashHazeState = remember { HazeState() }
                val trashGridState = rememberLazyGridState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = subscreenChromeReservedTop())
                ) {
                when (appState.trashTab) {
                    com.photonne.app.ui.library.TrashTab.Personal ->
                        com.photonne.app.ui.library.TrashScreen(
                            state = trashState,
                            onItemClick = { index ->
                                if (trashState.isSelectionActive) {
                                    trashState.items.getOrNull(index)?.let {
                                        trashViewModel.toggleSelection(it.id)
                                    }
                                } else {
                                    appState.assetDetail = AssetDetailContext(
                                        items = trashState.items,
                                        startIndex = index,
                                        source = AssetDetailContext.Source.Trash,
                                        hasMore = trashState.hasMore,
                                        onLoadMore = trashViewModel::loadMore,
                                        feed = com.photonne.app.ui.asset.AssetViewerFeed(
                                            items = { trashState.items },
                                            hasMore = { trashState.hasMore },
                                            loadMore = trashViewModel::loadMore
                                        ),
                                        onFavoriteChanged = { id, isFav ->
                                            // Trashed assets ignore favorite changes
                                            // server-side, but keep the local copy
                                            // consistent if the viewer toggles.
                                            timelineViewModel.setFavorite(id, isFav)
                                        }
                                    )
                                }
                            },
                            onItemLongClick = { index ->
                                trashState.items.getOrNull(index)?.let {
                                    trashViewModel.toggleSelection(it.id)
                                }
                            },
                            onLoadMore = trashViewModel::loadMore,
                            onLoad = trashViewModel::ensureLoaded,
                            onRefresh = trashViewModel::refresh,
                            onApplySelection = trashViewModel::applySelection,
                            gridState = trashGridState,
                            hazeState = trashHazeState
                        )
                    com.photonne.app.ui.library.TrashTab.Shared ->
                        com.photonne.app.ui.admin.AdminSharedTrashScreen(
                            viewModel = adminSharedTrashViewModel
                        )
                }
                }
                if (!trashSelecting) {
                    val count = trashState.items.size
                    SubscreenFloatingChrome(
                        // Con el ámbito escondido en la hoja, el título
                        // dice cuál de las dos papeleras se ve.
                        title = stringResource(
                            if (appState.trashTab == com.photonne.app.ui.library.TrashTab.Shared) {
                                Res.string.admin_shared_trash
                            } else Res.string.trash_title
                        ),
                        onBack = { appState.moreSubscreen = null },
                        // La rejilla personal manda el acople/ocultar; en
                        // Compartida no está compuesta, así que queda en
                        // reposo (acoplada), que es lo correcto.
                        scroll = SubscreenScroll(
                            firstVisibleItemIndex = { trashGridState.firstVisibleItemIndex },
                            firstVisibleItemScrollOffset = { trashGridState.firstVisibleItemScrollOffset },
                            isScrollInProgress = { trashGridState.isScrollInProgress },
                            scrollToTopMinIndex = 4,
                            onScrollToTop = { trashGridState.animateScrollToItem(0) }
                        ),
                        hazeState = trashHazeState,
                        onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                        // Restaurar todo / vaciar solo aplican a la papelera
                        // personal; en la compartida la propia pantalla pinta
                        // sus acciones, así que el ⋮ no sale. El filtro de
                        // ámbito sí, en las dos.
                        actions = {
                            TrashChromeActions(
                                tab = appState.trashTab,
                                showBulkActions = appState.trashTab ==
                                    com.photonne.app.ui.library.TrashTab.Personal && count > 0,
                                onOpenScope = { appState.showTrashScope = true },
                                onRestoreAll = { appState.showRestoreAllTrash = true },
                                onEmptyTrash = { appState.showEmptyTrash = true }
                            )
                        }
                    )
                }
                if (appState.showTrashScope) {
                    com.photonne.app.ui.library.TrashScopeSheet(
                        selected = appState.trashTab,
                        onSelect = { tab ->
                            if (tab != appState.trashTab) {
                                // Leaving the personal tab drops its selection
                                // so the top bar/back don't act on a hidden tab.
                                trashViewModel.clearSelection()
                                appState.trashTab = tab
                            }
                            appState.showTrashScope = false
                        },
                        onDismiss = { appState.showTrashScope = false }
                    )
                }
            }
            else -> AccountAdminSubscreenOverlay(host)
        }
    }
}

/** Subpantallas de Más: notificaciones, cuenta y administración. */
@Composable
private fun AccountAdminSubscreenOverlay(host: AuthenticatedContentHost) {
    with(host) {
        when (appState.moreSubscreen) {
            MoreSubscreen.Notifications -> {
                val noScreenMessage =
                    stringResource(Res.string.notifications_no_screen)
                com.photonne.app.ui.notifications.NotificationsScreen(
                    title = stringResource(Res.string.notifications_title),
                    onBack = { appState.moreSubscreen = null },
                    canMarkAllRead = notificationsState.unreadCount > 0 &&
                        !notificationsState.isMarkingAllRead,
                    onMarkAllRead = notificationsViewModel::markAllRead,
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = notificationsViewModel,
                    onNavigate = { url ->
                        // Map known server actionUrls to in-app
                        // subscreens; unknown routes are a no-op.
                        val path = url.substringBefore('?').trimEnd('/')
                        when {
                            path == "/shared-trash" ||
                                path.endsWith("/shared-trash") -> {
                                appState.trashTab = com.photonne.app.ui.library.TrashTab.Shared
                                appState.moreSubscreen = MoreSubscreen.Trash
                            }
                            path == "/admin/enrichment-failures" ||
                                path.endsWith("/admin/enrichment-failures") -> {
                                appState.adminEnrichmentInitialType = url
                                    .substringAfter('?', "")
                                    .split('&')
                                    .firstOrNull { it.startsWith("type=") }
                                    ?.substringAfter('=')
                                    ?.takeIf { it.isNotBlank() }
                                appState.adminEnrichmentReturnTo = MoreSubscreen.AdminSystemHub
                                appState.moreSubscreen = MoreSubscreen.AdminSystemEnrichmentFailures
                            }
                            path == "/admin/stats" ||
                                path.endsWith("/admin/stats") -> {
                                appState.moreSubscreen = MoreSubscreen.AdminStats
                            }
                            path == "/people" || path.endsWith("/people") -> {
                                appState.moreSubscreen = MoreSubscreen.People
                            }
                            // Ruta sin pantalla nativa: decirlo vale
                            // más que un toque que no hace nada.
                            else -> snackbarController.show(noScreenMessage)
                        }
                    }
                )
            }
            MoreSubscreen.AccountSettings -> {
                // Resumen de cuota en la fila de Almacenamiento: se pide
                // una vez y se reutiliza (el viewmodel vive con la app).
                LaunchedEffect(Unit) { accountStorageViewModel.loadIfNeeded() }
                val accountStorageState by accountStorageViewModel.state
                    .collectAsStateWithLifecycle()
                com.photonne.app.ui.settings.AccountSettingsScreen(
                    title = stringResource(Res.string.account_settings_title),
                    storage = accountStorageState.info,
                    onBack = { appState.moreSubscreen = null },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    activityNotificationsEnabled = activityNotificationsEnabled
                        .takeIf { activityNotifications.isSupported },
                    onActivityNotificationsChange = activityNotifications::setEnabled,
                    onOpen = { section ->
                        appState.profileOpenedFromMore = false
                        appState.moreSubscreen = when (section) {
                            com.photonne.app.ui.settings.AccountSettingsSection.Profile ->
                                MoreSubscreen.AccountProfile
                            com.photonne.app.ui.settings.AccountSettingsSection.Security ->
                                MoreSubscreen.AccountSecurity
                            com.photonne.app.ui.settings.AccountSettingsSection.Appearance ->
                                MoreSubscreen.AccountAppearance
                            com.photonne.app.ui.settings.AccountSettingsSection.Storage ->
                                MoreSubscreen.AccountStorage
                            com.photonne.app.ui.settings.AccountSettingsSection.Connection ->
                                MoreSubscreen.AccountConnection
                        }
                    }
                )
            }
            MoreSubscreen.AccountProfile ->
                com.photonne.app.ui.settings.AccountProfileScreen(
                    title = stringResource(Res.string.account_section_profile),
                    onBack = {
                        appState.moreSubscreen = if (appState.profileOpenedFromMore) null
                        else MoreSubscreen.AccountSettings
                    },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = accountProfileViewModel
                )
            MoreSubscreen.AccountSecurity ->
                com.photonne.app.ui.settings.AccountSecurityScreen(
                    title = stringResource(Res.string.account_section_security),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AccountSettings },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = accountSecurityViewModel
                )
            MoreSubscreen.AccountAppearance ->
                com.photonne.app.ui.settings.AccountAppearanceScreen(
                    title = stringResource(Res.string.account_section_appearance),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AccountSettings },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = appearanceViewModel
                )
            MoreSubscreen.AccountStorage ->
                com.photonne.app.ui.settings.AccountStorageScreen(
                    title = stringResource(Res.string.account_section_storage),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AccountSettings },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = accountStorageViewModel
                )
            MoreSubscreen.AccountConnection ->
                com.photonne.app.ui.settings.AccountConnectionScreen(
                    title = stringResource(Res.string.account_section_connection),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AccountSettings },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = deviceConnectionViewModel
                )
            MoreSubscreen.Administration ->
                com.photonne.app.ui.admin.AdministrationScreen(
                    title = stringResource(Res.string.administration_title),
                    onBack = { appState.moreSubscreen = null },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    onOpen = { section ->
                        appState.moreSubscreen = when (section) {
                            com.photonne.app.ui.admin.AdministrationSection.Users ->
                                MoreSubscreen.AdminUsers
                            com.photonne.app.ui.admin.AdministrationSection.Libraries ->
                                MoreSubscreen.AdminLibraries
                            com.photonne.app.ui.admin.AdministrationSection.Stats ->
                                MoreSubscreen.AdminStats
                            com.photonne.app.ui.admin.AdministrationSection.Settings ->
                                MoreSubscreen.AdminSettingsHub
                            com.photonne.app.ui.admin.AdministrationSection.System ->
                                MoreSubscreen.AdminSystemHub
                        }
                    }
                )
            MoreSubscreen.AdminUsers ->
                com.photonne.app.ui.admin.AdminUsersScreen(
                    title = stringResource(Res.string.admin_section_users),
                    onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                    onCreateNew = {
                        appState.adminUserEditorId = null
                        adminUsersViewModel.clearMessages()
                        appState.moreSubscreen = MoreSubscreen.AdminUserEditor
                    },
                    viewModel = adminUsersViewModel,
                    onEdit = { user ->
                        appState.adminUserEditorId = user.id
                        adminUsersViewModel.clearMessages()
                        appState.moreSubscreen = MoreSubscreen.AdminUserEditor
                    },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.AdminUserEditor ->
                com.photonne.app.ui.admin.AdminUserEditorScreen(
                    title = stringResource(
                        if (appState.adminUserEditorId == null) Res.string.admin_user_action_new
                        else Res.string.admin_user_edit_title
                    ),
                    onBack = {
                        appState.adminUserEditorId = null
                        adminUsersViewModel.clearMessages()
                        appState.moreSubscreen = MoreSubscreen.AdminUsers
                    },
                    viewModel = adminUsersViewModel,
                    userId = appState.adminUserEditorId,
                    onDone = {
                        appState.adminUserEditorId = null
                        appState.moreSubscreen = MoreSubscreen.AdminUsers
                    },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.AdminLibraries -> {
                val usersState by adminUsersViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) { adminUsersViewModel.ensureLoaded() }
                com.photonne.app.ui.admin.AdminLibrariesScreen(
                    title = stringResource(Res.string.admin_section_libraries),
                    onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                    onCreateNew = {
                        appState.adminLibraryEditorId = null
                        adminLibrariesViewModel.clearMessages()
                        appState.moreSubscreen = MoreSubscreen.AdminLibraryEditor
                    },
                    viewModel = adminLibrariesViewModel,
                    knownUsers = usersState.users,
                    onEdit = { library ->
                        appState.adminLibraryEditorId = library.id
                        adminLibrariesViewModel.clearMessages()
                        appState.moreSubscreen = MoreSubscreen.AdminLibraryEditor
                    },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            }
            MoreSubscreen.AdminLibraryEditor ->
                com.photonne.app.ui.admin.AdminLibraryEditorScreen(
                    title = stringResource(
                        if (appState.adminLibraryEditorId == null) Res.string.admin_libraries_action_new
                        else Res.string.admin_libraries_edit_title
                    ),
                    onBack = {
                        appState.adminLibraryEditorId = null
                        adminLibrariesViewModel.clearMessages()
                        appState.moreSubscreen = MoreSubscreen.AdminLibraries
                    },
                    viewModel = adminLibrariesViewModel,
                    libraryId = appState.adminLibraryEditorId,
                    onDone = {
                        appState.adminLibraryEditorId = null
                        appState.moreSubscreen = MoreSubscreen.AdminLibraries
                    },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it }
                )
            MoreSubscreen.AdminStats ->
                com.photonne.app.ui.admin.AdminStatsScreen(
                    title = stringResource(Res.string.admin_section_stats),
                    onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminStatsViewModel
                )
            MoreSubscreen.AdminSettingsHub ->
                com.photonne.app.ui.admin.AdminSettingsHubScreen(
                    title = stringResource(Res.string.admin_section_settings),
                    onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    onOpen = { entry ->
                        appState.moreSubscreen = when (entry) {
                            com.photonne.app.ui.admin.AdminSettingsEntry.FaceRecognition ->
                                MoreSubscreen.AdminSettingsFaceRecognition
                            com.photonne.app.ui.admin.AdminSettingsEntry.ObjectDetection ->
                                MoreSubscreen.AdminSettingsObjectDetection
                            com.photonne.app.ui.admin.AdminSettingsEntry.SceneClassification ->
                                MoreSubscreen.AdminSettingsSceneClassification
                            com.photonne.app.ui.admin.AdminSettingsEntry.TextRecognition ->
                                MoreSubscreen.AdminSettingsTextRecognition
                            com.photonne.app.ui.admin.AdminSettingsEntry.ImageEmbedding ->
                                MoreSubscreen.AdminSettingsImageEmbedding
                            com.photonne.app.ui.admin.AdminSettingsEntry.ImageSettings ->
                                MoreSubscreen.AdminSettingsImage
                            com.photonne.app.ui.admin.AdminSettingsEntry.Metadata ->
                                MoreSubscreen.AdminSettingsMetadata
                            com.photonne.app.ui.admin.AdminSettingsEntry.NightlyTasks ->
                                MoreSubscreen.AdminSettingsNightly
                            com.photonne.app.ui.admin.AdminSettingsEntry.Notifications ->
                                MoreSubscreen.AdminSettingsNotifications
                            com.photonne.app.ui.admin.AdminSettingsEntry.Server ->
                                MoreSubscreen.AdminSettingsServer
                            com.photonne.app.ui.admin.AdminSettingsEntry.Trash ->
                                MoreSubscreen.AdminSettingsTrash
                            com.photonne.app.ui.admin.AdminSettingsEntry.UserDefaults ->
                                MoreSubscreen.AdminSettingsUserDefaults
                            com.photonne.app.ui.admin.AdminSettingsEntry.VersionCheck ->
                                MoreSubscreen.AdminSettingsVersion
                        }
                    }
                )
            MoreSubscreen.AdminSettingsFaceRecognition -> {
                val vm: com.photonne.app.ui.admin.AdminFaceRecognitionSettingsViewModel =
                    koinViewModel()
                com.photonne.app.ui.admin.AdminFaceRecognitionSettingsScreen(
                    title = stringResource(Res.string.admin_settings_face_recognition),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = vm,
                    onOpenNightly = {
                        appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                    },
                )
            }
            MoreSubscreen.AdminSettingsObjectDetection -> {
                val vm: com.photonne.app.ui.admin.AdminObjectDetectionSettingsViewModel =
                    koinViewModel()
                com.photonne.app.ui.admin.AdminObjectDetectionSettingsScreen(
                    title = stringResource(Res.string.admin_settings_object_detection),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = vm,
                    onOpenNightly = {
                        appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                    },
                )
            }
            MoreSubscreen.AdminSettingsSceneClassification -> {
                val vm: com.photonne.app.ui.admin.AdminSceneClassificationSettingsViewModel =
                    koinViewModel()
                com.photonne.app.ui.admin.AdminSceneClassificationSettingsScreen(
                    title = stringResource(Res.string.admin_settings_scene_classification),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = vm,
                    onOpenNightly = {
                        appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                    },
                )
            }
            MoreSubscreen.AdminSettingsTextRecognition -> {
                val vm: com.photonne.app.ui.admin.AdminTextRecognitionSettingsViewModel =
                    koinViewModel()
                com.photonne.app.ui.admin.AdminTextRecognitionSettingsScreen(
                    title = stringResource(Res.string.admin_settings_text_recognition),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = vm,
                    onOpenNightly = {
                        appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                    },
                )
            }
            MoreSubscreen.AdminSettingsImageEmbedding -> {
                val vm: com.photonne.app.ui.admin.AdminImageEmbeddingSettingsViewModel =
                    koinViewModel()
                com.photonne.app.ui.admin.AdminImageEmbeddingSettingsScreen(
                    title = stringResource(Res.string.admin_settings_image_embedding),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = vm,
                    onOpenNightly = {
                        appState.moreSubscreen = MoreSubscreen.AdminSettingsNightly
                    },
                )
            }
            MoreSubscreen.AdminSettingsImage ->
                com.photonne.app.ui.admin.AdminImageSettingsScreen(
                    title = stringResource(Res.string.admin_settings_image),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminImageSettingsViewModel
                )
            MoreSubscreen.AdminSettingsMetadata ->
                com.photonne.app.ui.admin.AdminMetadataSettingsScreen(
                    title = stringResource(Res.string.admin_settings_metadata),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminMetadataSettingsViewModel
                )
            MoreSubscreen.AdminSettingsNightly ->
                com.photonne.app.ui.admin.AdminNightlySettingsScreen(
                    title = stringResource(Res.string.admin_settings_nightly),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminNightlySettingsViewModel
                )
            MoreSubscreen.AdminSettingsNotifications ->
                com.photonne.app.ui.admin.AdminNotificationSettingsScreen(
                    title = stringResource(Res.string.admin_settings_notifications),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminNotificationSettingsViewModel
                )
            MoreSubscreen.AdminSettingsServer ->
                com.photonne.app.ui.admin.AdminServerSettingsScreen(
                    title = stringResource(Res.string.admin_settings_server),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminServerSettingsViewModel
                )
            MoreSubscreen.AdminSettingsTrash ->
                com.photonne.app.ui.admin.AdminTrashSettingsScreen(
                    title = stringResource(Res.string.admin_settings_trash),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminTrashSettingsViewModel
                )
            MoreSubscreen.AdminSettingsUserDefaults ->
                com.photonne.app.ui.admin.AdminUserDefaultsScreen(
                    title = stringResource(Res.string.admin_settings_user_defaults),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminUserDefaultsViewModel
                )
            MoreSubscreen.AdminSettingsVersion ->
                com.photonne.app.ui.admin.AdminServerScreen(
                    title = stringResource(Res.string.admin_settings_version),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSettingsHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminVersionViewModel
                )
            MoreSubscreen.AdminSystemHub ->
                com.photonne.app.ui.admin.AdminSystemHubScreen(
                    title = stringResource(Res.string.admin_section_system),
                    onBack = { appState.moreSubscreen = MoreSubscreen.Administration },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    onOpen = { entry ->
                        appState.moreSubscreen = when (entry) {
                            com.photonne.app.ui.admin.AdminSystemEntry.RunTasks ->
                                MoreSubscreen.AdminSystemRunTasks
                            com.photonne.app.ui.admin.AdminSystemEntry.EnrichmentFailures -> {
                                appState.adminEnrichmentInitialType = null
                                appState.adminEnrichmentReturnTo = MoreSubscreen.AdminSystemHub
                                MoreSubscreen.AdminSystemEnrichmentFailures
                            }
                            com.photonne.app.ui.admin.AdminSystemEntry.Backup ->
                                MoreSubscreen.AdminSystemBackup
                        }
                    }
                )
            MoreSubscreen.AdminSystemRunTasks -> {
                val vm: com.photonne.app.ui.admin.AdminRunTasksViewModel =
                    koinViewModel()
                com.photonne.app.ui.admin.AdminRunTasksScreen(
                    title = stringResource(Res.string.admin_system_run_tasks),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSystemHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = vm,
                    // Only Duplicates still drills into its own
                    // screen; pipeline + AI rows handle their
                    // entire UX inline on the hub. Other taps are
                    // silently ignored because the hub doesn't
                    // currently expose any `onOpen` for them.
                    onOpenTask = { task ->
                        if (task == com.photonne.app.ui.admin.AdminRunTask.DetectDuplicates) {
                            appState.moreSubscreen = MoreSubscreen.AdminSystemDuplicates
                        }
                    },
                    // A backfill skips assets that used up their
                    // retries, so a row whose queue is full of them has
                    // no button left to press. The registry is the only
                    // place they can be retried or suppressed.
                    onOpenFailures = { type ->
                        appState.adminEnrichmentInitialType = type
                        appState.adminEnrichmentReturnTo = MoreSubscreen.AdminSystemRunTasks
                        appState.moreSubscreen = MoreSubscreen.AdminSystemEnrichmentFailures
                    },
                )
            }
            MoreSubscreen.AdminSystemDuplicates ->
                com.photonne.app.ui.admin.AdminDuplicatesScreen(
                    title = stringResource(Res.string.admin_system_duplicates),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSystemRunTasks },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminDuplicatesViewModel
                )
            MoreSubscreen.AdminSystemEnrichmentFailures -> {
                val vm: com.photonne.app.ui.admin.AdminEnrichmentFailuresViewModel =
                    koinViewModel()
                com.photonne.app.ui.admin.AdminEnrichmentFailuresScreen(
                    title = stringResource(Res.string.admin_system_enrichment_failures),
                    initialType = appState.adminEnrichmentInitialType,
                    onBack = { appState.moreSubscreen = appState.adminEnrichmentReturnTo },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = vm,
                    onOpenAsset = { failure ->
                        appState.assetDetail = AssetDetailContext(
                            items = listOf(failure.toSyntheticTimelineItem()),
                            startIndex = 0,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = { id, isFav ->
                                timelineViewModel.setFavorite(id, isFav)
                            }
                        )
                    }
                )
            }
            MoreSubscreen.AdminSystemBackup ->
                com.photonne.app.ui.admin.AdminBackupScreen(
                    title = stringResource(Res.string.admin_system_backup),
                    onBack = { appState.moreSubscreen = MoreSubscreen.AdminSystemHub },
                    onChromeVisibleChange = { appState.subscreenChromeVisible = it },
                    viewModel = adminBackupViewModel
                )
            else -> Unit
        }
    }
}
