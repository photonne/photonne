package com.photonne.app.ui.devicebackup

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.HourglassEmpty
import com.photonne.app.data.devicebackup.DeviceGallery
import com.photonne.app.data.devicebackup.DeviceMediaSyncState
import com.photonne.app.data.devicebackup.rememberDeviceFolderPicker
import com.photonne.app.resources.Res
import com.photonne.app.resources.backup_destination_default
import com.photonne.app.resources.backup_destination_label
import com.photonne.app.resources.background_sync_auto_hint
import com.photonne.app.resources.background_sync_auto_label
import com.photonne.app.resources.background_sync_charging_hint
import com.photonne.app.resources.background_sync_charging_label
import com.photonne.app.resources.background_sync_section
import com.photonne.app.resources.background_sync_wifi_hint
import com.photonne.app.resources.background_sync_wifi_label
import com.photonne.app.resources.enrichment_banner_action
import com.photonne.app.resources.enrichment_banner_subtitle
import com.photonne.app.resources.enrichment_banner_title
import com.photonne.app.resources.backup_disabled_hint
import com.photonne.app.resources.backup_enabled_label
import com.photonne.app.resources.backup_enabled_off
import com.photonne.app.resources.backup_enabled_on
import com.photonne.app.resources.backup_pending_count
import com.photonne.app.resources.backup_pending_none
import com.photonne.app.resources.backup_pending_title
import com.photonne.app.resources.backup_pending_unknown
import com.photonne.app.resources.backup_pending_view
import com.photonne.app.resources.backup_section_destination
import com.photonne.app.resources.backup_section_origin
import com.photonne.app.resources.backup_source_label
import com.photonne.app.resources.backup_source_none
import com.photonne.app.resources.backup_source_pick
import com.photonne.app.resources.device_backup_action_free_space
import com.photonne.app.resources.device_backup_free_space_cancel
import com.photonne.app.resources.device_backup_free_space_confirm
import com.photonne.app.resources.device_backup_free_space_dialog_message
import com.photonne.app.resources.device_backup_free_space_dialog_title
import com.photonne.app.resources.device_backup_free_space_in_progress
import com.photonne.app.resources.device_backup_not_supported
import org.jetbrains.compose.resources.stringResource

/**
 * The Backup tab's landing screen: a single column of cards covering
 * the toggle, origin folder, destination, pending status and the
 * "free up space" action. The actual file gallery (the grid view) is
 * a sub-route invoked from the pending panel.
 */
