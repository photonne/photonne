package com.photonne.app.ui.folder

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.folder_detail_empty_subtitle
import com.photonne.app.resources.folder_detail_empty_title
import com.photonne.app.ui.grid.AssetGridCell
import com.photonne.app.ui.grid.assetCellKey
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.remember
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.ImmersiveChromeEffect
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.grid.AssetGridDragSelect
import com.photonne.app.ui.grid.rememberAssetGridSelectionGestures
import com.photonne.app.ui.grid.dragselect.AssetCellContentType
import com.photonne.app.ui.grid.dragselect.dragSelectable
import com.photonne.app.ui.grid.dragselect.rememberLazyGridDragSelectAdapter
import com.photonne.app.ui.haptics.rememberPhotonneHaptics
import com.photonne.app.ui.theme.EmptyState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.Spacing

@Composable
fun FolderDetailScreen(
    folderId: String,
    folderName: String,
    parentFolderId: String?,
    title: String,
    onBack: () -> Unit,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onSubfolderClick: (FolderSummary) -> Unit,
    onSubfolderLongPress: (FolderSummary) -> Unit,
    viewModel: FolderDetailViewModel,
    actions: @Composable RowScope.() -> Unit = {},
    /**
     * Immersive bottom nav: while true the photo grid drives the hide-on-scroll
     * chrome (reported via [onChromeVisibleChange]) and reserves the nav's height
     * at its scroll end so it draws edge-to-edge behind the bar, like Fotos.
     */
    immersive: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val hazeState = remember { HazeState() }
    val gestures = rememberAssetGridSelectionGestures(viewModel::applySelection)
    // Cromo flotante salvo con una selección (de assets o de subcarpetas) activa,
    // que muestra su cápsula de selección en el mismo hueco: la rejilla lo
    // reserva siempre, así que la selección no la mueve.
    val floatingChrome = !state.isSelectionActive && !state.isSubfolderSelectionActive
    val reservedTop = subscreenChromeReservedTop()
    // Only drive the immersive chrome when the photo grid is what's on screen —
    // a subfolders-only view keeps the nav docked.
    val gridActive = immersive && state.items.isNotEmpty()
    if (gridActive) {
        ImmersiveChromeEffect(
            firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
            firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
            isScrollInProgress = { gridState.isScrollInProgress },
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
    // Con una selección activa la nav flotante deja su sitio a la cápsula de
    // acciones, que mide lo mismo: se sigue reservando el mismo hueco.
    val reservedBottom = if (immersive || state.isSelectionActive ||
        state.isSubfolderSelectionActive
    ) {
        floatingNavBarReservedHeight()
    } else 0.dp
    val gridContentPadding = PaddingValues(top = reservedTop, bottom = reservedBottom)

    LaunchedEffect(folderId) { viewModel.open(folderId, folderName, parentFolderId) }

    Box(modifier = Modifier.fillMaxSize()) {
        PhotonneRefreshableScreen(
            indicatorTopPadding = reservedTop,
            isRefreshing = state.isLoading &&
                (state.items.isNotEmpty() || state.subFolders.isNotEmpty()),
            onRefresh = viewModel::refresh
        ) {
            when {
                state.isLoading && state.items.isEmpty() && state.subFolders.isEmpty() ->
                    AssetGridSkeleton(contentPadding = PaddingValues(top = reservedTop))
                state.error != null && state.items.isEmpty() && state.subFolders.isEmpty() ->
                    com.photonne.app.ui.error.FullScreenError(
                        error = state.error,
                        onRetry = { state.folderId?.let { id ->
                            viewModel.open(id, state.folderName.orEmpty(), state.parentFolderId)
                        } },
                        modifier = Modifier.padding(top = reservedTop)
                    )
                state.items.isEmpty() && state.subFolders.isEmpty() ->
                    // No reutilizar el vacío de la LISTA de carpetas: aquí
                    // "Indexa una carpeta desde la app web" no aplica.
                    EmptyState(
                        icon = PhotonneIcons.Folder,
                        title = stringResource(Res.string.folder_detail_empty_title),
                        subtitle = stringResource(Res.string.folder_detail_empty_subtitle)
                    )
                // Subcarpetas y assets comparten un único LazyVerticalGrid para que
                // haya un solo scroll.
                else -> FolderDetailGrid(
                    subFolders = state.subFolders,
                    items = state.items,
                    baseUrl = apiBaseUrl,
                    isGrid = state.viewMode == FolderViewMode.Grid,
                    selectedSubfolderId = state.selectedSubfolderId,
                    selection = state.selection,
                    gridState = gridState,
                    contentPadding = gridContentPadding,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    onSubfolderClick = onSubfolderClick,
                    onSubfolderLongPress = onSubfolderLongPress,
                    dragSelect = gestures.dragSelect,
                    modifier = Modifier.hazeSource(hazeState)
                )
            }
        }

        if (floatingChrome) {
            SubscreenFloatingChrome(
                title = title,
                onBack = onBack,
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                    firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { gridState.isScrollInProgress },
                    scrollToTopMinIndex = 6,
                    onScrollToTop = {
                        if (gridState.firstVisibleItemIndex > 24) gridState.scrollToItem(24)
                        gridState.animateScrollToItem(0)
                    }
                ),
                hazeState = hazeState,
                onChromeVisibleChange = {},
                statusBarScrim = true,
                actions = actions
            )
        }
    }
}

/**
 * One scroll for the whole folder: subfolders (as full-width rows in List mode
 * or square cards in Grid mode, mirroring the root folder screen) sit above the
 * asset thumbnails inside a single [LazyVerticalGrid]. The subfolder cards keep
 * some breathing room via their own padding while the asset cells stay tight.
 */
@Composable
private fun FolderDetailGrid(
    subFolders: List<FolderSummary>,
    items: List<TimelineItem>,
    baseUrl: String,
    isGrid: Boolean,
    selectedSubfolderId: String?,
    selection: Set<String>,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onSubfolderClick: (FolderSummary) -> Unit,
    onSubfolderLongPress: (FolderSummary) -> Unit,
    dragSelect: AssetGridDragSelect? = null,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPhotonneHaptics()
    // Las subcarpetas y el separador que las cierra van ANTES de los assets y
    // son de longitud variable, así que el desfase índice→ordinal se calcula
    // en vez de darse por hecho.
    val preludeCount = if (subFolders.isEmpty()) 0
    else subFolders.size + if (items.isNotEmpty()) 1 else 0
    // Al cerrar el visor abierto desde aquí, la última foto vista vuelve a
    // pantalla (ver ViewerReturnState).
    val viewerOrigin = com.photonne.app.ui.grid.rememberViewerReturnOrigin()
    val markViewerOrigin = com.photonne.app.ui.grid.rememberMarkViewerOrigin(viewerOrigin)
    com.photonne.app.ui.grid.ViewerReturnScrollEffect(
        token = viewerOrigin,
        gridState = gridState,
        indexOf = { id ->
            val i = items.indexOfFirst { it.id == id }
            if (i < 0) -1 else i + preludeCount
        },
        items, preludeCount
    )
    val dragSelectAdapter = rememberLazyGridDragSelectAdapter(
        gridState = gridState,
        headerCount = { preludeCount },
        idAt = { ordinal -> items.getOrNull(ordinal)?.id }
    )
    val gridModifier = if (dragSelect != null) {
        modifier.fillMaxSize().dragSelectable(
            state = dragSelect.state,
            adapter = dragSelectAdapter,
            scrollableState = gridState,
            enabled = dragSelect.enabled,
            selectionActive = selection.isNotEmpty(),
            isSelected = { it in selection },
            onPatch = dragSelect.onPatch,
            haptics = haptics,
            config = dragSelect.config.copy(
                autoScrollTopInset = contentPadding.calculateTopPadding(),
                autoScrollBottomInset = contentPadding.calculateBottomPadding()
            )
        )
    } else {
        modifier.fillMaxSize()
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        modifier = gridModifier
    ) {
        if (subFolders.isNotEmpty()) {
            if (isGrid) {
                items(
                    subFolders,
                    key = { "subfolder-${it.id}" },
                    contentType = { "subfolder-card" }
                ) { folder ->
                    FolderCard(
                        // The grid arrangement is tight (matching the asset cells); this
                        // per-cell padding gives the folder cards their own breathing room.
                        modifier = Modifier.padding(Spacing.xs),
                        folder = folder,
                        isSelected = selectedSubfolderId == folder.id,
                        onClick = { onSubfolderClick(folder) },
                        onLongPress = { onSubfolderLongPress(folder) }
                    )
                }
            } else {
                items(
                    subFolders,
                    key = { "subfolder-${it.id}" },
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = { "subfolder-row" }
                ) { folder ->
                    FolderRow(
                        folder = folder,
                        isSelected = selectedSubfolderId == folder.id,
                        onClick = { onSubfolderClick(folder) },
                        onLongPress = { onSubfolderLongPress(folder) }
                    )
                }
            }
            if (items.isNotEmpty()) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "section-divider"
                ) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                }
            }
        }
        itemsIndexed(
            items,
            key = { index, item -> assetCellKey(item, index) },
            contentType = { _, _ -> AssetCellContentType }
        ) { index, asset ->
            AssetGridCell(
                asset = asset,
                baseUrl = baseUrl,
                onClick = {
                    if (selection.isEmpty()) markViewerOrigin()
                    onItemClick(index)
                },
                // Con arrastre en banda el long-press lo posee la rejilla.
                onLongClick = if (dragSelect != null) null else ({ onItemLongClick(index) }),
                onSecondaryClick = { onItemLongClick(index) },
                isSelected = asset.id in selection
            )
        }
    }
}
