package com.photonne.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.compose.setSingletonImageLoaderFactory
import com.photonne.app.data.auth.AuthState
import com.photonne.app.data.auth.AuthStateHolder
import com.photonne.app.ui.asset.AssetDetailScreen
import com.photonne.app.ui.image.buildPhotonneImageLoader
import com.photonne.app.ui.login.LoginScreen
import com.photonne.app.ui.theme.PhotonneTheme
import com.photonne.app.ui.timeline.TimelineScreen
import com.photonne.app.ui.timeline.TimelineViewModel
import io.ktor.client.HttpClient
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
    val timelineViewModel: TimelineViewModel = koinViewModel()
    val timelineState by timelineViewModel.state.collectAsState()
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val openAsset: (Int) -> Unit = { index -> selectedIndex = index }
    val closeAsset: () -> Unit = { selectedIndex = null }

    val current = selectedIndex
    if (current != null && current in timelineState.items.indices) {
        AssetDetailScreen(
            items = timelineState.items,
            startIndex = current,
            hasMore = timelineState.hasMore,
            onLoadMore = timelineViewModel::loadMore,
            onBack = closeAsset
        )
    } else {
        TimelineScreen(
            user = user.user,
            state = timelineState,
            onItemClick = openAsset,
            onLoadMore = timelineViewModel::loadMore,
            onRefresh = timelineViewModel::refresh
        )
    }
}
