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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.ExternalLibraryDto
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_libraries_action_delete
import com.photonne.app.resources.admin_libraries_action_new
import com.photonne.app.resources.admin_libraries_action_permissions
import com.photonne.app.resources.admin_libraries_action_scan
import com.photonne.app.resources.admin_libraries_asset_count
import com.photonne.app.resources.admin_libraries_cron_hint
import com.photonne.app.resources.admin_libraries_delete_message
import com.photonne.app.resources.admin_libraries_delete_title
import com.photonne.app.resources.admin_libraries_edit_title
import com.photonne.app.resources.admin_libraries_empty
import com.photonne.app.resources.admin_libraries_field_cron
import com.photonne.app.resources.admin_libraries_field_import_subfolders
import com.photonne.app.resources.admin_libraries_field_name
import com.photonne.app.resources.admin_libraries_field_path
import com.photonne.app.resources.admin_libraries_last_scan
import com.photonne.app.resources.admin_libraries_no_scan
import com.photonne.app.resources.admin_libraries_permissions_empty
import com.photonne.app.resources.admin_libraries_permissions_title
import com.photonne.app.resources.admin_libraries_scan_progress
import org.jetbrains.compose.resources.stringResource

private sealed class LibraryDialog {
    object Create : LibraryDialog()
    data class Edit(val lib: ExternalLibraryDto) : LibraryDialog()
    data class Delete(val lib: ExternalLibraryDto) : LibraryDialog()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLibrariesScreen(
    viewModel: AdminLibrariesViewModel,
    knownUsers: List<UserDto>
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }

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
                            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.libraries, key = { it.id }) { lib ->
                            LibraryCard(
                                library = lib,
                                onEdit = { dialog = LibraryDialog.Edit(lib) },
                                onDelete = { dialog = LibraryDialog.Delete(lib) },
                                onScan = { viewModel.startScan(lib.id) },
                                onPermissions = {
                                    viewModel.openPermissions(lib.id, knownUsers)
                                }
                            )
                        }
                    }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { dialog = LibraryDialog.Create },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(Res.string.admin_libraries_action_new)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }

    when (val current = dialog) {
        null -> Unit
        LibraryDialog.Create -> LibraryFormDialog(
            existing = null,
            isSubmitting = state.isMutating,
            errorMessage = state.errorMessage,
            onDismiss = {
                dialog = null
                viewModel.clearMessages()
            },
            onConfirm = { name, path, importSub, cron ->
                viewModel.create(name, path, importSub, cron) { dialog = null }
            }
        )
        is LibraryDialog.Edit -> LibraryFormDialog(
            existing = current.lib,
            isSubmitting = state.isMutating,
            errorMessage = state.errorMessage,
            onDismiss = {
                dialog = null
                viewModel.clearMessages()
            },
            onConfirm = { name, path, importSub, cron ->
                viewModel.update(current.lib.id, name, path, importSub, cron) { dialog = null }
            }
        )
        is LibraryDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(Res.string.admin_libraries_delete_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.admin_libraries_delete_message,
                        current.lib.name
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.delete(current.lib.id) { dialog = null } },
                    enabled = !state.isMutating
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }, enabled = !state.isMutating) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
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
    onDelete: () -> Unit,
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
                OutlinedButton(onClick = onDelete) {
                    Text(stringResource(Res.string.admin_libraries_action_delete))
                }
            }
        }
    }
}

@Composable
private fun LibraryFormDialog(
    existing: ExternalLibraryDto?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, path: String, importSubfolders: Boolean, cron: String?) -> Unit
) {
    val isEdit = existing != null
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var path by remember(existing?.id) { mutableStateOf(existing?.path.orEmpty()) }
    var importSubfolders by remember(existing?.id) {
        mutableStateOf(existing?.importSubfolders ?: true)
    }
    var cron by remember(existing?.id) { mutableStateOf(existing?.cronSchedule.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isEdit) Res.string.admin_libraries_edit_title
                    else Res.string.admin_libraries_action_new
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.admin_libraries_field_name)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(Res.string.admin_libraries_field_path)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_libraries_field_import_subfolders),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = importSubfolders,
                        onCheckedChange = { importSubfolders = it },
                        enabled = !isSubmitting
                    )
                }
                OutlinedTextField(
                    value = cron,
                    onValueChange = { cron = it },
                    label = { Text(stringResource(Res.string.admin_libraries_field_cron)) },
                    singleLine = true,
                    enabled = !isSubmitting,
                    supportingText = {
                        Text(stringResource(Res.string.admin_libraries_cron_hint))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && name.isNotBlank() && path.isNotBlank(),
                onClick = {
                    onConfirm(name, path, importSubfolders, cron.takeIf { it.isNotBlank() })
                }
            ) {
                Text(
                    stringResource(
                        if (isEdit) Res.string.action_save else Res.string.action_create
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

@Composable
private fun LibraryPermissionsDialog(
    permissions: List<com.photonne.app.data.models.LibraryPermissionDto>,
    candidates: List<UserDto>,
    onGrant: (userId: String) -> Unit,
    onRevoke: (userId: String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.admin_libraries_permissions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                Text(stringResource(Res.string.action_delete))
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_close))
            }
        }
    )
}
