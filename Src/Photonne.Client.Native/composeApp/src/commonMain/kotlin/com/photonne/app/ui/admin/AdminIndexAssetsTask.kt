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
import com.photonne.app.data.models.IndexStreamEvent
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_index_action_cancel
import com.photonne.app.resources.admin_index_action_start
import com.photonne.app.resources.admin_index_completed
import com.photonne.app.resources.admin_index_started
import com.photonne.app.resources.admin_index_stats_duplicates
import com.photonne.app.resources.admin_index_stats_extracted
import com.photonne.app.resources.admin_index_stats_indexed
import com.photonne.app.resources.admin_index_stats_jobs_queued
import com.photonne.app.resources.admin_index_stats_new
import com.photonne.app.resources.admin_index_stats_orphans
import com.photonne.app.resources.admin_index_stats_section
import com.photonne.app.resources.admin_index_stats_thumbnails
import com.photonne.app.resources.admin_index_stats_total
import com.photonne.app.resources.admin_index_stats_updated
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class AdminIndexUiState(
    val isRunning: Boolean = false,
    val lastEvent: IndexStreamEvent? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class AdminIndexAssetsViewModel(
    private val repository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminIndexUiState())
    val state: StateFlow<AdminIndexUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun start() {
        if (_state.value.isRunning) return
        _state.update { it.copy(isRunning = true, errorMessage = null, lastEvent = null) }
        job = viewModelScope.launch {
            runCatching {
                repository.indexStream().collect { event ->
                    _state.update { it.copy(lastEvent = event) }
                }
            }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunning = false,
                            errorMessage = error.message ?: "Indexing failed"
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
fun AdminIndexAssetsScreen(viewModel: AdminIndexAssetsViewModel) {
    val state by viewModel.state.collectAsState()

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
                            stringResource(Res.string.admin_index_completed)
                        event != null -> event.message
                        state.isRunning -> stringResource(Res.string.admin_index_started)
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
                    Text(
                        stringResource(Res.string.admin_index_stats_section),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_total),
                        stats.totalFilesFound
                    )
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_new),
                        stats.newFiles
                    )
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_updated),
                        stats.updatedFiles
                    )
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_indexed),
                        stats.hashesCalculated
                    )
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_extracted),
                        stats.exifExtracted
                    )
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_jobs_queued),
                        stats.mlJobsQueued
                    )
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_thumbnails),
                        stats.thumbnailsGenerated
                    )
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_orphans),
                        stats.orphanedFilesRemoved
                    )
                    IndexStatRow(
                        stringResource(Res.string.admin_index_stats_duplicates),
                        stats.duplicateAssetsRemoved
                    )
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
                    Text(stringResource(Res.string.admin_index_action_cancel))
                }
            } else {
                Button(onClick = viewModel::start) {
                    Text(stringResource(Res.string.admin_index_action_start))
                }
            }
        }
    }
}

@Composable
private fun IndexStatRow(label: String, value: Int?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}
