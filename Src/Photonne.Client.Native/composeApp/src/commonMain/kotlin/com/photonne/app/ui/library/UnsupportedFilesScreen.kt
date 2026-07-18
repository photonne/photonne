package com.photonne.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.UnsupportedFileItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.unsupported_files_download
import com.photonne.app.resources.unsupported_files_empty_subtitle
import com.photonne.app.resources.unsupported_files_empty_title
import com.photonne.app.resources.unsupported_files_supported_types
import com.photonne.app.resources.unsupported_files_unsupported_label
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource

private const val LOAD_MORE_THRESHOLD = 6

@Composable
fun UnsupportedFilesScreen(
    state: UnsupportedFilesUiState,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onDownload: (UnsupportedFileItem) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val reservedTop = subscreenChromeReservedTop()

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isInitialLoading ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.error != null && state.items.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize().padding(top = reservedTop).padding(24.dp)) {
                        com.photonne.app.ui.error.ErrorBanner(error = state.error)
                    }
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
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, file ->
                        // Page in more rows as the user nears the end of the list.
                        if (index >= state.items.size - LOAD_MORE_THRESHOLD) {
                            LaunchedEffect(state.items.size, index) {
                                if (state.hasMore && !state.isAppending) onLoadMore()
                            }
                        }
                        UnsupportedFileRow(
                            file = file,
                            downloadEnabled = !state.isDownloading,
                            onDownload = { onDownload(file) }
                        )
                        HorizontalDivider()
                    }
                    if (state.isAppending) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
}

@Composable
private fun UnsupportedFileRow(
    file: UnsupportedFileItem,
    downloadEnabled: Boolean,
    onDownload: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = stringResource(Res.string.unsupported_files_unsupported_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(Res.string.unsupported_files_supported_types),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${file.extension} · ${formatBytes(file.fileSize)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDownload, enabled = downloadEnabled) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = stringResource(Res.string.unsupported_files_download),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = ""
    for (u in units) {
        value /= 1024.0
        unit = u
        if (value < 1024) break
    }
    return "${(value * 10).toLong() / 10.0} $unit"
}
