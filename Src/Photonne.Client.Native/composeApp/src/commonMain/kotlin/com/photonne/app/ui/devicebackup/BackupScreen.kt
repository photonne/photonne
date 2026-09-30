package com.photonne.app.ui.devicebackup

import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.ProgressHeight
import com.photonne.app.ui.theme.SectionHeader
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.SettingsItem
import com.photonne.app.ui.theme.SettingsTrailing
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.HourglassEmpty
import com.photonne.app.data.devicebackup.DeviceGallery
import com.photonne.app.data.devicebackup.DeviceMediaSyncState
import com.photonne.app.data.devicebackup.rememberDeviceFolderPicker
import com.photonne.app.data.devicebackup.rememberNotificationPermission
import com.photonne.app.resources.backup_notifications_allow
import com.photonne.app.resources.backup_notifications_denied_hint
import com.photonne.app.resources.backup_blocked_quota_title
import com.photonne.app.resources.backup_blocked_quota_title_sized
import com.photonne.app.resources.backup_blocked_quota_body
import com.photonne.app.resources.backup_blocked_session_title
import com.photonne.app.resources.backup_blocked_session_body
import com.photonne.app.resources.Res
import com.photonne.app.resources.background_sync_auto_hint
import com.photonne.app.resources.background_sync_auto_label
import com.photonne.app.resources.background_sync_charging_hint
import com.photonne.app.resources.background_sync_charging_label
import com.photonne.app.resources.background_sync_wifi_hint
import com.photonne.app.resources.background_sync_wifi_label
import com.photonne.app.resources.backup_notifications_open_settings
import com.photonne.app.resources.backup_turbo_hint
import com.photonne.app.resources.backup_turbo_label
import com.photonne.app.resources.backup_disabled_hint
import com.photonne.app.resources.backup_last_run_background
import com.photonne.app.resources.backup_last_run_counts
import com.photonne.app.resources.backup_last_run_manual
import com.photonne.app.resources.backup_status_all_synced
import com.photonne.app.resources.backup_status_enrichment_row
import com.photonne.app.resources.backup_status_failures
import com.photonne.app.resources.backup_status_pending_sized
import com.photonne.app.resources.backup_status_recheck
import com.photonne.app.resources.backup_status_stop
import com.photonne.app.resources.backup_status_syncing
import com.photonne.app.resources.backup_status_upload_now
import com.photonne.app.resources.backup_status_verifying
import com.photonne.app.resources.backup_status_verifying_progress
import com.photonne.app.resources.backup_time_days
import com.photonne.app.resources.backup_time_hours
import com.photonne.app.resources.backup_time_just_now
import com.photonne.app.resources.backup_time_minutes
import com.photonne.app.resources.backup_enabled_label
import com.photonne.app.resources.backup_enabled_off
import com.photonne.app.resources.backup_enabled_on
import com.photonne.app.resources.backup_pending_unknown
import com.photonne.app.resources.backup_pending_view
import com.photonne.app.resources.backup_section_origin
import com.photonne.app.resources.backup_source_none
import com.photonne.app.resources.backup_source_pick
import com.photonne.app.resources.device_backup_action_free_space_sized
import com.photonne.app.resources.device_backup_free_space_blocked_unverified
import com.photonne.app.resources.device_backup_free_space_blocked_uploading
import com.photonne.app.resources.device_backup_free_space_blocked_verifying
import com.photonne.app.resources.device_backup_free_space_confirm
import com.photonne.app.resources.device_backup_free_space_dialog_message
import com.photonne.app.resources.device_backup_free_space_dialog_title
import com.photonne.app.resources.device_backup_not_supported
import androidx.compose.foundation.lazy.rememberLazyListState
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.format.humanBytes
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.PhotonneColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.time.Clock
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.saveable.rememberSaveable
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.resources.backup_section_settings
import com.photonne.app.resources.backup_status_ignored_row
import com.photonne.app.resources.backup_status_waiting_charging
import com.photonne.app.resources.backup_status_waiting_wifi
import com.photonne.app.resources.backup_status_waiting_wifi_charging
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import com.photonne.app.data.devicelibrary.DeviceBucket
import com.photonne.app.data.devicelibrary.DeviceLibrary
import com.photonne.app.resources.backup_bucket_added
import com.photonne.app.resources.backup_bucket_item_count
import com.photonne.app.resources.backup_bucket_other_folder
import com.photonne.app.resources.backup_bucket_picker_hint
import com.photonne.app.resources.backup_bucket_picker_title
import com.photonne.app.resources.backup_source_add
import com.photonne.app.resources.action_undo
import com.photonne.app.resources.backup_source_remove
import com.photonne.app.resources.backup_source_removed
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.SheetHeader

