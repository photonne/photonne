package com.photonne.app.ui.album

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_action_search
import com.photonne.app.resources.albums_badge_smart
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.albums_empty_action_create
import com.photonne.app.resources.albums_empty_subtitle
import com.photonne.app.resources.albums_empty_title
import com.photonne.app.resources.albums_search_empty_subtitle
import com.photonne.app.resources.albums_search_empty_title
import com.photonne.app.resources.albums_search_placeholder
import com.photonne.app.resources.albums_shared_empty
import com.photonne.app.resources.albums_badge_shared
import com.photonne.app.resources.albums_badge_pinned
import com.photonne.app.resources.albums_section_others
import com.photonne.app.resources.albums_section_pinned
import com.photonne.app.ui.theme.SectionHeader
import com.photonne.app.resources.album_share_link_badge
import com.photonne.app.resources.explore_section_objects
import com.photonne.app.resources.explore_section_scenes
import com.photonne.app.resources.explore_title
import com.photonne.app.resources.memories_strip_title
import com.photonne.app.resources.map_title
import com.photonne.app.resources.people_title
import com.photonne.app.ui.main.CreateAction
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.ImmersiveChromeEffect
import com.photonne.app.ui.main.SearchFieldPill
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.Dp
import com.photonne.app.resources.albums_title
import com.photonne.app.resources.folders_action_filters
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.photonne.app.ui.theme.EmptyState as SharedEmptyState
import com.photonne.app.ui.theme.MetaBadge
import com.photonne.app.ui.theme.OverlayIconBadge
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.photonne.app.ui.util.PlatformVerticalScrollbar
import com.photonne.app.ui.theme.GridTilesSkeleton
import com.photonne.app.ui.theme.ListRowsSkeleton
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.EntryTile
import com.photonne.app.ui.theme.CollectionRow
import com.photonne.app.ui.theme.CollectionCover
import com.photonne.app.ui.theme.CollectionCard

