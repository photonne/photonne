package com.photonne.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
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
import com.photonne.app.resources.admin_libraries_action_new
import com.photonne.app.resources.admin_libraries_action_permissions
import com.photonne.app.resources.admin_libraries_action_scan
import com.photonne.app.resources.admin_libraries_asset_count
import com.photonne.app.resources.admin_libraries_empty
import com.photonne.app.resources.admin_libraries_last_scan
import com.photonne.app.resources.admin_libraries_no_scan
import com.photonne.app.resources.admin_libraries_permissions_empty
import com.photonne.app.resources.admin_libraries_permissions_title
import com.photonne.app.resources.admin_libraries_scan_progress
import com.photonne.app.ui.main.CreateAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.photonne.app.data.error.UiError
import com.photonne.app.data.models.LibraryScanProgress
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.resources.admin_libraries_scan_cancel
import com.photonne.app.resources.admin_libraries_permissions_revoke
import com.photonne.app.resources.admin_libraries_permissions_add
import com.photonne.app.resources.admin_libraries_scan_starting
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLibrariesScreen(
    title: String,
    onBack: () -> Unit,
    onCreateNew: () -> Unit,
    viewModel: AdminLibrariesViewModel,
    knownUsers: List<UserDto>,
    onEdit: (ExternalLibraryDto) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    val scan = state.scanProgress
    AdminListScaffold(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        isLoading = state.isLoading,
        isEmpty = state.libraries.isEmpty(),
        error = state.error,
        onRefresh = viewModel::refresh,
        emptyIcon = Icons.Outlined.CreateNewFolder,
        emptyTitle = stringResource(Res.string.admin_libraries_empty),
        resultMessage = state.statusMessage,
        onResultShown = viewModel::consumeStatus,
        onDismissError = viewModel::clearMessages,
        actions = {
            CreateAction(
                icon = Icons.Outlined.CreateNewFolder,
                contentDescription = stringResource(Res.string.admin_libraries_action_new),
                onClick = onCreateNew
            )
        },
        // First row of the list rather than a strip above it: up there it sat
        // behind the chrome, cancel button and all.
        header = if (scan != null) {
            { item(key = "scan-progress") { ScanProgressCard(scan, onCancel = viewModel::cancelScan) } }
        } else null
    ) {
        items(state.libraries, key = { it.id }) { lib ->
            LibraryCard(
                library = lib,
                scanEnabled = state.scanningLibraryId == null,
                onEdit = { onEdit(lib) },
                onScan = { viewModel.startScan(lib.id) },
                onPermissions = { viewModel.openPermissions(lib.id, knownUsers) }
            )
        }
    }

    if (state.permissionsLibraryId != null) {
        LibraryPermissionsDialog(
            permissions = state.permissions,
            candidates = state.candidateUsers,
            isLoading = state.permissionsLoading,
            error = state.permissionsError,
            busyUserIds = state.permissionsBusy,
            onGrant = viewModel::grantPermission,
            onRevoke = viewModel::revokePermission,
            onDismiss = viewModel::closePermissions
        )
    }
}

@Composable
private fun ScanProgressCard(progress: LibraryScanProgress, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.admin_libraries_scan_progress, progress.percentage),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.admin_libraries_scan_cancel)
                    )
                }
            }
            Text(
                progress.message.ifBlank { stringResource(Res.string.admin_libraries_scan_starting) },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(Spacing.xs))
            LinearProgressIndicator(
                progress = { (progress.percentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
        }
    }
}

@Composable
private fun LibraryCard(
    library: ExternalLibraryDto,
    scanEnabled: Boolean,
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
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(library.name, style = MaterialTheme.typography.titleMedium)
            Text(
                library.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricPill(stringResource(Res.string.admin_libraries_asset_count, library.assetCount))
                library.cronSchedule?.takeIf { it.isNotBlank() }?.let {
                    MetricPill(it)
                }
            }
            Text(
                text = library.lastScannedAt?.let {
                    stringResource(
                        Res.string.admin_libraries_last_scan,
                        adminDate(it) ?: it,
                        library.lastScanStatus.orEmpty()
                    )
                } ?: stringResource(Res.string.admin_libraries_no_scan),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                OutlinedButton(onClick = onScan, enabled = scanEnabled, modifier = Modifier.weight(1f)) {
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
    isLoading: Boolean,
    error: UiError?,
    busyUserIds: Set<String>,
    onGrant: (userId: String) -> Unit,
    onRevoke: (userId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                stringResource(Res.string.admin_libraries_permissions_title),
                style = MaterialTheme.typography.titleLarge
            )
            // The sheet's own errors: the list's banner is behind it.
            ErrorBanner(error = error)
            // Scrolls: capped at 420 dp without it, a long user list was cut off.
            Column(
                modifier = Modifier
                    .heightIn(min = 140.dp, max = 420.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                when {
                    // Not "Solo tú tienes acceso" while it is still unknown.
                    isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    permissions.isEmpty() && error == null -> Text(
                        stringResource(Res.string.admin_libraries_permissions_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> permissions.forEach { perm ->
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
                            TextButton(
                                onClick = { onRevoke(perm.userId) },
                                enabled = perm.userId !in busyUserIds
                            ) {
                                Text(stringResource(Res.string.admin_libraries_permissions_revoke))
                            }
                        }
                    }
                }
                val availableCandidates = candidates.filter { user ->
                    permissions.none { it.userId == user.id }
                }
                if (!isLoading && availableCandidates.isNotEmpty()) {
                    SettingSectionHeader(stringResource(Res.string.admin_libraries_permissions_add))
                    availableCandidates.forEach { user ->
                        val busy = user.id in busyUserIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy, onClick = { onGrant(user.id) })
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.size(Spacing.sm))
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
