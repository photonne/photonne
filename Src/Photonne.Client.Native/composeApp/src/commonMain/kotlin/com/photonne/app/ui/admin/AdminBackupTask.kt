package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.AssetContentBytes
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_backup_action_download
import com.photonne.app.resources.admin_backup_downloaded
import com.photonne.app.resources.admin_backup_explanation
import com.photonne.app.resources.admin_backup_include_ml
import com.photonne.app.resources.admin_backup_restore_only_pwa
import com.photonne.app.resources.admin_backup_section_download
import com.photonne.app.resources.admin_backup_section_restore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class AdminBackupUiState(
    val includeMl: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadedTo: String? = null,
    val errorMessage: String? = null
)

class AdminBackupViewModel(
    private val repository: AdminRepository,
    private val sharing: com.photonne.app.ui.actions.AssetSharing
) : ViewModel() {

    private val _state = MutableStateFlow(AdminBackupUiState())
    val state: StateFlow<AdminBackupUiState> = _state.asStateFlow()

    fun setIncludeMl(value: Boolean) {
        _state.update { it.copy(includeMl = value) }
    }

    fun downloadBackup() {
        if (_state.value.isDownloading) return
        _state.update { it.copy(isDownloading = true, errorMessage = null, downloadedTo = null) }
        val includeMl = _state.value.includeMl
        viewModelScope.launch {
            runCatching {
                val payload: AssetContentBytes = repository.downloadBackup(includeMl)
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
                            errorMessage = error.message ?: "Backup download failed"
                        )
                    }
                }
        }
    }
}

@Composable
fun AdminBackupScreen(viewModel: AdminBackupViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_backup_include_ml),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.includeMl,
                onCheckedChange = viewModel::setIncludeMl,
                enabled = !state.isDownloading
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
            }
            Button(
                onClick = viewModel::downloadBackup,
                enabled = !state.isDownloading
            ) {
                Text(stringResource(Res.string.admin_backup_action_download))
            }
        }

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
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
