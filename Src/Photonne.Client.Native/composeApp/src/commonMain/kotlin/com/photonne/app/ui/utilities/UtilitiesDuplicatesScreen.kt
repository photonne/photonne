package com.photonne.app.ui.utilities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.utilities_duplicates_action_auto_select
import com.photonne.app.resources.utilities_duplicates_action_clear
import com.photonne.app.resources.utilities_duplicates_action_delete
import com.photonne.app.resources.utilities_duplicates_confirm_message
import com.photonne.app.resources.utilities_duplicates_confirm_title
import com.photonne.app.resources.utilities_duplicates_empty
import com.photonne.app.resources.utilities_duplicates_group_assets
import com.photonne.app.resources.utilities_duplicates_open_detail
import com.photonne.app.resources.utilities_duplicates_summary
import com.photonne.app.ui.admin.humanBytes
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun UtilitiesDuplicatesScreen(
    viewModel: UtilitiesDuplicatesViewModel,
    baseUrl: String,
    onOpenAsset: (index: Int, items: List<TimelineItem>) -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    var confirmOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        PhotonneRefreshableScreen(
            isRefreshing = state.isLoading && state.groups.isNotEmpty(),
            onRefresh = viewModel::refresh
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            state.statusMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            state.error?.userMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            when {
                state.isLoading && state.groups.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                state.groups.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.utilities_duplicates_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                else ->
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item("summary") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        stringResource(
                                            Res.string.utilities_duplicates_summary,
                                            state.groups.size,
                                            humanBytes(state.totalWastedBytes)
                                        ),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = viewModel::autoSelectKeepingBest,
                                            enabled = !state.isDeleting,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(Res.string.utilities_duplicates_action_auto_select))
                                        }
                                        OutlinedButton(
                                            onClick = viewModel::clearSelection,
                                            enabled = !state.isDeleting &&
                                                state.totalSelectedCount > 0,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(stringResource(Res.string.utilities_duplicates_action_clear))
                                        }
                                    }
                                }
                            }
                        }

                        items(state.groups, key = { it.group.hash }) { view ->
                            DuplicateGroupCard(
                                view = view,
                                baseUrl = baseUrl,
                                onToggle = { assetId ->
                                    viewModel.toggleAsset(view.group.hash, assetId)
                                },
                                onOpenAsset = { index ->
                                    onOpenAsset(index, view.group.assets)
                                }
                            )
                        }
                    }
            }
        }
        }

        if (state.totalSelectedCount > 0) {
            ExtendedFloatingActionButton(
                onClick = { confirmOpen = true },
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                text = {
                    Text(
                        stringResource(
                            Res.string.utilities_duplicates_action_delete,
                            state.totalSelectedCount,
                            humanBytes(state.totalSelectedBytes)
                        )
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text(stringResource(Res.string.utilities_duplicates_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.utilities_duplicates_confirm_message,
                        state.totalSelectedCount
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmOpen = false
                        viewModel.deleteSelected()
                    },
                    enabled = !state.isDeleting
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }, enabled = !state.isDeleting) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    view: DuplicateGroupView,
    baseUrl: String,
    onToggle: (String) -> Unit,
    onOpenAsset: (index: Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                stringResource(
                    Res.string.utilities_duplicates_group_assets,
                    view.group.assets.size,
                    humanBytes(view.group.totalSize)
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            // Each copy is rendered as a row inside the group card so
            // the full path is readable on one line of meta — and we
            // avoid nesting a scrollable grid inside the outer
            // LazyColumn.
            view.group.assets.forEachIndexed { index, asset ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                DuplicateAssetRow(
                    asset = asset,
                    baseUrl = baseUrl,
                    isSelected = asset.id in view.selectedAssetIds,
                    onToggle = { onToggle(asset.id) },
                    onOpen = { onOpenAsset(index) }
                )
            }
        }
    }
}

@Composable
private fun DuplicateAssetRow(
    asset: TimelineItem,
    baseUrl: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onToggle)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else Color.Transparent,
                    shape = RoundedCornerShape(6.dp)
                )
        ) {
            if (asset.hasThumbnails) {
                AsyncImage(
                    model = "$baseUrl/api/assets/${asset.id}/thumbnail?size=Small",
                    contentDescription = asset.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (asset.isVideo) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(2.dp)
                        .size(16.dp)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .background(Color.White, shape = CircleShape)
                        .size(18.dp)
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                asset.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                humanBytes(asset.fileSize),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(12.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    asset.fullPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        IconButton(onClick = onOpen) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(Res.string.utilities_duplicates_open_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
