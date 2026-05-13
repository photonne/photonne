package com.photonne.app.ui.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Collections
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
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.albums_empty_subtitle
import com.photonne.app.resources.albums_empty_title
import com.photonne.app.resources.albums_my_links_empty
import com.photonne.app.resources.albums_shared_empty
import com.photonne.app.resources.albums_tab_mine
import com.photonne.app.resources.albums_tab_my_links
import com.photonne.app.resources.albums_tab_shared
import com.photonne.app.ui.theme.EmptyState as SharedEmptyState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumsListScreen(
    onAlbumClick: (AlbumSummary) -> Unit,
    onAlbumLongPress: (AlbumSummary) -> Unit
) {
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
                else -> AlbumsContent(
                    albums = visible,
                    state = state,
                    apiBaseUrl = apiBaseUrl,
                    onClick = onAlbumClick,
                    onLongPress = onAlbumLongPress
                )
            }
        }
    }
}

@Composable
private fun AlbumsContent(
    albums: List<AlbumSummary>,
    state: AlbumsUiState,
    apiBaseUrl: String,
    onClick: (AlbumSummary) -> Unit,
    onLongPress: (AlbumSummary) -> Unit
) {
    val groups = if (state.groupByYear) groupByYear(albums) else emptyList()
    when (state.viewMode) {
        AlbumViewMode.Grid -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.groupByYear) {
                groups.forEach { (year, items) ->
                    item(
                        key = "year-$year",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        YearHeader(year)
                    }
                    items(items, key = { it.id }) { album ->
                        AlbumCard(
                            album = album,
                            baseUrl = apiBaseUrl,
                            isSelected = state.selectedAlbumId == album.id,
                            onClick = { onClick(album) },
                            onLongPress = { onLongPress(album) }
                        )
                    }
                }
            } else {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        baseUrl = apiBaseUrl,
                        isSelected = state.selectedAlbumId == album.id,
                        onClick = { onClick(album) },
                        onLongPress = { onLongPress(album) }
                    )
                }
            }
        }
        AlbumViewMode.List -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (state.groupByYear) {
                groups.forEach { (year, items) ->
                    item(key = "year-$year") { YearHeader(year, modifier = Modifier.padding(horizontal = 16.dp)) }
                    items(items, key = { it.id }) { album ->
                        AlbumRow(
                            album = album,
                            baseUrl = apiBaseUrl,
                            isSelected = state.selectedAlbumId == album.id,
                            onClick = { onClick(album) },
                            onLongPress = { onLongPress(album) }
                        )
                    }
                }
            } else {
                items(albums, key = { it.id }) { album ->
                    AlbumRow(
                        album = album,
                        baseUrl = apiBaseUrl,
                        isSelected = state.selectedAlbumId == album.id,
                        onClick = { onClick(album) },
                        onLongPress = { onLongPress(album) }
                    )
                }
            }
        }
    }
}

private fun groupByYear(albums: List<AlbumSummary>): List<Pair<Int, List<AlbumSummary>>> {
    val tz = TimeZone.currentSystemDefault()
    return albums
        .groupBy { it.createdAt.toLocalDateTime(tz).year }
        .toList()
        .sortedByDescending { it.first }
}

@Composable
private fun YearHeader(year: Int, modifier: Modifier = Modifier) {
    Text(
        text = year.toString(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp)
    )
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
    SharedEmptyState(
        icon = Icons.Outlined.Collections,
        title = title,
        subtitle = subtitle
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: AlbumSummary,
    baseUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isSelected) Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    ) else Modifier
                )
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
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.White, shape = RoundedCornerShape(50))
                        .padding(2.dp)
                        .size(20.dp)
                )
            }
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(
    album: AlbumSummary,
    baseUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isSelected) Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
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
                Text(
                    album.name.firstOrNull()?.uppercase() ?: "·",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .background(Color.White, shape = RoundedCornerShape(50))
                        .size(16.dp)
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            if (!album.description.isNullOrBlank()) {
                Text(
                    text = album.description!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = stringResource(Res.string.albums_count_format, album.assetCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun resolveCover(coverUrl: String, baseUrl: String): String {
    if (coverUrl.startsWith("http://", ignoreCase = true) || coverUrl.startsWith("https://", ignoreCase = true)) {
        return coverUrl
    }
    val sep = if (coverUrl.startsWith("/")) "" else "/"
    return "$baseUrl$sep$coverUrl"
}
