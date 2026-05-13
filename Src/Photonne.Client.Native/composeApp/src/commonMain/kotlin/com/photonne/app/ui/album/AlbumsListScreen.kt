package com.photonne.app.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_empty_subtitle
import com.photonne.app.resources.albums_empty_title
import com.photonne.app.resources.albums_my_links_empty
import com.photonne.app.resources.albums_shared_empty
import com.photonne.app.resources.albums_tab_mine
import com.photonne.app.resources.albums_tab_my_links
import com.photonne.app.resources.albums_tab_shared
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumsListScreen(onAlbumClick: (AlbumSummary) -> Unit) {
    val viewModel: AlbumsViewModel = koinViewModel()
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()
    val visible = state.visibleAlbums

    Column(modifier = Modifier.fillMaxSize()) {
        AlbumsTabBar(selected = state.selectedTab, onSelect = viewModel::selectTab)
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.albums.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.errorMessage != null && state.albums.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            state.errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                visible.isEmpty() -> EmptyAlbumsState(state.selectedTab)
                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(visible, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                baseUrl = apiBaseUrl,
                                onClick = { onAlbumClick(album) }
                            )
                        }
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumsTabBar(selected: AlbumsTab, onSelect: (AlbumsTab) -> Unit) {
    val tabs = AlbumsTab.values()
    PrimaryTabRow(selectedTabIndex = tabs.indexOf(selected)) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = {
                    Text(
                        when (tab) {
                            AlbumsTab.Mine -> stringResource(Res.string.albums_tab_mine)
                            AlbumsTab.Shared -> stringResource(Res.string.albums_tab_shared)
                            AlbumsTab.MyLinks -> stringResource(Res.string.albums_tab_my_links)
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun EmptyAlbumsState(tab: AlbumsTab) {
    val title = stringResource(Res.string.albums_empty_title)
    val subtitle = when (tab) {
        AlbumsTab.Mine -> stringResource(Res.string.albums_empty_subtitle)
        AlbumsTab.Shared -> stringResource(Res.string.albums_shared_empty)
        AlbumsTab.MyLinks -> stringResource(Res.string.albums_my_links_empty)
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumSummary, baseUrl: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val cover = album.coverThumbnailUrl?.let { resolveCover(it, baseUrl) }
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        album.name.firstOrNull()?.uppercase() ?: "·",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${album.assetCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (album.hasActiveShareLink) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(50))
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Public share link active",
                        tint = Color.White,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )
    }
}

private fun resolveCover(coverUrl: String, baseUrl: String): String {
    if (coverUrl.startsWith("http://", ignoreCase = true) || coverUrl.startsWith("https://", ignoreCase = true)) {
        return coverUrl
    }
    val sep = if (coverUrl.startsWith("/")) "" else "/"
    return "$baseUrl$sep$coverUrl"
}
