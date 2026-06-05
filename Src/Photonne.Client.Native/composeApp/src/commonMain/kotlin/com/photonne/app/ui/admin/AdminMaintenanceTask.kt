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
import androidx.compose.material3.MaterialTheme
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
import com.photonne.app.data.models.MaintenanceTaskResult
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_maintenance_action_empty_trash
import com.photonne.app.resources.admin_maintenance_action_missing
import com.photonne.app.resources.admin_maintenance_action_orphans
import com.photonne.app.resources.admin_maintenance_action_purge_missing
import com.photonne.app.resources.admin_maintenance_action_recalculate
import com.photonne.app.resources.admin_maintenance_desc_empty_trash
import com.photonne.app.resources.admin_maintenance_desc_missing
import com.photonne.app.resources.admin_maintenance_desc_orphans
import com.photonne.app.resources.admin_maintenance_desc_purge_missing
import com.photonne.app.resources.admin_maintenance_desc_recalculate
import com.photonne.app.resources.admin_maintenance_result
import com.photonne.app.resources.action_refresh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class AdminMaintenanceUiState(
    val runningKind: String? = null,
    val lastResults: Map<String, MaintenanceTaskResult> = emptyMap(),
    val errorMessage: String? = null
)

class AdminMaintenanceViewModel(
    private val repository: AdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AdminMaintenanceUiState())
    val state: StateFlow<AdminMaintenanceUiState> = _state.asStateFlow()

    fun run(kind: String) {
        if (_state.value.runningKind != null) return
        _state.update { it.copy(runningKind = kind, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.runMaintenance(kind) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            runningKind = null,
                            lastResults = it.lastResults + (kind to result)
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            runningKind = null,
                            errorMessage = error.message ?: "Maintenance task failed"
                        )
                    }
                }
        }
    }
}

private data class MaintenanceAction(
    val kind: String,
    val titleResId: org.jetbrains.compose.resources.StringResource,
    val descriptionResId: org.jetbrains.compose.resources.StringResource
)

@Composable
fun AdminMaintenanceScreen(viewModel: AdminMaintenanceViewModel) {
    val state by viewModel.state.collectAsState()

    val actions = listOf(
        MaintenanceAction(
            "orphan-thumbnails",
            Res.string.admin_maintenance_action_orphans,
            Res.string.admin_maintenance_desc_orphans
        ),
        MaintenanceAction(
            "missing-files",
            Res.string.admin_maintenance_action_missing,
            Res.string.admin_maintenance_desc_missing
        ),
        MaintenanceAction(
            "recalculate-sizes",
            Res.string.admin_maintenance_action_recalculate,
            Res.string.admin_maintenance_desc_recalculate
        ),
        MaintenanceAction(
            "empty-trash",
            Res.string.admin_maintenance_action_empty_trash,
            Res.string.admin_maintenance_desc_empty_trash
        ),
        MaintenanceAction(
            "purge-missing",
            Res.string.admin_maintenance_action_purge_missing,
            Res.string.admin_maintenance_desc_purge_missing
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        actions.forEach { action ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(action.titleResId),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(action.descriptionResId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.lastResults[action.kind]?.let { result ->
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(
                                Res.string.admin_maintenance_result,
                                result.processed,
                                result.affected
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (result.success) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        if (result.message.isNotBlank()) {
                            Text(
                                result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.runningKind == action.kind) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(12.dp))
                        }
                        Button(
                            onClick = { viewModel.run(action.kind) },
                            enabled = state.runningKind == null
                        ) {
                            Text(stringResource(action.titleResId))
                        }
                    }
                }
            }
        }
    }
}