@Composable
fun AlbumsListScreen(
    onAlbumClick: (AlbumSummary) -> Unit,
    onAlbumLongPress: (AlbumSummary) -> Unit,
    onCreateAlbum: (() -> Unit)? = null,
    onOpenMemories: () -> Unit = {},
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
    onChromeVisibleChange: (Boolean) -> Unit = {},
    /** Se incrementa al retocar la pestaña Álbumes ya activa: volver arriba. */
    scrollToTopTick: Int = 0
) {
    val viewModel: AlbumsViewModel = koinViewModel()
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val visible = state.visibleAlbums

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val isGrid = state.viewMode == AlbumViewMode.Grid
    // Retocar la pestaña ya activa en su raíz vuelve arriba, como Fotos.
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) {
            if (isGrid) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0)
        }
    }
    // La búsqueda también va en la cápsula flotante (campo dentro), como el
    // buscador global: solo una selección activa la sustituye por la cápsula de
    // selección.
    val floatingChrome = !state.isSelectionActive
    // Con selección, la cápsula de selección ocupa el mismo hueco: se reserva siempre.
    val reservedTop = subscreenChromeReservedTop()

    // Automatic asset groupings (Memories / People / Map / Scenes / Objects) that sit atop
    // the album list — a scroll header so they pass under the floating chrome.
    val previewsViewModel: ExplorePreviewsViewModel = koinViewModel()
    val previews by previewsViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { previewsViewModel.loadIfNeeded() }
    val exploreRow: @Composable () -> Unit = {
        ExploreRow(
            peopleFaceIds = previews.peopleFaceIds,
            apiBaseUrl = apiBaseUrl,
            onOpenMemories = onOpenMemories,
            onOpenPeople = onOpenPeople,
            onOpenMap = onOpenMap,
            onOpenScenes = onOpenScenes,
            onOpenObjects = onOpenObjects
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PhotonneRefreshableScreen(
                indicatorTopPadding = reservedTop,
                isRefreshing = state.isLoading && state.albums.isNotEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.isLoading && state.albums.isEmpty() ->
                            Column(modifier = Modifier.fillMaxSize().padding(top = reservedTop)) {
                                exploreRow()
                                // El esqueleto con la forma de la vista elegida.
                                if (isGrid) GridTilesSkeleton() else ListRowsSkeleton()
                            }
                        state.error != null && state.albums.isEmpty() ->
                            Column(modifier = Modifier.fillMaxSize().padding(top = reservedTop)) {
                                exploreRow()
                                com.photonne.app.ui.error.FullScreenError(
                                    error = state.error,
                                    onRetry = viewModel::refresh,
                                    modifier = Modifier.weight(1f)
                                )
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
                            albums = state.unpinnedAlbums,
                            pinned = state.pinnedAlbums,
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
            val searching = state.isSearchActive
            SubscreenFloatingChrome(
                title = if (searching) "" else stringResource(Res.string.albums_title),
                // Al buscar, el botón de atrás cierra la búsqueda (como el buscador).
                onBack = if (searching) viewModel::toggleSearch else null,
                titleContent = if (searching) {
                    {
                        SearchFieldPill(
                            value = state.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            onClear = { viewModel.setSearchQuery("") },
                            placeholder = stringResource(Res.string.albums_search_placeholder),
                            autofocus = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else null,
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
                        // CreateAction es el afford de "añadir aquí" de la app
                        // (tonal, no un icono plano más de la fila).
                        CreateAction(
                            icon = PhotonneIcons.Add,
                            contentDescription = stringResource(
                                Res.string.albums_empty_action_create
                            ),
                            onClick = onCreateAlbum
                        )
                    }
                    if (!searching) {
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(
                                PhotonneIcons.Search,
                                contentDescription = stringResource(Res.string.albums_action_search)
                            )
                        }
                    }
                    IconButton(onClick = onOpenFilters) {
                        Icon(
                            PhotonneIcons.Filter,
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
private fun EmptySearchState(query: String) {
    SharedEmptyState(
        icon = PhotonneIcons.NoResults,
        title = stringResource(Res.string.albums_search_empty_title),
        subtitle = stringResource(Res.string.albums_search_empty_subtitle, query)
    )
}

@Composable
private fun AlbumsContent(
    albums: List<AlbumSummary>,
    /** Sección "Fijados" encima del resto; vacía = sin sección. */
    pinned: List<AlbumSummary>,
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
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
            if (pinned.isNotEmpty()) {
                item(key = "pinned-header", span = { GridItemSpan(maxLineSpan) }) {
                    PinnedSectionHeader(pinned = true, modifier = Modifier.animateItem())
                }
                items(pinned, key = { it.id }) { album ->
                    AlbumCard(
                        modifier = Modifier.animateItem(),
                        album = album,
                        baseUrl = apiBaseUrl,
                        isSelected = album.id in state.selectedAlbumIds,
                        onClick = { onClick(album) },
                        onLongPress = { onLongPress(album) }
                    )
                }
                // Con años, los propios años ya separan; sin ellos, un título.
                if (albums.isNotEmpty() && !state.groupByYear) {
                    item(key = "others-header", span = { GridItemSpan(maxLineSpan) }) {
                        PinnedSectionHeader(pinned = false, modifier = Modifier.animateItem())
                    }
                }
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
                            modifier = Modifier.animateItem(),
                            album = album,
                            baseUrl = apiBaseUrl,
                            isSelected = album.id in state.selectedAlbumIds,
                            onClick = { onClick(album) },
                            onLongPress = { onLongPress(album) }
                        )
                    }
                }
            } else {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(
                        modifier = Modifier.animateItem(),
                        album = album,
                        baseUrl = apiBaseUrl,
                        isSelected = album.id in state.selectedAlbumIds,
                        onClick = { onClick(album) },
                        onLongPress = { onLongPress(album) }
                    )
                }
            }
        }
        AlbumViewMode.List -> Box(Modifier.fillMaxSize()) {
            LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            contentPadding = PaddingValues(
                top = 8.dp + chromeTopReserve,
                bottom = reservedBottom ?: 8.dp
            ),
            modifier = Modifier.fillMaxSize().hazeSource(hazeState)
        ) {
            if (exploreHeader != null) {
                item(key = "explore-row") { exploreHeader() }
            }
            if (pinned.isNotEmpty()) {
                item(key = "pinned-header") {
                    PinnedSectionHeader(
                        pinned = true,
                        modifier = Modifier.animateItem().padding(horizontal = Spacing.sm)
                    )
                }
                items(pinned, key = { it.id }) { album ->
                    AlbumRow(
                        modifier = Modifier.animateItem(),
                        album = album,
                        baseUrl = apiBaseUrl,
                        isSelected = album.id in state.selectedAlbumIds,
                        onClick = { onClick(album) },
                        onLongPress = { onLongPress(album) }
                    )
                }
                if (albums.isNotEmpty() && !state.groupByYear) {
                    item(key = "others-header") {
                        PinnedSectionHeader(
                            pinned = false,
                            modifier = Modifier.animateItem().padding(horizontal = Spacing.sm)
                        )
                    }
                }
            }
            if (state.groupByYear) {
                groups.forEach { (year, items) ->
                    item(key = "year-$year") { YearHeader(year, modifier = Modifier.padding(horizontal = Spacing.lg)) }
                    items(items, key = { it.id }) { album ->
                        AlbumRow(
                            modifier = Modifier.animateItem(),
                            album = album,
                            baseUrl = apiBaseUrl,
                            isSelected = album.id in state.selectedAlbumIds,
                            onClick = { onClick(album) },
                            onLongPress = { onLongPress(album) }
                        )
                    }
                }
            } else {
                items(albums, key = { it.id }) { album ->
                    AlbumRow(
                        modifier = Modifier.animateItem(),
                        album = album,
                        baseUrl = apiBaseUrl,
                        isSelected = album.id in state.selectedAlbumIds,
                        onClick = { onClick(album) },
                        onLongPress = { onLongPress(album) }
                    )
                }
            }
        }
            PlatformVerticalScrollbar(
                state = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

/** "Fijados" o, debajo, "Otros álbumes". */
@Composable
private fun PinnedSectionHeader(pinned: Boolean, modifier: Modifier = Modifier) {
    SectionHeader(
        text = stringResource(
            if (pinned) Res.string.albums_section_pinned else Res.string.albums_section_others
        ),
        modifier = modifier
    )
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
        modifier = modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
    )
}

@Composable
private fun ExploreRow(
    peopleFaceIds: List<String>,
    apiBaseUrl: String,
    onOpenMemories: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenObjects: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.lg)
    ) {
        Text(
            text = stringResource(Res.string.explore_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.lg, bottom = 6.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Recuerdos también es una colección generada; aquí gana una segunda
            // puerta además de la tira de Fotos, que solo enseña "hoy hace…".
            ExploreCard(
                label = stringResource(Res.string.memories_strip_title),
                icon = PhotonneIcons.Memories,
                onClick = onOpenMemories,
                modifier = Modifier.weight(1f)
            )
            ExploreCard(
                label = stringResource(Res.string.people_title),
                icon = PhotonneIcons.People,
                onClick = onOpenPeople,
                modifier = Modifier.weight(1f),
                // Las caras de quien más sale; el icono mientras no llegan.
                preview = if (peopleFaceIds.isNotEmpty()) {
                    { OverlappingFaces(peopleFaceIds, apiBaseUrl) }
                } else null
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
    modifier: Modifier = Modifier,
    preview: (@Composable () -> Unit)? = null
) {
    EntryTile(icon = icon, label = label, onClick = onClick, modifier = modifier, preview = preview)
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
        icon = PhotonneIcons.Album,
        title = title,
        subtitle = subtitle,
        actionLabel = action?.let { stringResource(Res.string.albums_empty_action_create) },
        onAction = action
    )
}

@Composable
private fun AlbumCard(
    album: AlbumSummary,
    baseUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    CollectionCard(
        title = album.name,
        thumbnail = { AlbumCover(album, baseUrl, large = true) },
        onClick = onClick,
        onLongClick = onLongPress,
        selected = isSelected,
        count = album.assetCount,
        modifier = modifier,
        badges = {
            if (album.isPinned) {
                OverlayIconBadge(
                    icon = PhotonneIcons.PinActive,
                    contentDescription = stringResource(Res.string.albums_badge_pinned)
                )
            }
            if (album.isSmart) {
                // Sin distintivo, un álbum de reglas parecía uno normal y
                // sus acciones imposibles (añadir fotos) confundían.
                OverlayIconBadge(
                    icon = Icons.Outlined.AutoAwesome,
                    contentDescription = stringResource(Res.string.albums_badge_smart)
                )
            }
            if (album.isShared || !album.isOwner) {
                OverlayIconBadge(
                    icon = PhotonneIcons.Person,
                    contentDescription = stringResource(Res.string.albums_badge_shared)
                )
            }
            if (album.hasActiveShareLink) {
                OverlayIconBadge(
                    icon = PhotonneIcons.Share,
                    contentDescription = stringResource(Res.string.album_share_link_badge)
                )
            }
        }
    )
}

@Composable
private fun AlbumRow(
    album: AlbumSummary,
    baseUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    CollectionRow(
        title = album.name,
        subtitle = album.description?.takeIf { it.isNotBlank() },
        thumbnail = { AlbumCover(album, baseUrl, large = false) },
        onClick = onClick,
        onLongClick = onLongPress,
        selected = isSelected,
        modifier = modifier,
        badges = {
            Text(
                text = pluralStringResource(Res.plurals.albums_count_format, album.assetCount, album.assetCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // List mode used to show no qualifiers at all, so switching to it
            // silently dropped what the grid told you about an album.
            if (album.isPinned) {
                MetaBadge(stringResource(Res.string.albums_badge_pinned), PhotonneIcons.PinActive)
            }
            if (album.isSmart) {
                MetaBadge(stringResource(Res.string.albums_badge_smart), Icons.Outlined.AutoAwesome)
            }
            if (album.isShared || !album.isOwner) {
                MetaBadge(stringResource(Res.string.albums_badge_shared), PhotonneIcons.Person)
            }
            if (album.hasActiveShareLink) {
                MetaBadge(stringResource(Res.string.album_share_link_badge), PhotonneIcons.Share)
            }
        }
    )
}

/**
 * Portada de un álbum para [CollectionRow]/[CollectionCard]; sin portada, la
 * inicial del nombre. También la usa "Añadir a álbum".
 */
@Composable
internal fun AlbumCover(album: AlbumSummary, baseUrl: String, large: Boolean) {
    CollectionCover(
        model = album.coverThumbnailUrl?.let { resolveCover(it, baseUrl) },
        contentDescription = album.name
    ) {
        Text(
            album.name.firstOrNull()?.uppercase() ?: "·",
            style = if (large) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
