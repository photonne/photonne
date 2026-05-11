package com.photonne.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.compose.setSingletonImageLoaderFactory
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_save
import com.photonne.app.resources.album_action_edit
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.archive_title
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
import com.photonne.app.resources.favorites_title
import com.photonne.app.resources.people_title
import com.photonne.app.resources.people_unnamed
import com.photonne.app.resources.map_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.upload_subtitle_pending
import com.photonne.app.resources.upload_title
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.album.AddToAlbumDialog
import com.photonne.app.ui.album.AlbumDetailScreen
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumFormDialog
import com.photonne.app.ui.album.AlbumPermissionsViewModel
import com.photonne.app.ui.album.AlbumSharesViewModel
import com.photonne.app.ui.album.AlbumsListScreen
import com.photonne.app.ui.album.AlbumsViewModel
import com.photonne.app.ui.album.CreateShareDialog
import com.photonne.app.ui.album.DeleteAlbumDialog
import com.photonne.app.ui.album.InviteMemberDialog
import com.photonne.app.ui.album.LeaveAlbumDialog
import com.photonne.app.ui.album.ManagePermissionsDialog
import com.photonne.app.ui.album.ManageSharesDialog
import com.photonne.app.ui.asset.AssetDetailScreen
import com.photonne.app.ui.folder.DeleteFolderDialog
import com.photonne.app.ui.folder.FolderFormDialog
import com.photonne.app.ui.folder.FolderPermissionsViewModel
import com.photonne.app.ui.folder.FoldersViewModel
import com.photonne.app.ui.folder.InviteFolderMemberDialog
import com.photonne.app.ui.folder.ManageFolderPermissionsDialog
import com.photonne.app.ui.image.buildPhotonneImageLoader
import com.photonne.app.ui.login.LoginScreen
import com.photonne.app.ui.main.AlbumDetailTopBar
import com.photonne.app.ui.main.AlbumsListTopBar
import com.photonne.app.ui.main.FolderDetailTopBar
import com.photonne.app.ui.main.FoldersListTopBar
import com.photonne.app.ui.main.MainScaffold
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.main.MoreScreen
import com.photonne.app.ui.main.MoreTopBar
import com.photonne.app.ui.main.SelectionTopBar
import com.photonne.app.ui.main.TimelineTopBar
import com.photonne.app.ui.theme.PhotonneTheme
import com.photonne.app.ui.timeline.TimelineScreen
import com.photonne.app.ui.timeline.TimelineViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private data class AssetDetailContext(
    val items: List<TimelineItem>,
    val startIndex: Int,
    val source: Source,
    val hasMore: Boolean,
    val onLoadMore: () -> Unit,
    val onFavoriteChanged: (assetId: String, isFavorite: Boolean) -> Unit
) {
    enum class Source { Timeline, Album }
}

