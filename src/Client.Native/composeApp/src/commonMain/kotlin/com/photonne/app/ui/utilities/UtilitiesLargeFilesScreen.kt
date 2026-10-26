package com.photonne.app.ui.utilities

import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.CheckCircle
import com.photonne.app.resources.action_undo
import com.photonne.app.resources.selection_action_trash
import com.photonne.app.resources.selection_deleted_permanently_done
import com.photonne.app.resources.selection_trash_done
import com.photonne.app.resources.trash_disabled_delete_confirm
import com.photonne.app.resources.trash_disabled_delete_message
import com.photonne.app.resources.trash_disabled_delete_title
import com.photonne.app.resources.utilities_duplicates_confirm_title
import com.photonne.app.resources.utilities_duplicates_confirm_message
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.main.LocalSnackbarController
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.IconSize
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.resources.Res
import com.photonne.app.resources.utilities_large_files_count_label
import com.photonne.app.resources.utilities_large_files_empty
import com.photonne.app.resources.utilities_large_files_total
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.format.humanBytes
import com.photonne.app.ui.theme.ListRowsSkeleton
import com.photonne.app.ui.theme.Spacing

@Composable
fun UtilitiesLargeFilesScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: UtilitiesLargeFilesViewModel,
    baseUrl: String,
    onAssetClick: (index: Int, items: List<TimelineItem>) -> Unit,
    /** Restores from the trash the ids the undo just sent there. */
    onUndoTrash: (List<String>) -> Unit = {},
    /**
     * The trash confirmation is open. It lives in the host because the button
     * that opens it is the confirm capsule in place of the nav, as in Duplicados.
     */
    confirmOpen: Boolean = false,
    onConfirmOpenChange: (Boolean) -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val density = LocalDensity.current
    var headerHeightPx by remember { mutableStateOf(0) }
    val headerReserve = with(density) { headerHeightPx.toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        PhotonneRefreshableScreen(
            indicatorTopPadding = reservedTop,
            isRefreshing = state.isLoading && state.items.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                state.isLoading && state.items.isEmpty() ->
                    ListRowsSkeleton(contentPadding = PaddingValues(top = reservedTop))
                // Lote L10: un fallo de carga no es "no hay archivos grandes".
                state.error != null && state.items.isEmpty() ->
                    com.photonne.app.ui.error.FullScreenError(
                        error = state.error,
                        onRetry = viewModel::refresh,
                        modifier = Modifier.padding(top = headerReserve)
                    )
                state.items.isEmpty() ->
                    EmptyState(
                        icon = Icons.Outlined.Storage,
                        title = stringResource(Res.string.utilities_large_files_empty)
                    )
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp + headerReserve,
                        bottom = 24.dp + floatingNavBarReservedHeight()
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                ) {
                    item("total") {
                        Text(
                            stringResource(
                                Res.string.utilities_large_files_total,
                                state.items.size,
                                humanBytes(state.totalBytes)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.xs)
                        )
                    }
                    itemsIndexed(
                        items = state.items,
                        key = { _, item -> item.id }
                    ) { index, item ->
                        LargeFileRow(
                            modifier = Modifier.animateItem(),
                            item = item,
                            baseUrl = baseUrl,
                            isSelected = item.id in state.selection,
                            // While choosing, a tap marks; otherwise it opens.
                            // A long press starts choosing, as in the grids.
                            onClick = {
                                if (state.isSelectionActive) viewModel.toggle(item.id)
                                else onAssetClick(index, state.items)
                            },
                            onLongClick = { viewModel.toggle(item.id) }
                        )
                    }
                }
            }
        }
        // Cabecera fija por debajo del cromo: su alto se reserva en el
        // contentPadding de la lista (headerReserve) para que el contenido pase
        // por debajo del cromo y esmerile, con el filtro siempre visible.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
        ) {
            Spacer(Modifier.height(reservedTop))
            CountFilterRow(
                current = state.count,
                options = UtilitiesLargeFilesUiState.CountOptions,
                onSelect = viewModel::setCount
            )
            // Con la lista en pantalla el error va en banner; sin ella ya lo
            // enseña el error a pantalla completa de arriba.
            ErrorBanner(
                error = state.error?.takeIf { state.items.isNotEmpty() },
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 6.dp),
                onRetry = viewModel::refresh
            )
        }
        // While choosing, the selection capsule (painted by the host) takes its place.
        if (!state.isSelectionActive) {
            SubscreenFloatingChrome(
                title = title,
                onBack = onBack,
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                    firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { listState.isScrollInProgress },
                    scrollToTopMinIndex = 4,
                    onScrollToTop = { listState.animateScrollToItem(0) }
                ),
                hazeState = hazeState,
                onChromeVisibleChange = onChromeVisibleChange
            )
        }
    }

    LargeFilesTrashConfirm(
        open = confirmOpen,
        state = state,
        viewModel = viewModel,
        onOpenChange = onConfirmOpenChange,
        onUndoTrash = onUndoTrash
    )
}

