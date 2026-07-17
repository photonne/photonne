package com.photonne.app.ui.admin

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.admin_libraries_action_delete
import com.photonne.app.resources.admin_libraries_action_permissions
import com.photonne.app.resources.admin_libraries_action_scan
import com.photonne.app.resources.admin_libraries_asset_count
import com.photonne.app.resources.admin_libraries_empty
import com.photonne.app.resources.admin_libraries_last_scan
import com.photonne.app.resources.admin_libraries_no_scan
import com.photonne.app.resources.admin_libraries_permissions_empty
import com.photonne.app.resources.admin_libraries_permissions_title
import com.photonne.app.resources.admin_libraries_scan_progress
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLibrariesScreen(
    viewModel: AdminLibrariesViewModel,
    knownUsers: List<UserDto>,
    onEdit: (ExternalLibraryDto) -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

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
        state.scanProgress?.let { progress ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                Res.string.admin_libraries_scan_progress,
                                progress.percentage
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::cancelScan) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                    Text(progress.message, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (progress.percentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                }
            }
        }

        when {
            state.isLoading && state.libraries.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.libraries.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.admin_libraries_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            else ->
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + floatingNavBarReservedHeight()
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.libraries, key = { it.id }) { lib ->
                        LibraryCard(
                            library = lib,
                            onEdit = { onEdit(lib) },
                            onScan = { viewModel.startScan(lib.id) },
                            onPermissions = {
                                viewModel.openPermissions(lib.id, knownUsers)
                            }
                        )
                    }
                }
        }
    }

    if (state.permissionsLibraryId != null) {
        LibraryPermissionsDialog(
            permissions = state.permissions,
            candidates = state.candidateUsers,
            onGrant = viewModel::grantPermission,
            onRevoke = viewModel::revokePermission,
            onDismiss = viewModel::closePermissions
        )
    }
}

@Composable
private fun LibraryCard(
    library: ExternalLibraryDto,
    onEdit: () -> Unit,
    onScan: () -> Unit,
    onPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(library.name, style = MaterialTheme.typography.titleMedium)
            Text(
                library.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onEdit,
                    label = {
                        Text(
                            stringResource(
                                Res.string.admin_libraries_asset_count,
                                library.assetCount
                            )
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                library.cronSchedule?.takeIf { it.isNotBlank() }?.let {
                    AssistChip(
                        onClick = onEdit,
                        label = { Text(it) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
            Text(
                text = library.lastScannedAt?.let {
                    stringResource(
                        Res.string.admin_libraries_last_scan,
                        isoDateOnly(it) ?: it,
                        library.lastScanStatus.orEmpty()
                    )
                } ?: stringResource(Res.string.admin_libraries_no_scan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onScan, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.admin_libraries_action_scan))
                }
                OutlinedButton(onClick = onPermissions, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.admin_libraries_action_permissions))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPermissionsDialog(
    permissions: List<com.photonne.app.data.models.LibraryPermissionDto>,
    candidates: List<UserDto>,
    onGrant: (userId: String) -> Unit,
    onRevoke: (userId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.admin_libraries_permissions_title),
                style = MaterialTheme.typography.titleLarge
            )
            Column(
                modifier = Modifier.heightIn(min = 140.dp, max = 420.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (permissions.isEmpty()) {
                    Text(
                        stringResource(Res.string.admin_libraries_permissions_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    permissions.forEach { perm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(perm.username, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    perm.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onRevoke(perm.userId) }) {
                                Text(stringResource(Res.string.admin_libraries_action_delete))
                            }
                        }
                    }
                }
                val availableCandidates = candidates.filter { user ->
                    permissions.none { it.userId == user.id }
                }
                if (availableCandidates.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    Text("+", style = MaterialTheme.typography.titleSmall)
                    availableCandidates.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onGrant(user.id) })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(user.username, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_close))
                }
            }
        }
    }
}
