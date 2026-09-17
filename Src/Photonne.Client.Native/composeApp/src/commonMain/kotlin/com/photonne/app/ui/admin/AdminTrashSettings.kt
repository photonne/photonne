package com.photonne.app.ui.admin

import com.photonne.app.ui.theme.Spacing
import com.photonne.app.resources.admin_trash_error_stats
import com.photonne.app.resources.admin_trash_cleanup_done_format
import com.photonne.app.resources.admin_trash_error_cleanup
import org.jetbrains.compose.resources.getString
import com.photonne.app.data.error.UiError
import com.photonne.app.ui.error.ErrorBanner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.models.TrashUserStat
import kotlinx.coroutines.launch
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_trash_enabled
import com.photonne.app.resources.admin_settings_trash_max_quota
import com.photonne.app.resources.admin_settings_trash_max_quota_hint
import com.photonne.app.resources.admin_settings_trash_retention
import com.photonne.app.resources.admin_settings_trash_retention_hint
import com.photonne.app.resources.admin_settings_trash_section_config
import com.photonne.app.resources.admin_settings_trash_section_stats
import com.photonne.app.resources.admin_trash_action_cleanup
import com.photonne.app.resources.admin_trash_expired
import com.photonne.app.resources.admin_trash_per_user_expired
import com.photonne.app.resources.admin_trash_per_user_title
import com.photonne.app.resources.admin_trash_over_quota_bytes
import com.photonne.app.resources.admin_trash_over_quota_users
import com.photonne.app.resources.admin_trash_total_bytes
import com.photonne.app.resources.admin_trash_total_items
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.format.humanBytes

class AdminTrashSettingsViewModel(
    private val repository: AdminRepository,
    private val errorFactory: UiErrorFactory,
) : AdminKeyValueSettingsViewModel(repository, errorFactory) {

    override val keys = listOf("TrashSettings.Enabled", RETENTION_KEY, MAX_QUOTA_KEY)

    override val defaults = mapOf(
        "TrashSettings.Enabled" to "true",
        "TrashSettings.RetentionDays" to "30",
        "TrashSettings.MaxQuotaMb" to "0"
    )

    override val intRanges = mapOf(
        RETENTION_KEY to 0..3650,
        MAX_QUOTA_KEY to 0..100_000_000,
    )

    // Stats are loaded separately and held on the side; reuses the
    // same screen so the admin can read live trash usage and tweak
    // retention next to it without flipping subpages.
    private val _trashStats =
        kotlinx.coroutines.flow.MutableStateFlow(AdminTrashSideState())
    val trashStats: kotlinx.coroutines.flow.StateFlow<AdminTrashSideState> = _trashStats

    companion object {
        const val RETENTION_KEY = "TrashSettings.RetentionDays"
        const val MAX_QUOTA_KEY = "TrashSettings.MaxQuotaMb"
    }

    fun loadStats() {
        if (_trashStats.value.isLoading) return
        _trashStats.value = _trashStats.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.getTrashStats() }
                .onSuccess { stats ->
                    _trashStats.value =
                        _trashStats.value.copy(stats = stats, isLoading = false)
                }
                .onFailure { error ->
                    _trashStats.value = _trashStats.value.copy(
                        isLoading = false,
                        error = errorFactory.from(error, getString(Res.string.admin_trash_error_stats))
                    )
                }
        }
    }

    fun consumeCleanupStatus() {
        _trashStats.value = _trashStats.value.copy(statusMessage = null)
    }

    fun cleanupExpired() {
        if (_trashStats.value.isCleaning) return
        _trashStats.value = _trashStats.value.copy(isCleaning = true, error = null)
        viewModelScope.launch {
            runCatching { repository.cleanupExpiredTrash() }
                .onSuccess { result ->
                    _trashStats.value = _trashStats.value.copy(
                        isCleaning = false,
                        statusMessage = getString(Res.string.admin_trash_cleanup_done_format, result.deleted)
                    )
                    loadStats()
                }
                .onFailure { error ->
                    _trashStats.value = _trashStats.value.copy(
                        isCleaning = false,
                        error = errorFactory.from(error, getString(Res.string.admin_trash_error_cleanup))
                    )
                }
        }
    }
}

