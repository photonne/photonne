package com.photonne.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import com.photonne.app.ui.image.buildPhotonneImageLoader
import com.photonne.app.ui.login.LoginScreen
import com.photonne.app.ui.main.AlbumDetailTopBar
import com.photonne.app.ui.main.AlbumsListTopBar
import com.photonne.app.ui.main.MainScaffold
import com.photonne.app.ui.main.MainTab
import com.photonne.app.ui.main.MoreScreen
import com.photonne.app.ui.main.MoreTopBar
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
    val assetId: String,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
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
    val albumSharesViewModel: AlbumSharesViewModel = koinViewModel()
    val albumPermissionsViewModel: AlbumPermissionsViewModel = koinViewModel()
    val timelineState by timelineViewModel.state.collectAsState()
    val albumsState by albumsViewModel.state.collectAsState()
    val albumDetailState by albumDetailViewModel.state.collectAsState()
    val albumSharesState by albumSharesViewModel.state.collectAsState()
    val albumPermissionsState by albumPermissionsViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(MainTab.Timeline) }
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
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

    val onLogout: () -> Unit = { authRepository.logout() }
    val albumBack: () -> Unit = { selectedAlbum = null }

    val topBar: @Composable () -> Unit = {
        when {
            selectedTab == MainTab.Albums && selectedAlbum != null -> AlbumDetailTopBar(
                title = albumDetailState.albumName ?: selectedAlbum!!.name,
                subtitle = albumDetailState.items.size.takeIf { it > 0 }?.let { "$it elementos" },
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
            selectedTab == MainTab.More -> MoreTopBar(user = user.user, onLogout = onLogout)
            else -> TimelineTopBar(
                user = user.user,
                onRefresh = timelineViewModel::refresh,
                onLogout = onLogout
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MainScaffold(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                if (tab == MainTab.Albums && selectedTab == MainTab.Albums) selectedAlbum = null
                selectedTab = tab
            },
            topBar = topBar
        ) {
            when (selectedTab) {
                MainTab.Timeline -> TimelineScreen(
                    state = timelineState,
                    onItemClick = { index ->
                        assetDetail = AssetDetailContext(
                            items = timelineState.items,
                            startIndex = index,
                            source = AssetDetailContext.Source.Timeline,
                            hasMore = timelineState.hasMore,
                            onLoadMore = timelineViewModel::loadMore,
                            onFavoriteChanged = timelineViewModel::setFavorite
                        )
                    },
                    onLoadMore = timelineViewModel::loadMore
                )
                MainTab.Albums -> {
                    val openedAlbum = selectedAlbum
                    if (openedAlbum == null) {
                        AlbumsListScreen(onAlbumClick = { album -> selectedAlbum = album })
                    } else {
                        AlbumDetailScreen(
                            albumId = openedAlbum.id,
                            albumName = openedAlbum.name,
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
                            viewModel = albumDetailViewModel
                        )
                    }
                }
                MainTab.More -> MoreScreen(user = user.user, onLogout = onLogout)
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
                onAddToAlbum = { assetId -> addToAlbum = AddToAlbumState(assetId = assetId) }
            )
        }
    }

    if (showCreateAlbum) {
        AlbumFormDialog(
            title = "New album",
            confirmLabel = "Create",
            isSubmitting = albumsState.isMutating,
            errorMessage = albumsState.errorMessage,
            onDismiss = {
                showCreateAlbum = false
                albumsViewModel.clearError()
            },
            onConfirm = { name, description ->
                albumsViewModel.create(name, description) { newAlbum ->
                    showCreateAlbum = false
                    selectedTab = MainTab.Albums
                    selectedAlbum = newAlbum
                }
            }
        )
    }

    val openedAlbum = selectedAlbum
    if (showEditAlbum && openedAlbum != null) {
        AlbumFormDialog(
            title = "Edit album",
            confirmLabel = "Save",
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
                    runCatching { albumsRepository.addAsset(album.id, addToAlbumState.assetId) }
                        .onSuccess {
                            albumsViewModel.applyAssetAdded(album.id)
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
}
