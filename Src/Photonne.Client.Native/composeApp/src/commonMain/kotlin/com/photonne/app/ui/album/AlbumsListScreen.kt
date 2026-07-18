package com.photonne.app.ui.album

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_action_search
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.albums_empty_action_create
import com.photonne.app.resources.albums_empty_subtitle
import com.photonne.app.resources.albums_empty_title
import com.photonne.app.resources.albums_search_empty_subtitle
import com.photonne.app.resources.albums_search_empty_title
import com.photonne.app.resources.albums_search_placeholder
import com.photonne.app.resources.albums_shared_empty
import com.photonne.app.resources.albums_badge_shared
import com.photonne.app.resources.album_share_link_badge
import com.photonne.app.resources.explore_section_objects
import com.photonne.app.resources.explore_section_scenes
import com.photonne.app.resources.explore_title
import com.photonne.app.resources.map_title
import com.photonne.app.resources.people_title
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.ImmersiveChromeEffect
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.Dp
import com.photonne.app.resources.albums_title
import com.photonne.app.resources.folders_action_filters
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.photonne.app.ui.theme.EmptyState as SharedEmptyState
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.MetaBadge
import com.photonne.app.ui.theme.OverlayIconBadge
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AlbumsListScreen(
    onAlbumClick: (AlbumSummary) -> Unit,
    onAlbumLongPress: (AlbumSummary) -> Unit,
    onCreateAlbum: (() -> Unit)? = null,
    onOpenPeople: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenScenes: () -> Unit = {},
    onOpenObjects: () -> Unit = {},
    onOpenFilters: () -> Unit = {},
    /**
     * Immersive bottom nav: while true the albums list drives the hide-on-scroll
     * chrome (reported via [onChromeVisibleChange]) and reserves the nav's height
     * at its scroll end so it can draw edge-to-edge behind the bar, like Fotos.
     */
    immersive: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val viewModel: AlbumsViewModel = koinViewModel()
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()
    val visible = state.visibleAlbums

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val isGrid = state.viewMode == AlbumViewMode.Grid
    val floatingChrome = !state.isSearchActive && !state.isSelectionActive
    val reservedTop = if (floatingChrome) subscreenChromeReservedTop() else 0.dp

    // Automatic asset groupings (People / Map / Scenes / Objects) that sit atop
    // the album list — a scroll header so they pass under the floating chrome.
    val exploreRow: @Composable () -> Unit = {
        ExploreRow(
            onOpenPeople = onOpenPeople,
            onOpenMap = onOpenMap,
            onOpenScenes = onOpenScenes,
            onOpenObjects = onOpenObjects
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.isSearchActive) {
                AlbumsSearchField(
                    query = state.searchQuery,
                    onQueryChange = viewModel::setSearchQuery,
                    onClose = viewModel::toggleSearch
                )
            }
            PhotonneRefreshableScreen(
                isRefreshing = state.isLoading && state.albums.isNotEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.isLoading && state.albums.isEmpty() ->
                            Column(modifier = Modifier.fillMaxSize().padding(top = reservedTop)) {
                                exploreRow()
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        state.error != null && state.albums.isEmpty() ->
                            Column(modifier = Modifier.fillMaxSize().padding(top = reservedTop)) {
                                exploreRow()
                                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                                    com.photonne.app.ui.error.ErrorBanner(error = state.error)
                                }
                            }
                        visible.isEmpty() && state.hasActiveQuery ->
                            Column(modifier = Modifier.fillMaxSize().padding(top = reservedTop)) {
                                exploreRow()
                                EmptySearchState(query = state.searchQuery.trim())
                            }
                        visible.isEmpty() ->
                            Column(modifier = Modifier.fillMaxSize().padding(top = reservedTop)) {
                                exploreRow()
                                EmptyAlbumsState(scope = state.scope, onCreateAlbum = onCreateAlbum)
                            }
                        else -> AlbumsContent(
                            albums = visible,
                            state = state,
                            apiBaseUrl = apiBaseUrl,
                            onClick = onAlbumClick,
                            onLongPress = onAlbumLongPress,
                            gridState = gridState,
                            listState = listState,
                            hazeState = hazeState,
                            chromeTopReserve = reservedTop,
                            exploreHeader = exploreRow,
                            immersive = immersive,
                            onChromeVisibleChange = onChromeVisibleChange
                        )
                    }
                }
            }
        }

        if (floatingChrome) {
            SubscreenFloatingChrome(
                title = stringResource(Res.string.albums_title),
                onBack = null,
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = {
                        if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                    },
                    firstVisibleItemScrollOffset = {
                        if (isGrid) gridState.firstVisibleItemScrollOffset
                        else listState.firstVisibleItemScrollOffset
                    },
                    isScrollInProgress = {
                        if (isGrid) gridState.isScrollInProgress else listState.isScrollInProgress
                    },
                    scrollToTopMinIndex = 6,
                    onScrollToTop = {
                        if (isGrid) {
                            if (gridState.firstVisibleItemIndex > 24) gridState.scrollToItem(24)
                            gridState.animateScrollToItem(0)
                        } else {
                            if (listState.firstVisibleItemIndex > 24) listState.scrollToItem(24)
                            listState.animateScrollToItem(0)
                        }
                    }
                ),
                hazeState = hazeState,
                onChromeVisibleChange = {},
                actions = {
                    if (onCreateAlbum != null) {
                        IconButton(onClick = onCreateAlbum) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = stringResource(Res.string.albums_empty_action_create)
                            )
                        }
                    }
                    IconButton(onClick = viewModel::toggleSearch) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = stringResource(Res.string.albums_action_search)
                        )
                    }
                    IconButton(onClick = onOpenFilters) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = stringResource(Res.string.folders_action_filters),
                            tint = if (state.isFilterActive) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun AlbumsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(Res.string.albums_search_placeholder)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = {
                if (query.isEmpty()) onClose() else onQueryChange("")
            }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.albums_action_search)
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .focusRequester(focusRequester)
    )
}