/** Trash (or delete, with the trash off) what is marked, with undo. */
@Composable
private fun LargeFilesTrashConfirm(
    open: Boolean,
    state: UtilitiesLargeFilesUiState,
    viewModel: UtilitiesLargeFilesViewModel,
    onOpenChange: (Boolean) -> Unit,
    onUndoTrash: (List<String>) -> Unit
) {
    var confirmError by remember { mutableStateOf<String?>(null) }
    val snackbar = LocalSnackbarController.current
    val count = state.selection.size
    val undoLabel = stringResource(Res.string.action_undo)
    val trashEnabled = com.photonne.app.ui.actions.rememberServerTrashEnabled()
    val doneMessage = pluralStringResource(
        if (trashEnabled) Res.plurals.selection_trash_done
        else Res.plurals.selection_deleted_permanently_done,
        count,
        count
    )
    if (!open) return
    ConfirmActionDialog(
        title = stringResource(
            if (trashEnabled) Res.string.utilities_duplicates_confirm_title
            else Res.string.trash_disabled_delete_title
        ),
        message = pluralStringResource(
            if (trashEnabled) Res.plurals.utilities_duplicates_confirm_message
            else Res.plurals.trash_disabled_delete_message,
            count,
            count
        ),
        confirmLabel = stringResource(
            if (trashEnabled) Res.string.selection_action_trash
            else Res.string.trash_disabled_delete_confirm
        ),
        isDestructive = true,
        isSubmitting = state.isDeleting,
        errorMessage = confirmError,
        onDismiss = {
            onOpenChange(false)
            confirmError = null
        },
        onConfirm = {
            confirmError = null
            viewModel.deleteSelected { deleted, error ->
                if (error != null) {
                    confirmError = error.userMessage
                } else {
                    onOpenChange(false)
                    if (trashEnabled) snackbar?.show(doneMessage, undoLabel) { onUndoTrash(deleted) }
                    else snackbar?.show(doneMessage)
                }
            }
        }
    )
}

@Composable
private fun CountFilterRow(
    current: Int,
    options: List<Int>,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            stringResource(Res.string.utilities_large_files_count_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.forEach { option ->
            FilterChip(
                selected = current == option,
                onClick = { onSelect(option) },
                label = { Text(option.toString()) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LargeFileRow(
    item: TimelineItem,
    baseUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CardDefaults.shape
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (item.hasThumbnails) {
                    AsyncImage(
                        model = "$baseUrl/api/assets/${item.id}/thumbnail?size=Small",
                        contentDescription = item.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (item.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(Spacing.xxs)
                            .size(IconSize.sm)
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Spacing.xxs)
                            .background(Color.White, shape = CircleShape)
                            .size(IconSize.chip)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    humanBytes(item.fileSize),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    item.fullPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

