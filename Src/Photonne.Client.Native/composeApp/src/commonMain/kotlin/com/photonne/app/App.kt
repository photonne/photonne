package com.photonne.app

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.compose.setSingletonImageLoaderFactory
import com.photonne.app.data.auth.AuthRepository
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.album.AlbumDetailScreen
import com.photonne.app.ui.album.AlbumDetailViewModel
import com.photonne.app.ui.album.AlbumsListScreen
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
    val timelineViewModel: TimelineViewModel = koinViewModel()
    val albumDetailViewModel: AlbumDetailViewModel = koinViewModel()
    val timelineState by timelineViewModel.state.collectAsState()
    val albumDetailState by albumDetailViewModel.state.collectAsState()

    var selectedTab by remember { mutableStateOf(MainTab.Timeline) }
    var selectedAlbum by remember { mutableStateOf<AlbumSummary?>(null) }
    var assetDetail by remember { mutableStateOf<AssetDetailContext?>(null) }

    val onLogout: () -> Unit = { authRepository.logout() }

    val albumBack: () -> Unit = { selectedAlbum = null }

    val topBar: @Composable () -> Unit = {
        when {
            selectedTab == MainTab.Albums && selectedAlbum != null -> AlbumDetailTopBar(
                title = albumDetailState.albumName ?: selectedAlbum!!.name,
                subtitle = albumDetailState.items.size.takeIf { it > 0 }?.let { "$it elementos" },
                onBack = albumBack,
                user = user.user,
                onLogout = onLogout
            )
            selectedTab == MainTab.Albums -> AlbumsListTopBar(user = user.user, onLogout = onLogout)
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
            onFavoriteChanged = ctx.onFavoriteChanged
        )
    }
    }
}