@Composable
private fun EmptySearchState(query: String) {
    SharedEmptyState(
        icon = Icons.Filled.Search,
        title = stringResource(Res.string.albums_search_empty_title),
        subtitle = stringResource(Res.string.albums_search_empty_subtitle, query)
    )
}

@Composable
private fun AlbumsContent(
    albums: List<AlbumSummary>,
    state: AlbumsUiState,
    apiBaseUrl: String,
    onClick: (AlbumSummary) -> Unit,
    onLongPress: (AlbumSummary) -> Unit,
    gridState: LazyGridState,
    listState: LazyListState,
    hazeState: HazeState,
    chromeTopReserve: Dp = 0.dp,
    /** ExploreRow como cabecera del scroll, para que pase bajo el cromo flotante. */
    exploreHeader: (@Composable () -> Unit)? = null,
    immersive: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val groups = if (state.groupByYear) groupByYear(albums) else emptyList()
    val isGrid = state.viewMode == AlbumViewMode.Grid
    // One list now serves every scope, so the scroll position survives a filter
    // change and would otherwise land on an index the shorter list doesn't have
    // — which also leaves ImmersiveChromeEffect reading a stale first-visible
    // index and the chrome stuck hidden.
    LaunchedEffect(state.scope) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }
    if (immersive) {
        ImmersiveChromeEffect(
            firstVisibleItemIndex = {
                if (isGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
            },
            firstVisibleItemScrollOffset = {
                if (isGrid) gridState.firstVisibleItemScrollOffset
                else listState.firstVisibleItemScrollOffset
            },
            isScrollInProgress = {
                if (isGrid) gridState.isScrollInProgress else listState.isScrollInProgress
            },
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
    // Reserve the bottom nav's height at the scroll end so the last row clears
    // it while the grid draws full-bleed behind the (overlaid) bar. La cápsula
    // de selección ocupa ese mismo hueco con la misma altura, así que reserva
    // igual aunque `immersive` esté apagado por haber una tarjeta seleccionada.
    val reservedBottom = if (immersive || state.isSelectionActive) {
        floatingNavBarReservedHeight()
    } else null
    when (state.viewMode) {
        AlbumViewMode.Grid -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp + chromeTopReserve,
                end = 16.dp,
                bottom = reservedBottom ?: 16.dp
            ),
            modifier = Modifier.fillMaxSize().hazeSource(hazeState)
        ) {
            if (exploreHeader != null) {
                item(key = "explore-row", span = { GridItemSpan(maxLineSpan) }) { exploreHeader() }
            }
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
            state = listState,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(
                top = 8.dp + chromeTopReserve,
                bottom = reservedBottom ?: 8.dp
            ),
            modifier = Modifier.fillMaxSize().hazeSource(hazeState)
        ) {
            if (exploreHeader != null) {
                item(key = "explore-row") { exploreHeader() }
            }
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

@Composable
private fun ExploreRow(
    onOpenPeople: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenObjects: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
    ) {
        Text(
            text = stringResource(Res.string.explore_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExploreCard(
                label = stringResource(Res.string.people_title),
                icon = Icons.Outlined.People,
                onClick = onOpenPeople,
                modifier = Modifier.weight(1f)
            )
            ExploreCard(
                label = stringResource(Res.string.map_title),
                icon = Icons.Outlined.Map,
                onClick = onOpenMap,
                modifier = Modifier.weight(1f)
            )
            ExploreCard(
                label = stringResource(Res.string.explore_section_scenes),
                icon = Icons.Outlined.Landscape,
                onClick = onOpenScenes,
                modifier = Modifier.weight(1f)
            )
            ExploreCard(
                label = stringResource(Res.string.explore_section_objects),
                icon = Icons.Outlined.Category,
                onClick = onOpenObjects,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ExploreCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyAlbumsState(scope: AlbumsScope, onCreateAlbum: (() -> Unit)?) {
    val title = stringResource(Res.string.albums_empty_title)
    val subtitle = when (scope) {
        AlbumsScope.All, AlbumsScope.Mine -> stringResource(Res.string.albums_empty_subtitle)
        AlbumsScope.Shared -> stringResource(Res.string.albums_shared_empty)
    }
    // CTA everywhere but "Compartidos", whose empty state depends on someone
    // else sharing with you — a Create button there would promise something the
    // user can't actually trigger. Under "Todos" an empty list means no albums
    // at all, so creating one is exactly the right move.
    val action = onCreateAlbum?.takeIf { scope != AlbumsScope.Shared }
    SharedEmptyState(
        icon = Icons.Outlined.Collections,
        title = title,
        subtitle = subtitle,
        actionLabel = action?.let { stringResource(Res.string.albums_empty_action_create) },
        onAction = action
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
                    .background(PhotonneColors.scrimMedium, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${album.assetCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (album.isShared || !album.isOwner) {
                    OverlayIconBadge(
                        icon = Icons.Filled.Person,
                        contentDescription = stringResource(Res.string.albums_badge_shared)
                    )
                }
                if (album.hasActiveShareLink) {
                    OverlayIconBadge(
                        icon = Icons.Outlined.Share,
                        contentDescription = stringResource(Res.string.album_share_link_badge)
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
        Column(modifier = Modifier.weight(1f)) {
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
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.albums_count_format, album.assetCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // List mode used to show no qualifiers at all, so switching to it
                // silently dropped what the grid told you about an album.
                if (album.isShared || !album.isOwner) {
                    MetaBadge(stringResource(Res.string.albums_badge_shared), Icons.Filled.Person)
                }
                if (album.hasActiveShareLink) {
                    MetaBadge(stringResource(Res.string.album_share_link_badge), Icons.Outlined.Share)
                }
            }
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
