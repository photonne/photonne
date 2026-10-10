package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.photonne.app.ui.format.humanBytes
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.remember
import com.photonne.app.resources.unsupported_files_title
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.UnsupportedFileItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.unsupported_files_delete_confirm_message
import com.photonne.app.resources.unsupported_files_delete_confirm_title
import com.photonne.app.resources.unsupported_files_download
import com.photonne.app.resources.unsupported_files_empty_subtitle
import com.photonne.app.resources.unsupported_files_empty_title
import com.photonne.app.resources.unsupported_files_subtitle
import com.photonne.app.resources.unsupported_files_supported_types
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.ListRowsSkeleton
import com.photonne.app.ui.theme.Spacing

private const val LOAD_MORE_THRESHOLD = 6

@Composable
fun UnsupportedFilesScreen(
    state: UnsupportedFilesUiState,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDownload: (UnsupportedFileItem) -> Unit,
    /** Borra el archivo del servidor; llama al callback cuando queda borrado. */
    onDelete: (UnsupportedFileItem, onDeleted: () -> Unit) -> Unit,
    onClearDeleteError: () -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val reservedTop = subscreenChromeReservedTop()
    var deleting by remember { mutableStateOf<UnsupportedFileItem?>(null) }

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        indicatorTopPadding = reservedTop,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading ->
                    ListRowsSkeleton(contentPadding = PaddingValues(top = reservedTop))
                state.error != null && state.items.isEmpty() ->
                    com.photonne.app.ui.error.FullScreenError(
                        error = state.error,
                        onRetry = onRefresh,
                        modifier = Modifier.padding(top = reservedTop)
                    )
                state.isEmpty ->
                    EmptyState(
                        icon = Icons.Outlined.FolderOff,
                        title = stringResource(Res.string.unsupported_files_empty_title),
                        subtitle = stringResource(Res.string.unsupported_files_empty_subtitle)
                    )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    contentPadding = PaddingValues(
                        top = reservedTop,
                        bottom = floatingNavBarReservedHeight()
                    )
                ) {
                    // Qué es la lista y qué sí se admite, una vez arriba en vez
                    // de repetido en cada fila.
                    item("intro") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        ) {
                            Text(
                                text = stringResource(Res.string.unsupported_files_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(Res.string.unsupported_files_supported_types),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider()
                    }
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, file ->
                        // Page in more rows as the user nears the end of the list.
                        if (index >= state.items.size - LOAD_MORE_THRESHOLD) {
                            LaunchedEffect(state.items.size, index) {
                                if (state.hasMore && !state.isAppending) onLoadMore()
                            }
                        }
                        UnsupportedFileRow(
                            modifier = Modifier.animateItem(),
                            file = file,
                            downloadEnabled = !state.isDownloading,
                            onDownload = { onDownload(file) },
                            onDelete = { deleting = file }.takeIf { file.canDelete }
                        )
                        HorizontalDivider()
                    }
                    if (state.isAppending) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                    }
                }
            }

            SubscreenFloatingChrome(
                title = stringResource(Res.string.unsupported_files_title),
                onBack = onBack,
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                    firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { listState.isScrollInProgress },
                    scrollToTopMinIndex = 4,
                    onScrollToTop = {
                        if (listState.firstVisibleItemIndex > 10) {
                            listState.scrollToItem(10)
                        }
                        listState.animateScrollToItem(0)
                    }
                ),
                hazeState = hazeState,
                onChromeVisibleChange = onChromeVisibleChange
            )
        }
    }

    deleting?.let { file ->
        // Diálogo estándar: espera al servidor y enseña el fallo sin cerrarse.
        ConfirmActionDialog(
            title = stringResource(Res.string.unsupported_files_delete_confirm_title),
            message = stringResource(Res.string.unsupported_files_delete_confirm_message, file.fileName),
            confirmLabel = stringResource(Res.string.action_delete),
            isDestructive = true,
            isSubmitting = state.isDeleting,
            errorMessage = state.deleteError?.userMessage,
            onDismiss = {
                deleting = null
                onClearDeleteError()
            },
            onConfirm = { onDelete(file) { deleting = null } }
        )
    }
}

@Composable
private fun UnsupportedFileRow(
    file: UnsupportedFileItem,
    downloadEnabled: Boolean,
    onDownload: () -> Unit,
    /** Null cuando el usuario no puede borrar el archivo: no se pinta el botón. */
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // La ruta entera, sin recortar a una línea: es lo que hace falta
            // para ir a buscar el archivo y arreglarlo o borrarlo.
            Text(
                text = file.fullPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${file.extension} · ${humanBytes(file.fileSize)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDownload, enabled = downloadEnabled) {
            Icon(
                imageVector = PhotonneIcons.Download,
                contentDescription = stringResource(Res.string.unsupported_files_download),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = PhotonneIcons.Delete,
                    contentDescription = stringResource(Res.string.action_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