data class AdminTrashSideState(
    val stats: com.photonne.app.data.models.TrashStatsResponse? = null,
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false,
    val error: UiError? = null,
    val statusMessage: String? = null
)

@Composable
fun AdminTrashSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminTrashSettingsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val settings by viewModel.state.collectAsState()
    val stats by viewModel.trashStats.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadStats()
    }

    AdminSettingsForm(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        state = settings,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onSavedShown = viewModel::consumeSaved,
        onDismissError = viewModel::dismissError,
        footer = {
            AdminResultSnackbar(stats.statusMessage, viewModel::consumeCleanupStatus)
            TrashUsageSection(stats, viewModel::cleanupExpired)
        },
    ) {
        SettingSectionHeader(stringResource(Res.string.admin_settings_trash_section_config), divider = false)
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_trash_enabled),
            checked = settings.bool("TrashSettings.Enabled")
        ) { viewModel.setBool("TrashSettings.Enabled", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_trash_retention),
            settings.get(AdminTrashSettingsViewModel.RETENTION_KEY),
            supporting = stringResource(Res.string.admin_settings_trash_retention_hint),
            range = viewModel.intRanges[AdminTrashSettingsViewModel.RETENTION_KEY]
        ) { viewModel.set(AdminTrashSettingsViewModel.RETENTION_KEY, it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_trash_max_quota),
            settings.get(AdminTrashSettingsViewModel.MAX_QUOTA_KEY),
            supporting = stringResource(Res.string.admin_settings_trash_max_quota_hint),
            range = viewModel.intRanges[AdminTrashSettingsViewModel.MAX_QUOTA_KEY]
        ) { viewModel.set(AdminTrashSettingsViewModel.MAX_QUOTA_KEY, it) }
    }
}

/** Live trash usage and the manual cleanup, under the form: read next to the
 *  retention it depends on, but not something Save touches. */
@Composable
private fun TrashUsageSection(stats: AdminTrashSideState, onCleanup: () -> Unit) {
    SettingSectionHeader(stringResource(Res.string.admin_settings_trash_section_stats))

    ErrorBanner(error = stats.error)

    val s = stats.stats
    if (s != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AdminKeyValueRow(stringResource(Res.string.admin_trash_total_items), formatCount(s.totalItems))
                AdminKeyValueRow(stringResource(Res.string.admin_trash_total_bytes), humanBytes(s.totalBytes))
                AdminKeyValueRow(stringResource(Res.string.admin_trash_expired), formatCount(s.expiredItems))
                AdminKeyValueRow(stringResource(Res.string.admin_trash_over_quota_users), formatCount(s.overQuotaUsers))
                AdminKeyValueRow(stringResource(Res.string.admin_trash_over_quota_bytes), humanBytes(s.overQuotaBytes))
            }
        }
        if (s.perUser.isNotEmpty()) {
            SettingSectionHeader(stringResource(Res.string.admin_trash_per_user_title), divider = false)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    s.perUser.forEach { user -> PerUserRow(user) }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stats.isCleaning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(Spacing.md))
            }
            OutlinedButton(
                onClick = onCleanup,
                enabled = !stats.isCleaning && (s.expiredItems > 0)
            ) {
                Text(stringResource(Res.string.admin_trash_action_cleanup))
            }
        }
    } else if (stats.isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PerUserRow(stat: TrashUserStat) {
    val accent = if (stat.overQuota) MaterialTheme.colorScheme.error else Color.Unspecified
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stat.username,
                style = MaterialTheme.typography.bodyMedium,
                color = accent
            )
            if (stat.expiredItems > 0) {
                Text(
                    stringResource(Res.string.admin_trash_per_user_expired, stat.expiredItems),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "${stat.items} · ${humanBytes(stat.bytes)}",
            style = MaterialTheme.typography.titleMedium,
            color = accent
        )
    }
}
