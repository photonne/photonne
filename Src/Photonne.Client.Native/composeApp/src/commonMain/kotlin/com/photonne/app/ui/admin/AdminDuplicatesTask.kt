package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.photonne.app.data.models.DuplicatesStreamEvent
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_duplicates_action_cancel
import com.photonne.app.resources.admin_duplicates_action_start
import com.photonne.app.resources.admin_duplicates_cleanup
import com.photonne.app.resources.admin_duplicates_completed
import com.photonne.app.resources.admin_duplicates_physical
import com.photonne.app.resources.admin_duplicates_stats_assets
import com.photonne.app.resources.admin_duplicates_stats_groups
import com.photonne.app.resources.admin_duplicates_stats_reclaimed
import com.photonne.app.resources.admin_duplicates_stats_removed
import com.photonne.app.resources.admin_duplicates_stats_total
import com.photonne.app.resources.admin_duplicates_stats_unindexed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class AdminDuplicatesUiState(
    val cleanup: Boolean = false,
    val physical: Boolean = false,
    val isRunning: Boolean = false,
    val lastEvent: DuplicatesStreamEvent? = null,
    val errorMessage: String? = null
)

class AdminDuplicatesViewModel(
    private val repository: AdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AdminDuplicatesUiState())
    val state: StateFlow<AdminDuplicatesUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun setCleanup(value: Boolean) {
        _state.update { it.copy(cleanup = value) }
    }

    fun setPhysical(value: Boolean) {
        _state.update { it.copy(physical = value) }
    }

    fun start() {
        if (_state.value.isRunning) return
        val cleanup = _state.value.cleanup
        val physical = _state.value.physical
        _state.update { it.copy(isRunning = true, errorMessage = null, lastEvent = null) }
        job = viewModelScope.launch {
            runCatching {
                repository.duplicatesStream(cleanup = cleanup, physical = physical)
                    .collect { event -> _state.update { it.copy(lastEvent = event) } }
            }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunning = false,
                            errorMessage = error.message ?: "Duplicate scan failed"
                        )
                    }
                }
            _state.update { it.copy(isRunning = false) }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(isRunning = false) }
    }
}

@Composable
fun AdminDuplicatesScreen(viewModel: AdminDuplicatesViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_duplicates_physical),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.physical,
                onCheckedChange = viewModel::setPhysical,
                enabled = !state.isRunning
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_duplicates_cleanup),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.cleanup,
                onCheckedChange = viewModel::setCleanup,
                enabled = !state.isRunning
            )
        }

        val event = state.lastEvent
        val pct = (event?.percentage ?: 0.0).toFloat().coerceIn(0f, 100f) / 100f
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = when {
                        event != null && event.isCompleted ->
                            stringResource(Res.string.admin_duplicates_completed)
                        event != null -> event.message
                        else -> "—"
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }
        }

        event?.statistics?.let { stats ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    StatRow(stringResource(Res.string.admin_duplicates_stats_total), stats.totalAssets?.toString())
                    StatRow(stringResource(Res.string.admin_duplicates_stats_groups), stats.duplicateGroups?.toString())
                    StatRow(stringResource(Res.string.admin_duplicates_stats_assets), stats.duplicateAssets?.toString())
                    StatRow(stringResource(Res.string.admin_duplicates_stats_removed), stats.removed?.toString())
                    StatRow(
                        stringResource(Res.string.admin_duplicates_stats_reclaimed),
                        stats.bytesReclaimed?.let(::humanBytes)
                    )
                    StatRow(stringResource(Res.string.admin_duplicates_stats_unindexed), stats.unindexedFiles?.toString())
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                OutlinedButton(onClick = viewModel::cancel) {
                    Text(stringResource(Res.string.admin_duplicates_action_cancel))
                }
            } else {
                Button(onClick = viewModel::start) {
                    Text(stringResource(Res.string.admin_duplicates_action_start))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}