private data class AddToAlbumState(
    val asset: TimelineItem,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

private enum class MoreSubscreen { Upload, Favorites, People, Map, Archived, Trash }

/** Build a thin TimelineItem out of a map point so the asset viewer
 * can be seeded without an extra fetch — it re-queries AssetDetail
 * on display, so most fields can stay blank. */
private fun com.photonne.app.data.models.MapPoint.toSyntheticTimelineItem():
    com.photonne.app.data.models.TimelineItem =
    com.photonne.app.data.models.TimelineItem(
        id = id,
        fileName = "",
        fullPath = "",
        fileSize = 0L,
        fileCreatedAt = date,
        fileModifiedAt = date,
        extension = "",
        scannedAt = date,
        type = "Image",
        hasThumbnails = hasThumbnail
    )

@Composable
fun App() {
    val httpClient: HttpClient = koinInject()
    setSingletonImageLoaderFactory { context ->
        buildPhotonneImageLoader(context, httpClient)
    }

    PhotonneTheme {
        val authState: AuthStateHolder = koinInject()
        val state by authState.state.collectAsState()
        when (val current = state) {
            is AuthState.Authenticated -> AuthenticatedApp(user = current)
            AuthState.Unauthenticated, AuthState.Unknown -> LoginScreen()
        }
    }
}

@Composable
private fun AuthenticatedApp(user: AuthState.Authenticated) {
    val authRepository: AuthRepository = koinInject()
    val albumsRepository: AlbumsRepository = koinInject()
    val timelineViewModel: TimelineViewModel = koinViewModel()
    val albumsViewModel: AlbumsViewModel = koinViewModel()
    val albumDetailViewModel: AlbumDetailViewModel = koinViewModel()
    val searchViewModel: com.photonne.app.ui.search.SearchViewModel = koinViewModel()
    val foldersViewModel: FoldersViewModel = koinViewModel()
    val folderDetailViewModel: com.photonne.app.ui.folder.FolderDetailViewModel = koinViewModel()
    val folderPermissionsViewModel: FolderPermissionsViewModel = koinViewModel()
    val albumSharesViewModel: AlbumSharesViewModel = koinViewModel()
    val albumPermissionsViewModel: AlbumPermissionsViewModel = koinViewModel()
    val archivedViewModel: com.photonne.app.ui.library.ArchivedViewModel = koinViewModel()
    val trashViewModel: com.photonne.app.ui.library.TrashViewModel = koinViewModel()
    val favoritesViewModel: com.photonne.app.ui.library.FavoritesViewModel = koinViewModel()
    val uploadViewModel: com.photonne.app.ui.upload.UploadViewModel = koinViewModel()
    val mapViewModel: com.photonne.app.ui.map.MapViewModel = koinViewModel()
    val peopleViewModel: com.photonne.app.ui.people.PeopleViewModel = koinViewModel()
    val personDetailViewModel: com.photonne.app.ui.people.PersonDetailViewModel = koinViewModel()
    val timelineState by timelineViewModel.state.collectAsState()
    val albumsState by albumsViewModel.state.collectAsState()
    val albumDetailState by albumDetailViewModel.state.collectAsState()
    val searchState by searchViewModel.state.collectAsState()
    val foldersState by foldersViewModel.state.collectAsState()
    val folderDetailState by folderDetailViewModel.state.collectAsState()
    val folderPermissionsState by folderPermissionsViewModel.state.collectAsState()
    val albumSharesState by albumSharesViewModel.state.collectAsState()
    val albumPermissionsState by albumPermissionsViewModel.state.collectAsState()
    val archivedState by archivedViewModel.state.collectAsState()
    val trashState by trashViewModel.state.collectAsState()
    val favoritesState by favoritesViewModel.state.collectAsState()
    val peopleState by peopleViewModel.state.collectAsState()
    val personDetailState by personDetailViewModel.state.collectAsState()
    val uploadState by uploadViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(MainTab.Timeline) }
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var selectedFolder by remember {
        mutableStateOf<com.photonne.app.data.models.FolderSummary?>(null)
    }
    var assetDetail by remember { mutableStateOf<AssetDetailContext?>(null) }
    var showCreateAlbum by remember { mutableStateOf(false) }
    var showEditAlbum by remember { mutableStateOf(false) }
    var showDeleteAlbum by remember { mutableStateOf(false) }
    var showLeaveAlbum by remember { mutableStateOf(false) }
    var showShares by remember { mutableStateOf(false) }
    var showCreateShare by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showInviteMember by remember { mutableStateOf(false) }
    var addToAlbum by remember { mutableStateOf<AddToAlbumState?>(null) }
    var bulkAddToAlbum by remember { mutableStateOf<Boolean>(false) }
    var bulkAddToAlbumFromSearch by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromMap by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromFavorites by remember { mutableStateOf(false) }
    var bulkAddToAlbumFromPeople by remember { mutableStateOf(false) }
    var selectedPerson by remember {
        mutableStateOf<com.photonne.app.data.models.Person?>(null)
    }
    var showRenamePerson by remember { mutableStateOf(false) }
    var showJumpToDate by remember { mutableStateOf(false) }
    var pendingJumpDate by remember { mutableStateOf<kotlinx.datetime.Instant?>(null) }
    var pendingBulkAddOnCreate by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var showEditFolder by remember { mutableStateOf(false) }
    var showDeleteFolder by remember { mutableStateOf(false) }
    var showFolderMembers by remember { mutableStateOf(false) }
    var showInviteFolderMember by remember { mutableStateOf(false) }
    var showMoveFolder by remember { mutableStateOf(false) }
    var showMoveSelectedAssets by remember { mutableStateOf(false) }
    var showSearchFilters by remember { mutableStateOf(false) }
    var moreSubscreen by remember { mutableStateOf<MoreSubscreen?>(null) }
    var showUnarchiveAll by remember { mutableStateOf(false) }
    var showRestoreAllTrash by remember { mutableStateOf(false) }
    var showEmptyTrash by remember { mutableStateOf(false) }
    var showPurgeSelected by remember { mutableStateOf(false) }

    val onLogout: () -> Unit = { authRepository.logout() }
    val albumBack: () -> Unit = { selectedAlbum = null }

    // Mirror the share link count for the currently opened album back into
    // the albums list so the public-link badge stays in sync after
    // create/revoke without a full refresh.
    val openedAlbumId = selectedAlbum?.id
    val sharedAlbumId = albumSharesState.albumId
    val activeLinks = albumSharesState.links.isNotEmpty()
    LaunchedEffect(openedAlbumId, sharedAlbumId, activeLinks) {
        if (openedAlbumId != null && openedAlbumId == sharedAlbumId) {
            albumsViewModel.applyShareLinkChanged(openedAlbumId, activeLinks)
            selectedAlbum = selectedAlbum?.copy(hasActiveShareLink = activeLinks)
        }
    }

    val topBar: @Composable () -> Unit = {
        when {
            selectedTab == MainTab.Timeline && timelineState.isSelectionActive -> SelectionTopBar(
                selectedCount = timelineState.selection.size,
                isMutating = timelineState.isBulkMutating,
                onClose = timelineViewModel::clearSelection,
                onAddToAlbum = { bulkAddToAlbum = true },
                onArchive = timelineViewModel::bulkArchive,
                onTrash = timelineViewModel::bulkTrash
            )
            selectedTab == MainTab.Albums && selectedAlbum != null -> AlbumDetailTopBar(
                title = albumDetailState.albumName ?: selectedAlbum!!.name,
                subtitle = albumDetailState.items.size.takeIf { it > 0 }?.let {
                    stringResource(Res.string.albums_count_format, it)
                },
                canEdit = selectedAlbum?.canWrite == true || selectedAlbum?.isOwner == true,
                canDelete = selectedAlbum?.isOwner == true,
                canShare = selectedAlbum?.canWrite == true || selectedAlbum?.isOwner == true,
                canManageMembers = selectedAlbum?.isOwner == true ||
                    selectedAlbum?.canManagePermissions == true,
                canLeave = selectedAlbum?.isOwner == false,
                onBack = albumBack,
                onEdit = { showEditAlbum = true },
                onDelete = { showDeleteAlbum = true },
                onShare = {
                    selectedAlbum?.let { albumSharesViewModel.open(it.id) }
                    showShares = true
                },
                onManageMembers = {
                    selectedAlbum?.let { albumPermissionsViewModel.open(it.id) }
                    showMembers = true
                },
                onLeave = { showLeaveAlbum = true },
                user = user.user,
                onLogout = onLogout
            )
            selectedTab == MainTab.Albums -> AlbumsListTopBar(
                user = user.user,
                onCreateAlbum = { showCreateAlbum = true },
                onLogout = onLogout
            )
            selectedTab == MainTab.Folders && selectedFolder != null &&
                folderDetailState.isSelectionActive ->
                com.photonne.app.ui.main.FolderSelectionTopBar(
                    selectedCount = folderDetailState.selection.size,
                    isMutating = folderDetailState.isBulkMutating,
                    onClose = folderDetailViewModel::clearSelection,
                    onMoveToFolder = { showMoveSelectedAssets = true }
                )
            selectedTab == MainTab.Folders && selectedFolder != null -> {
                val folder = selectedFolder!!
                FolderDetailTopBar(
                    title = (folderDetailState.folderName ?: folder.name).ifBlank { folder.path },
                    subtitle = stringResource(
                        Res.string.albums_count_format,
                        folder.assetCount
                    ),
                    canEdit = folder.isOwner,
                    canDelete = folder.isOwner,
                    canManageMembers = folder.isOwner,
                    canMove = folder.isOwner,
                    onBack = { selectedFolder = null },
                    onEdit = { showEditFolder = true },
                    onMove = { showMoveFolder = true },
                    onDelete = { showDeleteFolder = true },
                    onManageMembers = {
                        folderPermissionsViewModel.open(folder.id)
                        showFolderMembers = true
                    },
                    user = user.user,
                    onLogout = onLogout
                )
            }
            selectedTab == MainTab.Folders ->
                FoldersListTopBar(
                    user = user.user,
                    onCreateFolder = { showCreateFolder = true },
                    onLogout = onLogout
                )
            selectedTab == MainTab.Search && searchState.isSelectionActive -> SelectionTopBar(
                selectedCount = searchState.selection.size,
                isMutating = searchState.isBulkMutating,
                onClose = searchViewModel::clearSelection,
                onAddToAlbum = { bulkAddToAlbumFromSearch = true },
                onArchive = searchViewModel::bulkArchive,
                onTrash = searchViewModel::bulkTrash
            )
            selectedTab == MainTab.Search ->
                com.photonne.app.ui.main.SearchTopBar(user = user.user, onLogout = onLogout)
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.Upload ->
                com.photonne.app.ui.main.UploadTopBar(
                    title = stringResource(Res.string.upload_title),
                    subtitle = if (uploadState.pendingCount > 0)
                        stringResource(
                            Res.string.upload_subtitle_pending,
                            uploadState.pendingCount
                        )
                    else null,
                    onBack = { moreSubscreen = null },
                    user = user.user,
                    onLogout = onLogout
                )
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.Map ->
                com.photonne.app.ui.main.MapTopBar(
                    title = stringResource(Res.string.map_title),
                    onBack = { moreSubscreen = null },
                    onRefresh = mapViewModel::refresh,
                    user = user.user,
                    onLogout = onLogout
                )
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.People &&
                selectedPerson != null && personDetailState.isSelectionActive ->
                com.photonne.app.ui.main.PersonDetailSelectionTopBar(
                    selectedCount = personDetailState.selection.size,
                    isMutating = personDetailState.isBulkMutating,
                    onClose = personDetailViewModel::clearSelection,
                    onAddToAlbum = { bulkAddToAlbumFromPeople = true },
                    onArchive = personDetailViewModel::bulkArchive,
                    onTrash = personDetailViewModel::bulkTrash
                )
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.People &&
                selectedPerson != null -> {
                val person = selectedPerson!!
                val resolvedName = personDetailState.personName ?: person.name
                com.photonne.app.ui.main.PersonDetailTopBar(
                    title = resolvedName?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.people_unnamed),
                    subtitle = if (personDetailState.total > 0)
                        stringResource(Res.string.albums_count_format, personDetailState.total)
                    else null,
                    isHidden = person.isHidden,
                    onBack = { selectedPerson = null },
                    onRename = { showRenamePerson = true },
                    onToggleHidden = {
                        if (person.isHidden) {
                            peopleViewModel.unhide(person.id) {
                                selectedPerson = person.copy(isHidden = false)
                            }
                        } else {
                            peopleViewModel.hide(person.id) {
                                selectedPerson = null
                            }
                        }
                    },
                    user = user.user,
                    onLogout = onLogout
                )
            }
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.People ->
                com.photonne.app.ui.main.PeopleTopBar(
                    title = stringResource(Res.string.people_title),
                    onBack = { moreSubscreen = null },
                    onRefresh = peopleViewModel::refresh,
                    showHidden = peopleState.showHidden,
                    onToggleHidden = peopleViewModel::toggleShowHidden,
                    user = user.user,
                    onLogout = onLogout
                )
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.Favorites &&
                favoritesState.isSelectionActive ->
                com.photonne.app.ui.main.FavoritesSelectionTopBar(
                    selectedCount = favoritesState.selection.size,
                    isMutating = favoritesState.isBulkMutating,
                    onClose = favoritesViewModel::clearSelection,
                    onAddToAlbum = { bulkAddToAlbumFromFavorites = true },
                    onArchive = favoritesViewModel::bulkArchive,
                    onTrash = favoritesViewModel::bulkTrash
                )
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.Favorites -> {
                val count = favoritesState.items.size
                com.photonne.app.ui.main.FavoritesTopBar(
                    title = stringResource(Res.string.favorites_title),
                    subtitle = if (count > 0)
                        stringResource(Res.string.albums_count_format, count) else null,
                    onBack = { moreSubscreen = null },
                    onRefresh = favoritesViewModel::refresh,
                    user = user.user,
                    onLogout = onLogout
                )
            }
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.Archived &&
                archivedState.isSelectionActive ->
                com.photonne.app.ui.main.ArchivedSelectionTopBar(
                    selectedCount = archivedState.selection.size,
                    isMutating = archivedState.isBulkMutating,
                    onClose = archivedViewModel::clearSelection,
                    onUnarchive = { archivedViewModel.bulkUnarchive() }
                )
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.Archived -> {
                val count = archivedState.items.size
                com.photonne.app.ui.main.ArchivedTopBar(
                    title = stringResource(Res.string.archive_title),
                    subtitle = if (count > 0)
                        stringResource(Res.string.albums_count_format, count) else null,
                    canUnarchiveAll = count > 0,
                    onBack = { moreSubscreen = null },
                    onRefresh = archivedViewModel::refresh,
                    onUnarchiveAll = { showUnarchiveAll = true },
                    user = user.user,
                    onLogout = onLogout
                )
            }
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.Trash &&
                trashState.isSelectionActive ->
                com.photonne.app.ui.main.TrashSelectionTopBar(
                    selectedCount = trashState.selection.size,
                    isMutating = trashState.isBulkMutating,
                    onClose = trashViewModel::clearSelection,
                    onRestore = { trashViewModel.bulkRestore() },
                    onPurge = { showPurgeSelected = true }
                )
            selectedTab == MainTab.More && moreSubscreen == MoreSubscreen.Trash -> {
                val count = trashState.items.size
                com.photonne.app.ui.main.TrashTopBar(
                    title = stringResource(Res.string.trash_title),
                    subtitle = if (count > 0)
                        stringResource(Res.string.albums_count_format, count) else null,
                    canActOnAll = count > 0,
                    onBack = { moreSubscreen = null },
                    onRefresh = trashViewModel::refresh,
                    onRestoreAll = { showRestoreAllTrash = true },
                    onEmptyTrash = { showEmptyTrash = true },
                    user = user.user,
                    onLogout = onLogout
                )
            }
            selectedTab == MainTab.More -> MoreTopBar(user = user.user, onLogout = onLogout)
            else -> TimelineTopBar(
                user = user.user,
                onRefresh = timelineViewModel::refresh,
                onJumpToDate = { showJumpToDate = true },
                onUpload = {
                    selectedTab = MainTab.More
                    moreSubscreen = MoreSubscreen.Upload
                },
                onLogout = onLogout
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainScaffold(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                if (tab == MainTab.Albums && selectedTab == MainTab.Albums) selectedAlbum = null
                if (tab == MainTab.Folders && selectedTab == MainTab.Folders) selectedFolder = null
                if (tab == MainTab.More && selectedTab == MainTab.More) {
                    moreSubscreen = null
                    selectedPerson = null
                }
                selectedTab = tab
            },
            topBar = topBar
        ) {
            when (selectedTab) {
                MainTab.Timeline -> TimelineScreen(
                    state = timelineState,
                    onItemClick = { index ->
                        if (timelineState.isSelectionActive) {
                            timelineState.items.getOrNull(index)?.let {
                                timelineViewModel.toggleSelection(it.id)
                            }
                        } else {
                            assetDetail = AssetDetailContext(
                                items = timelineState.items,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = timelineState.hasMore,
                                onLoadMore = timelineViewModel::loadMore,
                                onFavoriteChanged = timelineViewModel::setFavorite
                            )
                        }
                    },
                    onLoadMore = timelineViewModel::loadMore,
                    onRefresh = timelineViewModel::refresh,
                    onItemLongClick = { index ->
                        timelineState.items.getOrNull(index)?.let {
                            timelineViewModel.toggleSelection(it.id)
                        }
                    },
                    pendingJumpDate = pendingJumpDate,
                    onJumpHandled = { pendingJumpDate = null },
                    onMemoryClick = { items, index ->
                        assetDetail = AssetDetailContext(
                            items = items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = false,
                            onLoadMore = {},
                            onFavoriteChanged = timelineViewModel::setFavorite
                        )
                    }
                )
                MainTab.Albums -> {
                    val openedAlbum = selectedAlbum
                    if (openedAlbum == null) {
                        AlbumsListScreen(onAlbumClick = { album -> selectedAlbum = album })
                    } else {
                        val albumCanManage =
                            openedAlbum.canWrite || openedAlbum.isOwner
                        AlbumDetailScreen(
                            albumId = openedAlbum.id,
                            albumName = openedAlbum.name,
                            canManage = albumCanManage,
                            onItemClick = { index ->
                                assetDetail = AssetDetailContext(
                                    items = albumDetailState.items,
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
                            onSetAsCover = { item ->
                                albumDetailViewModel.setCover(item.id) { updated ->
                                    albumsViewModel.applyUpdate(updated)
                                    selectedAlbum = openedAlbum.copy(
                                        coverThumbnailUrl = updated.coverThumbnailUrl
                                    )
                                }
                            },
                            onRemoveFromAlbum = { item ->
                                albumDetailViewModel.removeAsset(item.id) {
                                    albumsViewModel.applyAssetRemoved(openedAlbum.id)
                                }
                            },
                            viewModel = albumDetailViewModel
                        )
                    }
                }
                MainTab.Folders -> {
                    val openedFolder = selectedFolder
                    if (openedFolder == null) {
                        com.photonne.app.ui.folder.FoldersListScreen(
                            onFolderClick = { folder -> selectedFolder = folder }
                        )
                    } else {
                        com.photonne.app.ui.folder.FolderDetailScreen(
                            folderId = openedFolder.id,
                            folderName = openedFolder.name.ifBlank { openedFolder.path },
                            parentFolderId = openedFolder.parentFolderId,
                            onItemClick = { index ->
                                if (folderDetailState.isSelectionActive) {
                                    folderDetailState.items.getOrNull(index)?.let {
                                        folderDetailViewModel.toggleSelection(it.id)
                                    }
                                } else {
                                    val folderState = folderDetailViewModel.state.value
                                    assetDetail = AssetDetailContext(
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
                                if (openedFolder.isOwner) {
                                    folderDetailState.items.getOrNull(index)?.let {
                                        folderDetailViewModel.toggleSelection(it.id)
                                    }
                                }
                            },
                            viewModel = folderDetailViewModel
                        )
                    }
                }
                MainTab.Search -> com.photonne.app.ui.search.SearchScreen(
                    viewModel = searchViewModel,
                    onOpenFilters = { showSearchFilters = true },
                    onItemClick = { index ->
                        if (searchState.isSelectionActive) {
                            searchState.results.getOrNull(index)?.let {
                                searchViewModel.toggleSelection(it.id)
                            }
                        } else {
                            assetDetail = AssetDetailContext(
                                items = searchState.results,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
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
                MainTab.More -> when (moreSubscreen) {
                    null -> MoreScreen(
                        user = user.user,
                        onLogout = onLogout,
                        onOpenUpload = { moreSubscreen = MoreSubscreen.Upload },
                        onOpenMap = { moreSubscreen = MoreSubscreen.Map },
                        onOpenFavorites = { moreSubscreen = MoreSubscreen.Favorites },
                        onOpenPeople = { moreSubscreen = MoreSubscreen.People },
                        onOpenArchived = { moreSubscreen = MoreSubscreen.Archived },
                        onOpenTrash = { moreSubscreen = MoreSubscreen.Trash }
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
                        onDismissPickerError = uploadViewModel::clearPickerError
                    )
                    MoreSubscreen.Map -> com.photonne.app.ui.map.MapScreen(
                        viewModel = mapViewModel,
                        onPointOpen = { point ->
                            // Single-marker tap → open the asset viewer
                            // seeded with that one item. The viewer
                            // re-fetches asset detail on display, so a
                            // synthetic TimelineItem is enough.
                            assetDetail = AssetDetailContext(
                                items = listOf(point.toSyntheticTimelineItem()),
                                startIndex = 0,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = { id, isFav ->
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                        },
                        onClusterPhotoOpen = { sheetPoints, index ->
                            // Bottom-sheet thumbnail tap → open the
                            // viewer seeded with the whole cluster so
                            // the user can swipe through it.
                            val items = sheetPoints.map { it.toSyntheticTimelineItem() }
                            assetDetail = AssetDetailContext(
                                items = items,
                                startIndex = index,
                                source = AssetDetailContext.Source.Timeline,
                                hasMore = false,
                                onLoadMore = {},
                                onFavoriteChanged = { id, isFav ->
                                    timelineViewModel.setFavorite(id, isFav)
                                }
                            )
                            mapViewModel.closeClusterSheet()
                        },
                        onBulkAddToAlbum = { bulkAddToAlbumFromMap = true }
                    )
                    MoreSubscreen.People -> {
                        val person = selectedPerson
                        if (person == null) {
                            com.photonne.app.ui.people.PeopleScreen(
                                state = peopleState,
                                onPersonClick = { picked ->
                                    selectedPerson = picked
                                    personDetailViewModel.open(picked.id, picked.name)
                                },
                                onLoadMore = peopleViewModel::loadMore,
                                onRefresh = peopleViewModel::ensureLoaded
                            )
                        } else {
                            com.photonne.app.ui.people.PersonDetailScreen(
                                state = personDetailState,
                                onItemClick = { index ->
                                    if (personDetailState.isSelectionActive) {
                                        personDetailState.items.getOrNull(index)?.let {
                                            personDetailViewModel.toggleSelection(it.id)
                                        }
                                    } else {
                                        assetDetail = AssetDetailContext(
                                            items = personDetailState.items,
                                            startIndex = index,
                                            source = AssetDetailContext.Source.Timeline,
                                            hasMore = personDetailState.hasMore,
                                            onLoadMore = personDetailViewModel::loadMore,
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
                                onLoadMore = personDetailViewModel::loadMore
                            )
                        }
                    }
                    MoreSubscreen.Favorites -> com.photonne.app.ui.library.FavoritesScreen(
                        state = favoritesState,
                        onItemClick = { index ->
                            if (favoritesState.isSelectionActive) {
                                favoritesState.items.getOrNull(index)?.let {
                                    favoritesViewModel.toggleSelection(it.id)
                                }
                            } else {
                                assetDetail = AssetDetailContext(
                                    items = favoritesState.items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = favoritesState.hasMore,
                                    onLoadMore = favoritesViewModel::loadMore,
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
                        onRefresh = favoritesViewModel::ensureLoaded
                    )
                    MoreSubscreen.Archived -> com.photonne.app.ui.library.ArchivedScreen(
                        state = archivedState,
                        onItemClick = { index ->
                            if (archivedState.isSelectionActive) {
                                archivedState.items.getOrNull(index)?.let {
                                    archivedViewModel.toggleSelection(it.id)
                                }
                            } else {
                                assetDetail = AssetDetailContext(
                                    items = archivedState.items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = archivedState.hasMore,
                                    onLoadMore = archivedViewModel::loadMore,
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
                        onRefresh = archivedViewModel::ensureLoaded
                    )
                    MoreSubscreen.Trash -> com.photonne.app.ui.library.TrashScreen(
                        state = trashState,
                        onItemClick = { index ->
                            if (trashState.isSelectionActive) {
                                trashState.items.getOrNull(index)?.let {
                                    trashViewModel.toggleSelection(it.id)
                                }
                            } else {
                                assetDetail = AssetDetailContext(
                                    items = trashState.items,
                                    startIndex = index,
                                    source = AssetDetailContext.Source.Timeline,
                                    hasMore = trashState.hasMore,
                                    onLoadMore = trashViewModel::loadMore,
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
                        onRefresh = trashViewModel::ensureLoaded
                    )
                }
            }
        }

        val ctx = assetDetail
        if (ctx != null && ctx.startIndex in ctx.items.indices) {
            AssetDetailScreen(
                items = ctx.items,
                startIndex = ctx.startIndex,
                hasMore = ctx.hasMore,
                onLoadMore = ctx.onLoadMore,
                onBack = { assetDetail = null },
                onFavoriteChanged = ctx.onFavoriteChanged,
                onAddToAlbum = { item -> addToAlbum = AddToAlbumState(asset = item) },
                onAssetTrashed = { id ->
                    timelineViewModel.removeItemLocal(id)
                    if (ctx.source == AssetDetailContext.Source.Album) {
                        albumDetailViewModel.applyAssetRemovedLocal(id)
                    }
                    searchViewModel.removeItem(id)
                    archivedViewModel.applyAssetRemovedLocal(id)
                    personDetailViewModel.applyAssetRemovedLocal(id)
                    assetDetail = null
                },
                onAssetArchived = { id ->
                    timelineViewModel.removeItemLocal(id)
                    if (ctx.source == AssetDetailContext.Source.Album) {
                        albumDetailViewModel.applyAssetRemovedLocal(id)
                    }
                    searchViewModel.removeItem(id)
                    trashViewModel.applyAssetRemovedLocal(id)
                    personDetailViewModel.applyAssetRemovedLocal(id)
                    assetDetail = null
                }
            )
        }
    }

    if (showJumpToDate) {
        com.photonne.app.ui.timeline.JumpToDateDialog(
            onDismiss = { showJumpToDate = false },
            onConfirm = { date ->
                showJumpToDate = false
                pendingJumpDate = date
            }
        )
    }

    if (showCreateAlbum) {
        AlbumFormDialog(
            title = stringResource(Res.string.album_action_new),
            confirmLabel = stringResource(Res.string.action_create),
            isSubmitting = albumsState.isMutating || timelineState.isBulkMutating,
            errorMessage = albumsState.errorMessage,
            onDismiss = {
                showCreateAlbum = false
                pendingBulkAddOnCreate = false
                albumsViewModel.clearError()
            },
            onConfirm = { name, description ->
                albumsViewModel.create(name, description) { newAlbum ->
                    if (pendingBulkAddOnCreate) {
                        pendingBulkAddOnCreate = false
                        timelineViewModel.bulkAddToAlbum(newAlbum.id) { added ->
                            albumsViewModel.applyAssetsAdded(newAlbum.id, added.size)
                            showCreateAlbum = false
                            selectedTab = MainTab.Albums
                            selectedAlbum = newAlbum
                        }
                    } else {
                        showCreateAlbum = false
                        selectedTab = MainTab.Albums
                        selectedAlbum = newAlbum
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
            errorMessage = albumDetailState.errorMessage,
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
    }

    if (showDeleteAlbum && openedAlbum != null) {
        DeleteAlbumDialog(
            albumName = albumDetailState.albumName ?: openedAlbum.name,
            isSubmitting = albumDetailState.isMutating,
            errorMessage = albumDetailState.errorMessage,
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
    }

    if (showLeaveAlbum && openedAlbum != null) {
        LeaveAlbumDialog(
            albumName = albumDetailState.albumName ?: openedAlbum.name,
            isSubmitting = albumDetailState.isMutating,
            errorMessage = albumDetailState.errorMessage,
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
    }

    if (showShares && openedAlbum != null) {
        ManageSharesDialog(
            state = albumSharesState,
            onDismiss = {
                showShares = false
                albumSharesViewModel.clearError()
            },
            onCreate = { showCreateShare = true },
            onRevoke = { token -> albumSharesViewModel.revoke(token) }
        )
    }

    if (showMembers && openedAlbum != null) {
        ManagePermissionsDialog(
            state = albumPermissionsState,
            onDismiss = {
                showMembers = false
                albumPermissionsViewModel.clearError()
            },
            onInvite = { showInviteMember = true },
            onChangeRole = { member, role -> albumPermissionsViewModel.changeRole(member, role) },
            onRevoke = { member -> albumPermissionsViewModel.revoke(member) }
        )
    }

    if (showInviteMember && openedAlbum != null) {
        InviteMemberDialog(
            candidates = albumPermissionsState.invitableUsers,
            isSubmitting = albumPermissionsState.isMutating,
            errorMessage = albumPermissionsState.errorMessage,
            onDismiss = {
                showInviteMember = false
                albumPermissionsViewModel.clearError()
            },
            onInvite = { selectedUser, role ->
                albumPermissionsViewModel.grant(selectedUser, role)
                showInviteMember = false
            }
        )
    }

    if (showCreateShare && openedAlbum != null) {
        CreateShareDialog(
            isSubmitting = albumSharesState.isMutating,
            errorMessage = albumSharesState.errorMessage,
            onDismiss = {
                showCreateShare = false
                albumSharesViewModel.clearError()
            },
            onConfirm = { password, allowDownload, maxViews ->
                albumSharesViewModel.createLink(
                    expiresAt = null,
                    password = password,
                    allowDownload = allowDownload,
                    maxViews = maxViews
                )
                showCreateShare = false
            }
        )
    }

    if (bulkAddToAlbum) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = timelineState.isBulkMutating,
            errorMessage = timelineState.errorMessage,
            onCreateNew = {
                bulkAddToAlbum = false
                pendingBulkAddOnCreate = true
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                timelineViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbum = false
            },
            onDismiss = { bulkAddToAlbum = false }
        )
    }

    if (showCreateFolder) {
        FolderFormDialog(
            title = stringResource(Res.string.folder_action_new),
            confirmLabel = stringResource(Res.string.action_create),
            isSubmitting = foldersState.isMutating,
            errorMessage = foldersState.errorMessage,
            onDismiss = {
                showCreateFolder = false
                foldersViewModel.clearError()
            },
            onConfirm = { name ->
                foldersViewModel.create(name, parentFolderId = null) {
                    showCreateFolder = false
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
            errorMessage = folderDetailState.errorMessage,
            onDismiss = {
                showEditFolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { name ->
                folderDetailViewModel.rename(name) { updated ->
                    showEditFolder = false
                    selectedFolder = openedFolder.copy(name = updated.name, path = updated.path)
                    foldersViewModel.applyUpdate(updated)
                }
            }
        )
    }

    if (showDeleteFolder && openedFolder != null) {
        DeleteFolderDialog(
            folderName = folderDetailState.folderName ?: openedFolder.name.ifBlank { openedFolder.path },
            isSubmitting = folderDetailState.isMutating,
            errorMessage = folderDetailState.errorMessage,
            onDismiss = {
                showDeleteFolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = {
                folderDetailViewModel.delete { folderId ->
                    showDeleteFolder = false
                    foldersViewModel.applyDelete(folderId)
                    selectedFolder = null
                }
            }
        )
    }

    if (showFolderMembers && openedFolder != null) {
        ManageFolderPermissionsDialog(
            state = folderPermissionsState,
            onDismiss = {
                showFolderMembers = false
                folderPermissionsViewModel.clearError()
            },
            onInvite = { showInviteFolderMember = true },
            onChangeRole = { member, role -> folderPermissionsViewModel.changeRole(member, role) },
            onRevoke = { member -> folderPermissionsViewModel.revoke(member) }
        )
    }

    if (showInviteFolderMember && openedFolder != null) {
        InviteFolderMemberDialog(
            candidates = folderPermissionsState.invitableUsers,
            isSubmitting = folderPermissionsState.isMutating,
            errorMessage = folderPermissionsState.errorMessage,
            onDismiss = {
                showInviteFolderMember = false
                folderPermissionsViewModel.clearError()
            },
            onInvite = { selectedUser, role ->
                folderPermissionsViewModel.grant(selectedUser, role)
                showInviteFolderMember = false
            }
        )
    }

    if (showMoveFolder && openedFolder != null) {
        com.photonne.app.ui.folder.FolderPickerDialog(
            title = stringResource(Res.string.folder_move_title),
            folders = foldersState.folders,
            isSubmitting = folderDetailState.isMutating,
            errorMessage = folderDetailState.errorMessage,
            excludeFolderId = openedFolder.id,
            includeRoot = true,
            initialSelectionId = openedFolder.parentFolderId,
            onDismiss = {
                showMoveFolder = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { targetParentId ->
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

    if (showSearchFilters) {
        com.photonne.app.ui.search.SearchFiltersSheet(
            state = searchState,
            onDismiss = { showSearchFilters = false },
            onDateRangeChange = searchViewModel::setDateRange,
            onOcrChange = searchViewModel::setOcrText,
            onToggleObject = searchViewModel::toggleObjectLabel,
            onToggleScene = searchViewModel::toggleSceneLabel,
            onTogglePerson = searchViewModel::togglePerson,
            onClearAll = searchViewModel::clearAll
        )
    }

    if (bulkAddToAlbumFromSearch) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = searchState.isBulkMutating,
            errorMessage = searchState.errorMessage,
            onCreateNew = {
                bulkAddToAlbumFromSearch = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                searchViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbumFromSearch = false
            },
            onDismiss = { bulkAddToAlbumFromSearch = false }
        )
    }

    if (showMoveSelectedAssets && openedFolder != null) {
        com.photonne.app.ui.folder.FolderPickerDialog(
            title = stringResource(Res.string.folder_move_assets_title),
            folders = foldersState.folders,
            isSubmitting = folderDetailState.isBulkMutating,
            errorMessage = folderDetailState.errorMessage,
            excludeFolderId = openedFolder.id,
            includeRoot = false,
            onDismiss = {
                showMoveSelectedAssets = false
                folderDetailViewModel.clearError()
            },
            onConfirm = { targetFolderId ->
                if (targetFolderId != null) {
                    folderDetailViewModel.moveSelectedAssets(targetFolderId) { movedIds ->
                        showMoveSelectedAssets = false
                        val moved = movedIds.size
                        if (moved > 0) {
                            selectedFolder = openedFolder.copy(
                                assetCount = (openedFolder.assetCount - moved).coerceAtLeast(0)
                            )
                        }
                    }
                }
            }
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

    if (showUnarchiveAll) {
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.archive_action_unarchive_all_title),
            message = stringResource(Res.string.archive_action_unarchive_all_message),
            confirmLabel = stringResource(Res.string.archive_action_unarchive_all),
            isDestructive = false,
            isSubmitting = archivedState.isBulkMutating,
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
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_restore_all),
            message = stringResource(Res.string.trash_dialog_restore_all_message),
            confirmLabel = stringResource(Res.string.trash_action_restore_all),
            isDestructive = false,
            isSubmitting = trashState.isBulkMutating,
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
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_empty),
            message = stringResource(Res.string.trash_dialog_empty_message),
            confirmLabel = stringResource(Res.string.trash_action_empty),
            isDestructive = true,
            isSubmitting = trashState.isBulkMutating,
            onDismiss = { showEmptyTrash = false },
            onConfirm = {
                trashViewModel.emptyTrash { showEmptyTrash = false }
            }
        )
    }

    if (showPurgeSelected) {
        val count = trashState.selection.size
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.trash_action_delete_forever),
            message = stringResource(Res.string.trash_dialog_purge_message, count),
            confirmLabel = stringResource(Res.string.trash_action_delete_forever),
            isDestructive = true,
            isSubmitting = trashState.isBulkMutating,
            onDismiss = { showPurgeSelected = false },
            onConfirm = {
                trashViewModel.bulkPurge { showPurgeSelected = false }
            }
        )
    }

    if (bulkAddToAlbumFromMap) {
        val mapState = mapViewModel.state.collectAsState().value
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = mapState.isBulkMutating,
            errorMessage = mapState.errorMessage,
            onCreateNew = {
                bulkAddToAlbumFromMap = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                mapViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbumFromMap = false
            },
            onDismiss = { bulkAddToAlbumFromMap = false }
        )
    }

    if (bulkAddToAlbumFromPeople) {
        AddToAlbumDialog(
            albums = albumsState.albums,
            isLoadingAlbums = albumsState.isLoading,
            isSubmitting = personDetailState.isBulkMutating,
            errorMessage = personDetailState.errorMessage,
            onCreateNew = {
                bulkAddToAlbumFromPeople = false
                showCreateAlbum = true
            },
            onAlbumSelected = { album ->
                personDetailViewModel.bulkAddToAlbum(album.id) { added ->
                    albumsViewModel.applyAssetsAdded(album.id, added.size)
                    albumDetailViewModel.applyAssetsAdded(album.id, added)
                }
                bulkAddToAlbumFromPeople = false
            },
            onDismiss = { bulkAddToAlbumFromPeople = false }
        )
    }

    val activePerson = selectedPerson
    if (showRenamePerson && activePerson != null) {
        com.photonne.app.ui.people.RenamePersonDialog(
            initialName = personDetailState.personName ?: activePerson.name,
            isSubmitting = peopleState.isMutating,
            errorMessage = peopleState.errorMessage,
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
}