@Composable
fun BackupScreen(
    viewModel: DeviceBackupViewModel,
    enrichmentViewModel: EnrichmentStatusViewModel,
    gallery: DeviceGallery,
    onOpenPending: () -> Unit,
    onOpenEnrichment: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val enrichmentState by enrichmentViewModel.state.collectAsState()
    LaunchedEffect(state.isBackupEnabled) {
        if (state.isBackupEnabled) viewModel.ensureLoaded()
    }
    // Refresh on screen entry so the banner picks up new pending/failed tasks
    // generated by recent uploads.
    LaunchedEffect(Unit) { enrichmentViewModel.refresh() }
    val pickFolder = rememberDeviceFolderPicker(
        gallery = gallery,
        onPicked = viewModel::onFolderPicked
    )
    var showFreeSpaceConfirm by remember { mutableStateOf(false) }

    if (!state.isSupported) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(Res.string.device_backup_not_supported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val syncedCount = state.syncedCount
    val pendingCount = state.pendingEntries.size
    val hasChecked = remember(state.entries) {
        state.entries.any { it.syncState !is DeviceMediaSyncState.Unknown }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (enrichmentState.totalAssets > 0) {
            item("enrichment-banner") {
                EnrichmentBanner(
                    count = enrichmentState.totalAssets,
                    onClick = onOpenEnrichment
                )
            }
        }

        item("enable") {
            BackupToggleCard(
                enabled = state.isBackupEnabled,
                onChange = viewModel::setBackupEnabled
            )
        }

        if (!state.isBackupEnabled) {
            item("disabled-hint") {
                Text(
                    text = stringResource(Res.string.backup_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        item("origin-header") { SectionHeader(stringResource(Res.string.backup_section_origin)) }
        item("origin") {
            SettingsRow(
                icon = Icons.Filled.Folder,
                label = stringResource(Res.string.backup_source_label),
                value = state.folder?.displayName
                    ?: stringResource(Res.string.backup_source_none),
                actionLabel = if (state.folder == null)
                    stringResource(Res.string.backup_source_pick) else null,
                onClick = pickFolder
            )
        }

        item("destination-header") {
            SectionHeader(stringResource(Res.string.backup_section_destination))
        }
        item("destination") {
            SettingsRow(
                icon = Icons.Outlined.CloudUpload,
                label = stringResource(Res.string.backup_destination_label),
                value = stringResource(Res.string.backup_destination_default),
                actionLabel = null,
                onClick = null
            )
        }

        if (state.isBackupEnabled) {
            item("bg-header") {
                SectionHeader(stringResource(Res.string.background_sync_section))
            }
            item("bg-auto") {
                ToggleRow(
                    label = stringResource(Res.string.background_sync_auto_label),
                    hint = stringResource(Res.string.background_sync_auto_hint),
                    checked = state.backgroundSync.enabled,
                    onChange = viewModel::setAutoBackupEnabled
                )
            }
            // Constraints only matter when auto-sync is on — hide them to
            // avoid implying they affect manual syncs.
            if (state.backgroundSync.enabled) {
                item("bg-wifi") {
                    ToggleRow(
                        label = stringResource(Res.string.background_sync_wifi_label),
                        hint = stringResource(Res.string.background_sync_wifi_hint),
                        checked = state.backgroundSync.requireWifi,
                        onChange = viewModel::setRequireWifi
                    )
                }
                item("bg-charging") {
                    ToggleRow(
                        label = stringResource(Res.string.background_sync_charging_label),
                        hint = stringResource(Res.string.background_sync_charging_hint),
                        checked = state.backgroundSync.requireCharging,
                        onChange = viewModel::setRequireCharging
                    )
                }
            }
        }

        item("pending-header") { SectionHeader(stringResource(Res.string.backup_pending_title)) }
        item("pending") {
            PendingPanel(
                hasFolder = state.folder != null,
                hasChecked = hasChecked,
                pendingCount = pendingCount,
                onOpen = onOpenPending
            )
        }

        if (syncedCount > 0) {
            item("free-space") {
                FreeSpaceRow(
                    count = syncedCount,
                    isFreeing = state.isFreeingSpace,
                    enabled = !state.isSyncing && !state.isFreeingSpace,
                    onClick = { showFreeSpaceConfirm = true }
                )
            }
        }

        state.statusMessage?.let { msg ->
            item("status") {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        state.errorMessage?.let { msg ->
            item("error") {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }

    if (showFreeSpaceConfirm) {
        AlertDialog(
            onDismissRequest = { showFreeSpaceConfirm = false },
            icon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(Res.string.device_backup_free_space_dialog_title)) },
            text = {
                Text(stringResource(Res.string.device_backup_free_space_dialog_message, syncedCount))
            },
            confirmButton = {
                TextButton(onClick = {
                    showFreeSpaceConfirm = false
                    viewModel.freeUpSyncedSpace()
                }) {
                    Text(
                        stringResource(Res.string.device_backup_free_space_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreeSpaceConfirm = false }) {
                    Text(stringResource(Res.string.device_backup_free_space_cancel))
                }
            }
        )
    }
}

@Composable
private fun EnrichmentBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.HourglassEmpty,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.enrichment_banner_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(Res.string.enrichment_banner_subtitle, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = onClick) {
                Text(
                    stringResource(Res.string.enrichment_banner_action),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun BackupToggleCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!enabled) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconPill(icon = Icons.Filled.CloudUpload)
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.backup_enabled_label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        if (enabled) Res.string.backup_enabled_on
                        else Res.string.backup_enabled_off
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onChange(!checked) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    actionLabel: String?,
    onClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconPill(icon = icon)
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = actionLabel ?: value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PendingPanel(
    hasFolder: Boolean,
    hasChecked: Boolean,
    pendingCount: Int,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when {
                    !hasFolder || !hasChecked -> Icons.Outlined.CloudUpload to
                        MaterialTheme.colorScheme.onSurfaceVariant
                    pendingCount == 0 -> Icons.Filled.CheckCircle to
                        MaterialTheme.colorScheme.primary
                    else -> Icons.Filled.CloudUpload to MaterialTheme.colorScheme.error
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !hasFolder -> stringResource(Res.string.backup_source_none)
                            !hasChecked -> stringResource(Res.string.backup_pending_unknown)
                            pendingCount == 0 -> stringResource(Res.string.backup_pending_none)
                            else -> stringResource(
                                Res.string.backup_pending_count, pendingCount
                            )
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (hasFolder) {
                Spacer(Modifier.size(12.dp))
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.backup_pending_view))
                }
            }
        }
    }
}

@Composable
private fun FreeSpaceRow(
    count: Int,
    isFreeing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(Res.string.device_backup_action_free_space, count),
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (isFreeing) {
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
                Text(
                    text = stringResource(Res.string.device_backup_free_space_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
    )
}

@Composable
private fun IconPill(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp)
        )
    }
}
