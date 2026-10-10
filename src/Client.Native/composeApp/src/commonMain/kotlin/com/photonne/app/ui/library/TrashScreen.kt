package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_more
import com.photonne.app.resources.filters_action_active
import com.photonne.app.resources.filters_scope_label
import com.photonne.app.resources.filters_title
import com.photonne.app.resources.trash_action_empty
import com.photonne.app.resources.trash_action_restore_all
import com.photonne.app.resources.trash_empty_subtitle
import com.photonne.app.resources.trash_empty_title
import com.photonne.app.resources.trash_tab_personal
import com.photonne.app.resources.trash_tab_shared
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.PhotoGridScrubberOverlay
import com.photonne.app.ui.grid.rememberAssetGridSelectionGestures
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.theme.FieldGroupLabel
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.util.SegmentOption
import com.photonne.app.ui.util.SegmentedChoiceRow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/** Tabs of the unified Trash screen: the user's own trash and the shared-folder trash. */
enum class TrashTab { Personal, Shared }

/**
 * Ámbito de la papelera (Personal / Compartida) en una hoja de filtros, igual
 * que el ámbito de Álbumes y Carpetas: un [SegmentedChoiceRow] tras el botón de
 * filtros de la cápsula, no una barra de pestañas acoplada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScopeSheet(
    selected: TrashTab,
    onSelect: (TrashTab) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            SheetHeader(stringResource(Res.string.filters_title))
            FieldGroupLabel(stringResource(Res.string.filters_scope_label))
            SegmentedChoiceRow(
                options = listOf(
                    SegmentOption(TrashTab.Personal, stringResource(Res.string.trash_tab_personal)),
                    SegmentOption(TrashTab.Shared, stringResource(Res.string.trash_tab_shared))
                ),
                selected = selected,
                onSelect = onSelect
            )
        }
    }
}

/**
 * Acciones de la cápsula de la papelera: el filtro de ámbito y, en la personal
 * con elementos, el ⋮ con las acciones sobre toda la papelera (las mismas que
 * "Desarchivar todo" en Archivados: raras y masivas, por eso en el menú).
 */
@Composable
fun RowScope.TrashChromeActions(
    tab: TrashTab,
    showBulkActions: Boolean,
    onOpenScope: () -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    val scopeActive = tab != TrashTab.Personal
    IconButton(onClick = onOpenScope) {
        Icon(
            imageVector = if (scopeActive) PhotonneIcons.FilterActive else PhotonneIcons.Filter,
            contentDescription = stringResource(
                if (scopeActive) Res.string.filters_action_active else Res.string.filters_title
            ),
            tint = if (scopeActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
    }
    if (showBulkActions) {
        var menuOpen by rememberSaveable { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(PhotonneIcons.More, contentDescription = stringResource(Res.string.action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.trash_action_restore_all)) },
                    leadingIcon = { Icon(PhotonneIcons.Restore, contentDescription = null) },
                    onClick = { menuOpen = false; onRestoreAll() }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.trash_action_empty),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            PhotonneIcons.DeletePermanent,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = { menuOpen = false; onEmptyTrash() }
                )
            }
        }
    }
}

@Composable
fun TrashScreen(
    state: TrashUiState,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onApplySelection: (SelectionPatch) -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
    hazeState: HazeState = remember { HazeState() }
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val gestures = rememberAssetGridSelectionGestures(onApplySelection)

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading ->
                    AssetGridSkeleton(
                        contentPadding = PaddingValues(
                            bottom = floatingNavBarReservedHeight()
                        )
                    )
                state.error != null && state.items.isEmpty() ->
                    com.photonne.app.ui.error.FullScreenError(
                        error = state.error,
                        onRetry = onRefresh
                    )
                state.isEmpty ->
                    EmptyState(
                        icon = PhotonneIcons.Delete,
                        title = stringResource(Res.string.trash_empty_title),
                        subtitle = stringResource(Res.string.trash_empty_subtitle)
                    )
                else -> AssetGrid(
                    items = state.items,
                    baseUrl = apiBaseUrl,
                    gridState = gridState,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    selectedIds = state.selection,
                    hasMore = state.hasMore,
                    isAppending = state.isAppending,
                    isInitialLoading = state.isInitialLoading,
                    onLoadMore = onLoadMore,
                    dragSelect = gestures.dragSelect,
                    contentPadding = PaddingValues(bottom = floatingNavBarReservedHeight()),
                    modifier = Modifier.fillMaxWidth().hazeSource(hazeState)
                )
            }

            // El host ya reserva el top (barra de pestañas + cromo), así que aquí
            // el overlay arranca en 0.
            PhotoGridScrubberOverlay(
                gridState = gridState,
                items = state.items,
                reservedTop = 0.dp,
                reservedBottom = floatingNavBarReservedHeight(),
                selectionActive = state.isSelectionActive,
                hazeState = hazeState,
            )
        }
    }
}
