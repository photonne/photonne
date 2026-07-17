package com.photonne.app.ui.utilities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.FolderTreeNode
import com.photonne.app.resources.Res
import com.photonne.app.resources.utilities_locations_empty
import com.photonne.app.resources.utilities_locations_external_badge
import com.photonne.app.resources.utilities_locations_item_count
import com.photonne.app.resources.utilities_locations_shared_badge
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.util.sortedByNatural
import org.jetbrains.compose.resources.stringResource

@Composable
fun UtilitiesLocationsScreen(viewModel: UtilitiesLocationsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isLoading && state.roots.isNotEmpty(),
        onRefresh = viewModel::refresh
    ) {
        when {
            state.isLoading && state.roots.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error?.userMessage != null && state.roots.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.error?.userMessage!!, color = MaterialTheme.colorScheme.error)
                }
            state.roots.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.utilities_locations_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 24.dp + floatingNavBarReservedHeight()
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.roots.sortedByNatural { it.name.ifBlank { it.path } }.forEach { root ->
                    renderFolder(
                        node = root,
                        depth = 0,
                        expanded = state.expanded,
                        onToggle = viewModel::toggle
                    )
                }
            }
        }
    }
}

/** Recursive tree renderer. We flatten the tree into LazyColumn items
 *  on the fly so each visible row can recycle properly even on very
 *  deep folder hierarchies — Compose would otherwise re-measure the
 *  whole subtree on every expand/collapse. */
private fun LazyListScope.renderFolder(
    node: FolderTreeNode,
    depth: Int,
    expanded: Set<String>,
    onToggle: (String) -> Unit
) {
    item(key = node.id) {
        FolderRow(
            node = node,
            depth = depth,
            isExpanded = node.id in expanded,
            hasChildren = node.subFolders.isNotEmpty(),
            onToggle = { onToggle(node.id) }
        )
    }
    if (node.id in expanded) {
        node.subFolders.sortedByNatural { it.name.ifBlank { it.path } }.forEach { child ->
            renderFolder(
                node = child,
                depth = depth + 1,
                expanded = expanded,
                onToggle = onToggle
            )
        }
    }
}

@Composable
private fun FolderRow(
    node: FolderTreeNode,
    depth: Int,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onToggle: () -> Unit
) {
    val indent = (depth * 16).dp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .clickable(enabled = hasChildren, onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Caret only renders when the folder has children so leaf
            // rows don't reserve dead space at the start of the row.
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                if (hasChildren) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown
                                      else Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = when {
                    node.externalLibraryId != null -> Icons.Outlined.Storage
                    node.isShared -> Icons.Outlined.FolderShared
                    else -> Icons.Filled.Folder
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.name.ifBlank { node.path },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(
                            Res.string.utilities_locations_item_count,
                            node.assetCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (node.externalLibraryId != null) {
                        Text(
                            "· " + stringResource(Res.string.utilities_locations_external_badge),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (node.isShared) {
                        Text(
                            "· " + stringResource(Res.string.utilities_locations_shared_badge),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
