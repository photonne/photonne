package com.photonne.app.ui.folder

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_count_format
import com.photonne.app.resources.organize_inbox_card_subtitle
import com.photonne.app.resources.organize_inbox_count_format
import com.photonne.app.resources.organize_inbox_title
import com.photonne.app.resources.folder_external_badge
import com.photonne.app.resources.folder_shared_badge
import com.photonne.app.resources.folder_discovery_excluded_badge
import com.photonne.app.resources.folders_action_search
import com.photonne.app.resources.folders_empty_subtitle
import com.photonne.app.resources.folders_empty_title
import com.photonne.app.resources.folders_external_empty
import com.photonne.app.resources.folders_search_empty_subtitle
import com.photonne.app.resources.folders_search_empty_title
import com.photonne.app.resources.folders_search_placeholder
import com.photonne.app.resources.folders_shared_empty
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.ImmersiveChromeEffect
import com.photonne.app.ui.theme.EmptyState as SharedEmptyState
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.MetaBadge
import com.photonne.app.ui.main.SearchFieldPill
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.OverlayIconBadge
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.unit.Dp
import com.photonne.app.resources.folder_action_new
import com.photonne.app.resources.folders_action_filters
import com.photonne.app.resources.folders_title
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FoldersListScreen(
    onFolderClick: (FolderSummary) -> Unit,
    onFolderLongPress: (FolderSummary) -> Unit,
    onOpenOrganize: () -> Unit,
    onOpenFilters: () -> Unit = {},
    onCreateFolder: (() -> Unit)? = null,
    /**
     * Immersive bottom nav: while true the active folder list drives the
     * hide-on-scroll chrome (reported via [onChromeVisibleChange]) and reserves
     * the nav's height at its scroll end so it draws edge-to-edge behind the
     * bar, like Fotos.
     */
    immersive: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val viewModel: FoldersViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    val folders = state.visibleFolders
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val isGrid = state.viewMode == FolderViewMode.Grid
    // La búsqueda también va en la cápsula flotante (campo dentro), como el
    // buscador global: solo una selección activa la sustituye por la barra sólida.
    val floatingChrome = !state.isSelectionActive
    val reservedTop = if (floatingChrome) subscreenChromeReservedTop() else 0.dp
    // The inbox holds personal assets, so it makes no sense framed by
    // Compartidas/Externas — but it has to survive "Todas", the default.
    val showInbox = state.organizePendingCount > 0 &&
        !state.hasActiveQuery &&
        (state.scope == FoldersScope.All || state.scope == FoldersScope.Personal)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PhotonneRefreshableScreen(
                isRefreshing = state.isLoading && folders.isNotEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                FolderListContent(
                    folders = folders,
                    state = state,
                    isLoading = state.isLoading,
                    errorMessage = state.error?.userMessage,
                    emptyTitle = stringResource(Res.string.folders_empty_title),
                    emptySubtitle = when (state.scope) {
                        FoldersScope.All, FoldersScope.Personal ->
                            stringResource(Res.string.folders_empty_subtitle)
                        FoldersScope.Shared ->
                            stringResource(Res.string.folders_shared_empty)
                        FoldersScope.External ->
                            stringResource(Res.string.folders_external_empty)
                    },
                    onFolderClick = onFolderClick,
                    onFolderLongPress = onFolderLongPress,
                    gridState = gridState,
                    listState = listState,
                    hazeState = hazeState,
                    chromeTopReserve = reservedTop,
                    inboxHeader = if (showInbox) {
                        { OrganizeInboxCard(count = state.organizePendingCount, onClick = onOpenOrganize) }
                    } else null,
                    immersive = immersive,
                    onChromeVisibleChange = onChromeVisibleChange
                )
            }
        }

        if (floatingChrome) {
            val searching = state.isSearchActive
            SubscreenFloatingChrome(
                title = if (searching) "" else stringResource(Res.string.folders_title),
                onBack = if (searching) viewModel::toggleSearch else null,
                titleContent = if (searching) {
                    {
                        SearchFieldPill(
                            value = state.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            onClear = { viewModel.setSearchQuery("") },
                            placeholder = stringResource(Res.string.folders_search_placeholder),
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
                // La nav la mueve el ImmersiveChromeEffect de FolderListContent
                // (mismo scroll → en sync); el cromo solo se oculta a sí mismo.
                onChromeVisibleChange = {},
                actions = {
                    if (onCreateFolder != null) {
                        IconButton(onClick = onCreateFolder) {
                            Icon(
                                Icons.Outlined.CreateNewFolder,
                                contentDescription = stringResource(Res.string.folder_action_new)
                            )
                        }
                    }
                    if (!searching) {
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(Res.string.folders_action_search)
                            )
                        }
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
private fun FolderListContent(
    folders: List<FolderSummary>,
    state: FoldersUiState,
    isLoading: Boolean,
    errorMessage: String?,
    emptyTitle: String,
    emptySubtitle: String,
    onFolderClick: (FolderSummary) -> Unit,
    onFolderLongPress: (FolderSummary) -> Unit,
    gridState: LazyGridState,
    listState: LazyListState,
    hazeState: HazeState,
    /** Reserva del cromo flotante arriba (0 cuando el cromo va acoplado). */
    chromeTopReserve: Dp = 0.dp,
    /** Tarjeta "Para organizar" como cabecera del scroll (o null si no toca). */
    inboxHeader: (@Composable () -> Unit)? = null,
    immersive: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val isGrid = state.viewMode == FolderViewMode.Grid
    val hasList = folders.isNotEmpty()
    // One list now serves every scope, so the scroll position survives a filter
    // change and would otherwise land on an index the shorter list doesn't have
    // — which also leaves ImmersiveChromeEffect reading a stale first-visible
    // index and the chrome stuck hidden.
    LaunchedEffect(state.scope) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }
    if (immersive && hasList) {
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
    // La cápsula de selección ocupa el hueco de la nav flotante con la misma
    // altura, así que se reserva igual aunque `immersive` esté apagado por haber
    // una tarjeta seleccionada.
    val reservedBottom = if (immersive || state.isSelectionActive) {
        floatingNavBarReservedHeight()
    } else null
    when {
        isLoading && folders.isEmpty() ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        folders.isEmpty() && state.hasActiveQuery ->
            EmptySearchState(query = state.searchQuery.trim())
        folders.isEmpty() && errorMessage == null ->
            EmptyState(title = emptyTitle, subtitle = emptySubtitle)
        errorMessage != null && folders.isEmpty() ->
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        else -> when (state.viewMode) {
            FolderViewMode.List -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(
                    top = 8.dp + chromeTopReserve,
                    bottom = reservedBottom ?: 8.dp
                ),
                modifier = Modifier.fillMaxSize().hazeSource(hazeState)
            ) {
                if (inboxHeader != null) {
                    item(key = "organize-inbox") { inboxHeader() }
                }
                items(folders, key = { it.id }) { folder ->
                    FolderRow(
                        folder = folder,
                        isSelected = state.selectedFolderId == folder.id,
                        onClick = { onFolderClick(folder) },
                        onLongPress = { onFolderLongPress(folder) }
                    )
                }
            }
            FolderViewMode.Grid -> LazyVerticalGrid(
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
                if (inboxHeader != null) {
                    item(key = "organize-inbox", span = { GridItemSpan(maxLineSpan) }) {
                        inboxHeader()
                    }
                }
                items(folders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        isSelected = state.selectedFolderId == folder.id,
                        onClick = { onFolderClick(folder) },
                        onLongPress = { onFolderLongPress(folder) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    folder: FolderSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
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
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle
                else Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name.ifBlank { "/" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = folder.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.albums_count_format, folder.assetCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (folder.isShared) {
                    MetaBadge(stringResource(Res.string.folder_shared_badge), Icons.Filled.Person)
                }
                if (folder.externalLibraryId != null) {
                    MetaBadge(stringResource(Res.string.folder_external_badge), Icons.AutoMirrored.Filled.LibraryBooks)
                }
                if (folder.excludedFromDiscovery) {
                    MetaBadge(stringResource(Res.string.folder_discovery_excluded_badge), Icons.Outlined.VisibilityOff)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(
    folder: FolderSummary,
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
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(PhotonneColors.scrimMedium, shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${folder.assetCount}",
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
                // Same order as FolderRow, so a folder reads the same in both view modes.
                if (folder.isShared) {
                    OverlayIconBadge(
                        icon = Icons.Filled.Person,
                        contentDescription = stringResource(Res.string.folder_shared_badge)
                    )
                }
                if (folder.externalLibraryId != null) {
                    OverlayIconBadge(
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        contentDescription = stringResource(Res.string.folder_external_badge)
                    )
                }
                if (folder.excludedFromDiscovery) {
                    OverlayIconBadge(
                        icon = Icons.Outlined.VisibilityOff,
                        contentDescription = stringResource(Res.string.folder_discovery_excluded_badge)
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
            text = folder.name.ifBlank { folder.path },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    SharedEmptyState(
        icon = Icons.Outlined.Folder,
        title = title,
        subtitle = subtitle
    )
}

@Composable
private fun EmptySearchState(query: String) {
    SharedEmptyState(
        icon = Icons.Filled.Search,
        title = stringResource(Res.string.folders_search_empty_title),
        subtitle = stringResource(Res.string.folders_search_empty_subtitle, query)
    )
}

/**
 * Entry card at the top of the Personal tab: how many assets are still sitting
 * in MobileBackup ("sin organizar"), tapping opens the "Para organizar" inbox.
 * Only shown while there's something pending, so a tidy library isn't nagged.
 */
@Composable
private fun OrganizeInboxCard(
    count: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.organize_inbox_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(Res.string.organize_inbox_card_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        // The count sits between a weighted title and the chevron; without a
        // one-line cap it grabs intrinsic width on narrow screens and starves the
        // title column, which then wraps character-by-character (vertical text).
        Text(
            text = stringResource(Res.string.organize_inbox_count_format, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
