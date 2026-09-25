package com.photonne.app.ui.admin

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.ui.theme.PrimaryActionButton
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.resources.admin_backup_error_download
import org.jetbrains.compose.resources.getString
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.ui.error.ErrorBanner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.AssetContentBytes
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_backup_action_download
import com.photonne.app.resources.admin_backup_downloaded
import com.photonne.app.resources.admin_backup_explanation
import com.photonne.app.resources.admin_backup_level_config
import com.photonne.app.resources.admin_backup_level_config_desc
import com.photonne.app.resources.admin_backup_level_essential
import com.photonne.app.resources.admin_backup_level_essential_desc
import com.photonne.app.resources.admin_backup_level_full
import com.photonne.app.resources.admin_backup_level_full_desc
import com.photonne.app.resources.admin_backup_media_warning
import com.photonne.app.resources.admin_backup_restore_only_pwa
import com.photonne.app.resources.admin_backup_section_download
import com.photonne.app.resources.admin_backup_section_restore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class BackupLevel(val wireValue: String) {
    Config("config"),
    Essential("essential"),
    Full("full"),
}

data class AdminBackupUiState(
    val level: BackupLevel = BackupLevel.Essential,
    val isDownloading: Boolean = false,
    val downloadedTo: String? = null,
    val error: UiError? = null
)

class AdminBackupViewModel(
    private val repository: AdminRepository,
    private val sharing: com.photonne.app.ui.actions.AssetSharing,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminBackupUiState())
    val state: StateFlow<AdminBackupUiState> = _state.asStateFlow()

    fun setLevel(value: BackupLevel) {
        _state.update { it.copy(level = value) }
    }

    fun downloadBackup() {
        if (_state.value.isDownloading) return
        _state.update { it.copy(isDownloading = true, error = null, downloadedTo = null) }
        val level = _state.value.level
        viewModelScope.launch {
            runCatching {
                val payload: AssetContentBytes = repository.downloadBackup(level.wireValue)
                sharing.saveAsset(
                    bytes = payload.bytes,
                    fileName = payload.suggestedFileName,
                    mimeType = "application/json"
                )
            }
                .onSuccess { saved ->
                    _state.update {
                        it.copy(isDownloading = false, downloadedTo = saved.path)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isDownloading = false,
                            error = errorFactory.from(error, getString(Res.string.admin_backup_error_download))
                        )
                    }
                }
        }
    }
}

@Composable
fun AdminBackupScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminBackupViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AdminPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
    page {
        ErrorBanner(error = state.error)
        state.downloadedTo?.let { path ->
            Text(
                stringResource(Res.string.admin_backup_downloaded, path),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            stringResource(Res.string.admin_backup_section_download),
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            stringResource(Res.string.admin_backup_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                stringResource(Res.string.admin_backup_media_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.lg)
            )
        }

        BackupLevelOption(
            level = BackupLevel.Config,
            selected = state.level == BackupLevel.Config,
            title = Res.string.admin_backup_level_config,
            description = Res.string.admin_backup_level_config_desc,
            enabled = !state.isDownloading,
            onSelect = viewModel::setLevel
        )
        BackupLevelOption(
            level = BackupLevel.Essential,
            selected = state.level == BackupLevel.Essential,
            title = Res.string.admin_backup_level_essential,
            description = Res.string.admin_backup_level_essential_desc,
            enabled = !state.isDownloading,
            onSelect = viewModel::setLevel
        )
        BackupLevelOption(
            level = BackupLevel.Full,
            selected = state.level == BackupLevel.Full,
            title = Res.string.admin_backup_level_full,
            description = Res.string.admin_backup_level_full_desc,
            enabled = !state.isDownloading,
            onSelect = viewModel::setLevel
        )

        PrimaryActionButton(
            label = stringResource(Res.string.admin_backup_action_download),
            isLoading = state.isDownloading,
            onClick = viewModel::downloadBackup
        )

        HorizontalDivider()

        Text(
            stringResource(Res.string.admin_backup_section_restore),
            style = MaterialTheme.typography.titleSmall
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                stringResource(Res.string.admin_backup_restore_only_pwa),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.lg)
            )
        }
    }
    }
}

@Composable
private fun BackupLevelOption(
    level: BackupLevel,
    selected: Boolean,
    title: StringResource,
    description: StringResource,
    enabled: Boolean,
    onSelect: (BackupLevel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { onSelect(level) }
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            enabled = enabled,
            onClick = null
        )
        Spacer(Modifier.size(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
