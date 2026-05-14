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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import com.photonne.app.resources.utilities_duplicates_summary
import com.photonne.app.ui.admin.humanBytes
import org.jetbrains.compose.resources.stringResource

@Composable
fun UtilitiesDuplicatesScreen(
    viewModel: UtilitiesDuplicatesViewModel,
    baseUrl: String
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    var confirmOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
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
            state.errorMessage?.let { msg ->
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
                                }
                            )
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
    onToggle: (String) -> Unit
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        // Cap the inner grid so the outer LazyColumn keeps
                        // controlling scroll; a fixed cap keeps mixed group
                        // sizes from making the screen jumpy.
                        when {
                            view.group.assets.size <= 3 -> 110.dp
                            view.group.assets.size <= 6 -> 220.dp
                            else -> 330.dp
                        }
                    )
            ) {
                items(view.group.assets, key = { it.id }) { asset ->
                    DuplicateAssetCell(
                        asset = asset,
                        baseUrl = baseUrl,
                        isSelected = asset.id in view.selectedAssetIds,
                        onClick = { onToggle(asset.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateAssetCell(
    asset: TimelineItem,
    baseUrl: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
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
                    .padding(4.dp)
                    .size(20.dp)
            )
        }
        // Asset size badge — helps the user pick which copy to keep at
        // a glance without opening the viewer.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                humanBytes(asset.fileSize),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                        shape = RoundedCornerShape(6.dp)
                    )
            )
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.White, shape = CircleShape)
                    .size(20.dp)
            )
        }
    }
}
