package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.photonne.app.data.models.TrashUserStat
import com.photonne.app.ui.theme.actionButtonHeight
import kotlinx.coroutines.launch
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_settings_trash_enabled
import com.photonne.app.resources.admin_settings_trash_max_quota
import com.photonne.app.resources.admin_settings_trash_max_quota_hint
import com.photonne.app.resources.admin_settings_trash_retention
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

class AdminTrashSettingsViewModel(
    private val repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        "TrashSettings.Enabled",
        "TrashSettings.RetentionDays",
        "TrashSettings.MaxQuotaMb"
    )

    override val defaults = mapOf(
        "TrashSettings.Enabled" to "true",
        "TrashSettings.RetentionDays" to "30",
        "TrashSettings.MaxQuotaMb" to "0"
    )

    override fun normalize(key: String, value: String): String = when (key) {
        "TrashSettings.RetentionDays", "TrashSettings.MaxQuotaMb" -> value.filter { it.isDigit() }
        else -> value
    }

    // Stats are loaded separately and held on the side; reuses the
    // same screen so the admin can read live trash usage and tweak
    // retention next to it without flipping subpages.
    private val _trashStats =
        kotlinx.coroutines.flow.MutableStateFlow(AdminTrashSideState())
    val trashStats: kotlinx.coroutines.flow.StateFlow<AdminTrashSideState> = _trashStats

    fun loadStats() {
        if (_trashStats.value.isLoading) return
        _trashStats.value = _trashStats.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { repository.getTrashStats() }
                .onSuccess { stats ->
                    _trashStats.value =
                        _trashStats.value.copy(stats = stats, isLoading = false)
                }
                .onFailure { error ->
                    _trashStats.value = _trashStats.value.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Could not load trash stats"
                    )
                }
        }
    }

    fun cleanupExpired() {
        if (_trashStats.value.isCleaning) return
        _trashStats.value = _trashStats.value.copy(isCleaning = true, errorMessage = null)
        viewModelScope.launch {
            runCatching { repository.cleanupExpiredTrash() }
                .onSuccess { result ->
                    _trashStats.value = _trashStats.value.copy(
                        isCleaning = false,
                        statusMessage = result.message
                            .ifBlank { "Removed ${result.deleted} items" }
                    )
                    loadStats()
                }
                .onFailure { error ->
                    _trashStats.value = _trashStats.value.copy(
                        isCleaning = false,
                        errorMessage = error.message ?: "Cleanup failed"
                    )
                }
        }
    }
}

data class AdminTrashSideState(
    val stats: com.photonne.app.data.models.TrashStatsResponse? = null,
    val isLoading: Boolean = false,
    val isCleaning: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

@Composable
fun AdminTrashSettingsScreen(viewModel: AdminTrashSettingsViewModel) {
    val settings by viewModel.state.collectAsState()
    val stats by viewModel.trashStats.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.loadStats()
    }

    if (settings.isLoading && settings.original.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(Res.string.admin_settings_trash_section_config),
            style = MaterialTheme.typography.titleSmall
        )
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_trash_enabled),
            checked = settings.get("TrashSettings.Enabled").equals("true", true)
        ) { viewModel.set("TrashSettings.Enabled", if (it) "true" else "false") }
        SettingNumberField(
            stringResource(Res.string.admin_settings_trash_retention),
            settings.get("TrashSettings.RetentionDays")
        ) { viewModel.set("TrashSettings.RetentionDays", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_trash_max_quota),
            settings.get("TrashSettings.MaxQuotaMb"),
            supporting = stringResource(Res.string.admin_settings_trash_max_quota_hint)
        ) { viewModel.set("TrashSettings.MaxQuotaMb", it) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (settings.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
            }
            settings.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            }
            settings.successMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            }
            Button(
                onClick = viewModel::save,
                enabled = settings.canSave,
                modifier = Modifier.actionButtonHeight()
            ) {
                Text(stringResource(Res.string.action_save))
            }
        }

        HorizontalDivider()

        Text(
            stringResource(Res.string.admin_settings_trash_section_stats),
            style = MaterialTheme.typography.titleSmall
        )

        stats.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        stats.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        val s = stats.stats
        if (s != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TwoColumn(stringResource(Res.string.admin_trash_total_items), s.totalItems.toString())
                    TwoColumn(stringResource(Res.string.admin_trash_total_bytes), humanBytes(s.totalBytes))
                    TwoColumn(stringResource(Res.string.admin_trash_expired), s.expiredItems.toString())
                    TwoColumn(stringResource(Res.string.admin_trash_over_quota_users), s.overQuotaUsers.toString())
                    TwoColumn(stringResource(Res.string.admin_trash_over_quota_bytes), humanBytes(s.overQuotaBytes))
                }
            }
            if (s.perUser.isNotEmpty()) {
                Text(
                    stringResource(Res.string.admin_trash_per_user_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Spacer(Modifier.size(12.dp))
                }
                OutlinedButton(
                    onClick = viewModel::cleanupExpired,
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
}

@Composable
private fun TwoColumn(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
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