/**
 * The Backup tab's landing screen. Order matters here: the master switch, then
 * the status card that answers "am I backed up?", then the source folder, and
 * only then the collapsed tuning settings. It used to be the other way round,
 * with the status buried under every toggle.
 *
 * The actual file gallery (the grid view) is a sub-route reached from the card.
 */
@Composable
fun BackupScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: DeviceBackupViewModel,
    enrichmentViewModel: EnrichmentStatusViewModel,
    gallery: DeviceGallery,
    onOpenPending: () -> Unit,
    onOpenEnrichment: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val enrichmentState by enrichmentViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isBackupEnabled) {
        if (state.isBackupEnabled) viewModel.ensureLoaded()
    }
    // Refresh on screen entry so the banner picks up new pending/failed tasks
    // generated by recent uploads.
    LaunchedEffect(Unit) { enrichmentViewModel.refresh() }
    val addBackupSource = rememberBackupSourcePicker(
        gallery = gallery,
        viewModel = viewModel,
        addedUris = remember(state.folders) { state.folders.mapTo(HashSet()) { it.uri } }
    )
    // Android 13+ suppresses the worker's progress/failure notifications without
    // this grant, so ask exactly when the user opts into backups.
    val notifications = rememberNotificationPermission()
    var showFreeSpaceConfirm by remember { mutableStateOf(false) }
    // Free-space over MediaStore-backed entries (bucket sources) rides the
    // SYSTEM's permanent-delete consent flow — a bare delete is rejected for
    // media the app doesn't own. The launched uris wait here for the result.
    var pendingFreeUris by remember { mutableStateOf<List<String>>(emptyList()) }
    val freeSpaceDeleter = com.photonne.app.data.devicelibrary.rememberDeviceMediaDeleter { ok ->
        if (ok) viewModel.applyFreedDeviceUris(pendingFreeUris)
        pendingFreeUris = emptyList()
    }
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }

    // Feedback goes through the app-wide snackbar like everywhere else, instead
    // of the loose lines of text this screen used to append to the list.
    val snackbar = LocalSnackbarController.current
    LaunchedEffect(state.statusMessage, state.error) {
        val message = state.statusMessage ?: state.error?.userMessage
        if (message != null) {
            snackbar?.show(message)
            viewModel.clearMessages()
        }
    }

    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
    if (!state.isSupported) {
        Box(
            modifier = Modifier.fillMaxSize().padding(Spacing.xxl),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(Res.string.device_backup_not_supported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {

    val syncedCount = state.syncedCount
    val pendingCount = state.pendingEntries.size
    val hasChecked = remember(state.entries) {
        state.entries.any { it.syncState !is DeviceMediaSyncState.Unknown }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        contentPadding = PaddingValues(top = 16.dp + reservedTop, bottom = 16.dp + floatingNavBarReservedHeight()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item("enable") {
            SettingsGroup(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                SettingsItem(
                    headline = stringResource(Res.string.backup_enabled_label),
                    supporting = stringResource(
                        if (state.isBackupEnabled) Res.string.backup_enabled_on
                        else Res.string.backup_enabled_off
                    ),
                    leadingIcon = Icons.Filled.CloudUpload,
                    trailing = SettingsTrailing.Toggle(
                        checked = state.isBackupEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setBackupEnabled(enabled)
                            if (enabled) notifications.request()
                        }
                    )
                )
            }
        }

        if (!state.isBackupEnabled) {
            item("disabled-hint") {
                Text(
                    text = stringResource(Res.string.backup_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
        }

        // El aviso de permiso denegado vivía dentro de la sección "Ajustes",
        // plegada por defecto: quien más lo necesitaba no lo veía nunca.
        if (state.isBackupEnabled && state.backgroundSync.enabled &&
            !notifications.isGranted
        ) {
            item("notifications-denied") {
                NotificationsDeniedCard(
                    onOpenSettings = notifications.openSystemSettings,
                    onRequest = notifications.request
                )
            }
        }

        // Lote L11: la pasada se cortó por la cuenta (cuota llena, sesión
        // caducada). Va antes del estado: explica los fallos que éste cuenta.
        val passBlock = state.passBlock
        if (state.isBackupEnabled && passBlock != null) {
            item("pass-blocked") { BackupBlockedCard(passBlock) }
        }

        // The answer to "am I backed up?" comes first. It used to sit at the
        // bottom, below every setting — the one thing people open this screen
        // for was the last thing they saw.
        if (state.isBackupEnabled) {
            item("status") {
                BackupStatusCard(
                    state = state,
                    hasChecked = hasChecked,
                    pendingCount = pendingCount,
                    enrichmentCount = enrichmentState.totalAssets,
                    onUploadNow = viewModel::syncAllPending,
                    onOpenPending = onOpenPending,
                    onOpenEnrichment = onOpenEnrichment,
                    onRecheck = { viewModel.refreshSyncStates() },
                    onStop = viewModel::stopCurrentPass,
                    onFreeSpace = { showFreeSpaceConfirm = true }
                )
            }
        }

        item("origin-header") {
            SectionHeader(
                text = stringResource(Res.string.backup_section_origin),
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )
        }
        // One row per folder: a phone's photos live in Camera, WhatsApp,
        // Screenshots and Downloads at once, and picking a new one used to
        // replace the previous. All of them, plus "add", in one group: the
        // folder name is the row's title (it used to sit under a small
        // "Carpeta del dispositivo" caption, the hierarchy upside down).
        item("origin") {
            val removedTemplate = Res.string.backup_source_removed
            val undoLabel = stringResource(Res.string.action_undo)
            SettingsGroup(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                state.folders.forEachIndexed { index, folder ->
                    val removedMessage = stringResource(removedTemplate, folder.displayName)
                    SettingsItem(
                        headline = folder.displayName,
                        leadingIcon = Icons.Filled.Folder,
                        headlineMaxLines = 1,
                        showDivider = index > 0,
                        trailing = SettingsTrailing.Custom {
                            IconButton(onClick = {
                                // Reversible: quitarlo no borra nada del servidor, así
                                // que snackbar con Deshacer en lugar de confirmación.
                                // El registro de la carpeta (veredictos, "Omitidos")
                                // solo se borra cuando caduca el Deshacer.
                                viewModel.removeFolder(folder.uri)
                                if (snackbar == null) {
                                    viewModel.commitFolderRemoval(folder.uri)
                                } else {
                                    snackbar.show(
                                        message = removedMessage,
                                        actionLabel = undoLabel,
                                        onAction = { viewModel.onFolderPicked(folder) },
                                        onDismissed = { viewModel.commitFolderRemoval(folder.uri) }
                                    )
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(Res.string.backup_source_remove),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
                }
                SettingsItem(
                    headline = stringResource(
                        if (state.folders.isEmpty()) Res.string.backup_source_pick
                        else Res.string.backup_source_add
                    ),
                    supporting = if (state.folders.isEmpty())
                        stringResource(Res.string.backup_source_none) else null,
                    leadingIcon = Icons.Outlined.CreateNewFolder,
                    onClick = addBackupSource,
                    showDivider = state.folders.isNotEmpty()
                )
            }
        }

        if (state.isBackupEnabled) {
            // Collapsed by default: these are tuning knobs, not the daily
            // question. The destination row that used to live here is gone —
            // it was never tappable and always read "Photonne".
            item("settings-header") {
                CollapsibleHeader(
                    title = stringResource(Res.string.backup_section_settings),
                    expanded = settingsExpanded,
                    onToggle = { settingsExpanded = !settingsExpanded }
                )
            }
            if (settingsExpanded) {
                item("settings") {
                    SettingsGroup(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        // Turbo comes first because it tunes BOTH manual and background
                        // passes (it widens the upload fan-out), not just scheduled ones.
                        SettingsItem(
                            headline = stringResource(Res.string.backup_turbo_label),
                            supporting = stringResource(Res.string.backup_turbo_hint),
                            trailing = SettingsTrailing.Toggle(state.backgroundSync.turbo, viewModel::setTurbo)
                        )
                        SettingsItem(
                            headline = stringResource(Res.string.background_sync_auto_label),
                            supporting = stringResource(Res.string.background_sync_auto_hint),
                            trailing = SettingsTrailing.Toggle(
                                state.backgroundSync.enabled,
                                viewModel::setAutoBackupEnabled
                            ),
                            showDivider = true
                        )
                        // Constraints only matter when auto-sync is on — hide them to
                        // avoid implying they affect manual syncs.
                        if (state.backgroundSync.enabled) {
                            SettingsItem(
                                headline = stringResource(Res.string.background_sync_wifi_label),
                                supporting = stringResource(Res.string.background_sync_wifi_hint),
                                trailing = SettingsTrailing.Toggle(
                                    state.backgroundSync.requireWifi,
                                    viewModel::setRequireWifi
                                ),
                                showDivider = true
                            )
                            SettingsItem(
                                headline = stringResource(Res.string.background_sync_charging_label),
                                supporting = stringResource(Res.string.background_sync_charging_hint),
                                trailing = SettingsTrailing.Toggle(
                                    state.backgroundSync.requireCharging,
                                    viewModel::setRequireCharging
                                ),
                                showDivider = true
                            )
                        }
                    }
                }
            }
        }

    }

    if (showFreeSpaceConfirm) {
        com.photonne.app.ui.library.ConfirmActionDialog(
            title = stringResource(Res.string.device_backup_free_space_dialog_title),
            message = pluralStringResource(Res.plurals.device_backup_free_space_dialog_message, syncedCount, syncedCount),
            confirmLabel = stringResource(Res.string.device_backup_free_space_confirm),
            isDestructive = true,
            isSubmitting = false,
            onDismiss = { showFreeSpaceConfirm = false },
            onConfirm = {
                showFreeSpaceConfirm = false
                // MediaStore-backed entries → system consent flow;
                // SAF/PhotoKit entries → the in-process path as always.
                val deviceUris = viewModel.syncedMediaStoreUris()
                if (deviceUris.isNotEmpty()) {
                    pendingFreeUris = deviceUris
                    freeSpaceDeleter(deviceUris)
                }
                viewModel.freeUpSyncedSpace()
            }
        )
    }

    }
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

/**
 * Single source of truth for "how is my backup doing?". One card, one
 * mental model: verifying → uploading → (errors | pending | all backed
 * up), plus the last completed pass and the server-side enrichment
 * queue as secondary rows. Replaces the old PendingPanel + separate
 * enrichment banner.
 */
@Composable
private fun BackupStatusCard(
    state: DeviceBackupUiState,
    hasChecked: Boolean,
    pendingCount: Int,
    enrichmentCount: Int,
    onUploadNow: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenEnrichment: () -> Unit,
    onRecheck: () -> Unit,
    onStop: () -> Unit,
    onFreeSpace: () -> Unit
) {
    val hasFolder = state.folders.isNotEmpty()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
        ) {
            // ── Headline: the one-line verdict ──────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when {
                    !hasFolder -> Icons.Outlined.CloudUpload to
                        MaterialTheme.colorScheme.onSurfaceVariant
                    state.isSyncing || state.isCheckingHashes ->
                        Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.primary
                    state.failedCount > 0 -> Icons.Filled.CloudUpload to
                        MaterialTheme.colorScheme.error
                    pendingCount > 0 -> Icons.Filled.CloudUpload to
                        PhotonneColors.warning
                    hasChecked && state.ignoredCount == 0 -> Icons.Filled.CheckCircle to
                        PhotonneColors.success
                    hasChecked -> Icons.Filled.CloudUpload to
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else -> Icons.Outlined.CloudUpload to
                        MaterialTheme.colorScheme.onSurfaceVariant
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
                            state.isSyncing -> stringResource(
                                Res.string.backup_status_syncing,
                                (state.syncProgress?.completed ?: 0) +
                                    (state.syncProgress?.skipped ?: 0) +
                                    (state.syncProgress?.failed ?: 0),
                                state.syncProgress?.total ?: 0
                            )
                            state.isCheckingHashes -> state.hashProgress?.let {
                                if (it.hashTotal > 0) stringResource(
                                    Res.string.backup_status_verifying_progress,
                                    it.hashedCount, it.hashTotal
                                ) else stringResource(Res.string.backup_status_verifying)
                            } ?: stringResource(Res.string.backup_status_verifying)
                            state.failedCount > 0 -> pluralStringResource(Res.plurals.backup_status_failures, state.failedCount, state.failedCount)
                            pendingCount > 0 -> stringResource(
                                Res.string.backup_status_pending_sized,
                                pendingCount,
                                humanBytes(state.pendingBytes)
                            )
                            // "All backed up" is only true if nothing was
                            // skipped: saying it with 300 skipped files sitting
                            // there is the one lie this screen must never tell.
                            hasChecked && state.ignoredCount == 0 ->
                                stringResource(Res.string.backup_status_all_synced)
                            hasChecked -> stringResource(
                                Res.string.backup_status_ignored_row, state.ignoredCount
                            )
                            else -> stringResource(Res.string.backup_pending_unknown)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    state.lastRun?.let { run ->
                        Text(
                            text = stringResource(
                                if (run.background) Res.string.backup_last_run_background
                                else Res.string.backup_last_run_manual,
                                relativeTimeLabel(run.finishedAtMillis)
                            ) + if (run.uploaded > 0 || run.failed > 0) {
                                " · " + stringResource(
                                    Res.string.backup_last_run_counts,
                                    run.uploaded, run.failed
                                )
                            } else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Refresh icon sits next to the count — it's what re-checks the
                // pending/synced verdict (re-hash changed files + ask the server),
                // not an upload or a navigation. Icon-only keeps the headline
                // uncluttered. Hidden while it's already running.
                if (hasFolder && !state.isCheckingHashes && !state.isSyncing) {
                    IconButton(onClick = onRecheck) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(Res.string.backup_status_recheck),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ── Activity bar while verifying / uploading ────────────────
            if (state.isCheckingHashes || state.isSyncing) {
                Spacer(Modifier.size(12.dp))
                val progress = when {
                    // Byte-weighted while uploading, so the bar doesn't stall on
                    // a single big video and then leap through 300 photos.
                    state.isSyncing && state.syncProgress != null ->
                        state.syncProgress.fraction
                    state.isCheckingHashes && (state.hashProgress?.hashTotal ?: 0) > 0 ->
                        state.hashProgress!!.hashedCount.toFloat() / state.hashProgress!!.hashTotal
                    else -> null
                }
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(ProgressHeight.hero)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(ProgressHeight.hero)
                    )
                }
            }

            // ── Why nothing is happening right now ──────────────────────
            // Auto-sync defaults to Wi-Fi AND charging, so an unplugged phone
            // simply never syncs. The screen used to say nothing about it.
            val waitingReason = waitingReasonLabel(state, pendingCount)
            if (waitingReason != null) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = waitingReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Skipped files (secondary) ───────────────────────────────
            // Only when something else already owns the headline; otherwise
            // the headline itself reports them.
            if (state.ignoredCount > 0 && (pendingCount > 0 || state.failedCount > 0)) {
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPending),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            Res.string.backup_status_ignored_row, state.ignoredCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Server-side enrichment queue (secondary) ────────────────
            if (enrichmentCount > 0) {
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenEnrichment),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.HourglassEmpty,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = pluralStringResource(Res.plurals.backup_status_enrichment_row, enrichmentCount, enrichmentCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Actions ─────────────────────────────────────────────────
            // Stacked, full-width: "Subir ahora" is the primary action and
            // "Ver pendientes" the secondary navigation, so the focus is clear.
            if (hasFolder) {
                Spacer(Modifier.size(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // A running pass can always be stopped — verification of a
                    // huge folder and a multi-gigabyte batch alike used to be
                    // unstoppable short of killing the app.
                    if (state.canStopCurrentPass) {
                        OutlinedButton(
                            onClick = onStop,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.backup_status_stop))
                        }
                    } else if (pendingCount > 0 && !state.isSyncing && !state.isCheckingHashes) {
                        com.photonne.app.ui.theme.PrimaryActionButton(
                            label = stringResource(Res.string.backup_status_upload_now),
                            onClick = onUploadNow
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenPending,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.backup_pending_view))
                    }
                    // Reclaiming space belongs with the backup verdict that
                    // makes it safe, not as a loose destructive row at the very
                    // bottom of the screen.
                    if (state.syncedCount > 0) {
                        val freeSpaceBlock = state.freeSpaceBlock
                        TextButton(
                            onClick = onFreeSpace,
                            enabled = freeSpaceBlock == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(
                                    Res.string.device_backup_action_free_space_sized,
                                    state.syncedCount,
                                    humanBytes(state.syncedBytes)
                                ),
                                color = if (freeSpaceBlock == null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                        // Disabled without a word read as broken: say why.
                        val blockedReason = when (freeSpaceBlock) {
                            FreeSpaceBlock.Verifying ->
                                stringResource(Res.string.device_backup_free_space_blocked_verifying)
                            FreeSpaceBlock.Uploading ->
                                stringResource(Res.string.device_backup_free_space_blocked_uploading)
                            FreeSpaceBlock.NotVerified ->
                                stringResource(Res.string.device_backup_free_space_blocked_unverified)
                            FreeSpaceBlock.Freeing, null -> null
                        }
                        if (blockedReason != null) {
                            Text(
                                text = blockedReason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)
                            )
                        }
                    }
                    if (state.isFreeingSpace) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(ProgressHeight.inline)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Explains why a pending backlog isn't moving: auto-sync is on but its
 * constraints (Wi-Fi, charging) aren't met yet. Null when there's nothing
 * pending, a pass is already running, or auto-sync is off — in that last case
 * nothing is waiting, the user just has to press the button.
 */
@Composable
private fun waitingReasonLabel(state: DeviceBackupUiState, pendingCount: Int): String? {
    if (pendingCount == 0) return null
    if (state.isSyncing || state.isCheckingHashes) return null
    val prefs = state.backgroundSync
    if (!prefs.enabled) return null
    return when {
        prefs.requireWifi && prefs.requireCharging ->
            stringResource(Res.string.backup_status_waiting_wifi_charging)
        prefs.requireWifi -> stringResource(Res.string.backup_status_waiting_wifi)
        prefs.requireCharging -> stringResource(Res.string.backup_status_waiting_charging)
        else -> null
    }
}

/** Section header that folds its block away. Backup settings start collapsed. */
@Composable
private fun CollapsibleHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics { heading() }
            .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Misma tipografía que [SectionHeader]: es la cabecera de sección
        // de "Ajustes de la copia", solo que se pliega.
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.md)
        )
    }
}

/** "hace 5 min" / "5 min ago" style label for the last-run timestamp. */
@Composable
private fun relativeTimeLabel(epochMillis: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val minutes = ((now - epochMillis) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(Res.string.backup_time_just_now)
        minutes < 60 -> stringResource(Res.string.backup_time_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(Res.string.backup_time_hours, (minutes / 60).toInt())
        else -> pluralStringResource(Res.plurals.backup_time_days, (minutes / (60 * 24)).toInt(), (minutes / (60 * 24)).toInt())
    }
}

/**
 * Añadir un origen de copia, igual desde Copia de seguridad que desde el vacío
 * de Pendientes (Lote N5): antes Pendientes abría el selector SAF del sistema
 * y Backup el de MediaStore, y el mismo gesto daba dos experiencias distintas.
 *
 * The backup source is a COPY policy over the same library the timeline
 * shows: where the platform lists buckets (Android + media permission),
 * adding an origin opens an in-app picker over MediaStore's folders —
 * no SAF tree dance, and the resulting refs enumerate at index speed.
 * No buckets (iOS's single Camera Roll, permission missing) falls back
 * to the classic platform folder picker.
 *
 * Devuelve la acción de "añadir origen"; la hoja de buckets la pinta él mismo.
 */
@Composable
internal fun rememberBackupSourcePicker(
    gallery: DeviceGallery,
    viewModel: DeviceBackupViewModel,
    addedUris: Set<String>
): () -> Unit {
    val pickFolder = rememberDeviceFolderPicker(
        gallery = gallery,
        onPicked = viewModel::onFolderPicked
    )
    val deviceLibrary: DeviceLibrary = koinInject()
    val pickerScope = rememberCoroutineScope()
    var bucketChoices by remember { mutableStateOf<List<DeviceBucket>?>(null) }
    bucketChoices?.let { buckets ->
        DeviceBucketPickerSheet(
            buckets = buckets,
            addedUris = addedUris,
            onAdd = { bucket -> viewModel.onFolderPicked(bucket.toFolderRef()) },
            // Folders MediaStore doesn't index (.nomedia trees, nested SAF
            // sources) stay reachable through the classic picker.
            onPickOther = {
                bucketChoices = null
                pickFolder()
            },
            onDismiss = { bucketChoices = null }
        )
    }
    return {
        pickerScope.launch {
            val buckets = runCatching { deviceLibrary.listBuckets() }.getOrDefault(emptyList())
            if (buckets.isEmpty()) pickFolder() else bucketChoices = buckets
        }
    }
}

/**
 * In-app backup-source picker over the device library's buckets
 * (Camera, WhatsApp Images, Screenshots…), largest first. Tapping a
 * row adds it as an origin and the sheet stays open so several can be
 * added in one visit; already-added buckets show a check instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceBucketPickerSheet(
    buckets: List<DeviceBucket>,
    addedUris: Set<String>,
    onAdd: (DeviceBucket) -> Unit,
    onPickOther: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = Spacing.xl)
        ) {
            item("header") {
                SheetHeader(
                    title = stringResource(Res.string.backup_bucket_picker_title),
                    subtitle = stringResource(Res.string.backup_bucket_picker_hint),
                    modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm)
                )
            }
            items(buckets, key = { "bucket-${it.id}" }) { bucket ->
                val added = bucket.toFolderRef().uri in addedUris
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !added) { onAdd(bucket) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            bucket.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            pluralStringResource(Res.plurals.backup_bucket_item_count, bucket.itemCount, bucket.itemCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (added) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(Res.string.backup_bucket_added),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item("pick-other") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPickOther)
                        .padding(horizontal = Spacing.lg, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(16.dp))
                    Text(
                        stringResource(Res.string.backup_bucket_other_folder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Lote L11: por qué la copia está parada cuando la culpa no es de los
 * archivos. Con cuota llena enseña el uso y la cuota que dio el servidor.
 */
@Composable
private fun BackupBlockedCard(block: com.photonne.app.data.devicebackup.BackupPassBlock) {
    val isQuota = block.reason == com.photonne.app.data.devicebackup.UploadFailureReason.QuotaExceeded
    val used = block.usedBytes
    val quota = block.quotaBytes
    val title = when {
        !isQuota -> stringResource(Res.string.backup_blocked_session_title)
        used != null && quota != null -> stringResource(
            Res.string.backup_blocked_quota_title_sized, humanBytes(used), humanBytes(quota)
        )
        else -> stringResource(Res.string.backup_blocked_quota_title)
    }
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = stringResource(
                    if (isQuota) Res.string.backup_blocked_quota_body
                    else Res.string.backup_blocked_session_body
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Aviso prominente de permiso de notificaciones denegado, con la salida real:
 * pedirlo (si el sistema aún deja) o abrir los ajustes de la app.
 */
@Composable
private fun NotificationsDeniedCard(
    onOpenSettings: (() -> Unit)?,
    onRequest: () -> Unit
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(Res.string.backup_notifications_denied_hint),
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (onOpenSettings != null) {
                    TextButton(onClick = onOpenSettings) {
                        Text(
                            stringResource(Res.string.backup_notifications_open_settings),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                TextButton(onClick = onRequest) {
                    Text(
                        stringResource(Res.string.backup_notifications_allow),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
