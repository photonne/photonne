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
import com.photonne.app.data.models.ThumbnailStreamEvent
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_thumbnails_action_cancel
import com.photonne.app.resources.admin_thumbnails_action_start
import com.photonne.app.resources.admin_thumbnails_completed
import com.photonne.app.resources.admin_thumbnails_regenerate
import com.photonne.app.resources.admin_thumbnails_stats_failed
import com.photonne.app.resources.admin_thumbnails_stats_generated
import com.photonne.app.resources.admin_thumbnails_stats_processed
import com.photonne.app.resources.admin_thumbnails_stats_skipped
import com.photonne.app.resources.admin_thumbnails_stats_total
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

data class AdminThumbnailsUiState(
    val isRunning: Boolean = false,
    val regenerate: Boolean = false,
    val lastEvent: ThumbnailStreamEvent? = null,
    val errorMessage: String? = null
)

class AdminThumbnailsViewModel(
    private val repository: AdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(AdminThumbnailsUiState())
    val state: StateFlow<AdminThumbnailsUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun setRegenerate(value: Boolean) {
        _state.update { it.copy(regenerate = value) }
    }

    fun start() {
        if (_state.value.isRunning) return
        val regenerate = _state.value.regenerate
        _state.update { it.copy(isRunning = true, errorMessage = null, lastEvent = null) }
        job = viewModelScope.launch {
            runCatching {
                repository.thumbnailsStream(regenerate = regenerate).collect { event ->
                    _state.update { it.copy(lastEvent = event) }
                }
            }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunning = false,
                            errorMessage = error.message ?: "Thumbnail generation failed"
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
fun AdminThumbnailsScreen(viewModel: AdminThumbnailsViewModel) {
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
                stringResource(Res.string.admin_thumbnails_regenerate),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.regenerate,
                onCheckedChange = viewModel::setRegenerate,
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
                            stringResource(Res.string.admin_thumbnails_completed)
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
                    StatRow(stringResource(Res.string.admin_thumbnails_stats_total), stats.totalAssets)
                    StatRow(stringResource(Res.string.admin_thumbnails_stats_processed), stats.processed)
                    StatRow(stringResource(Res.string.admin_thumbnails_stats_generated), stats.generated)
                    StatRow(stringResource(Res.string.admin_thumbnails_stats_skipped), stats.skipped)
                    StatRow(stringResource(Res.string.admin_thumbnails_stats_failed), stats.failed)
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
                    Text(stringResource(Res.string.admin_thumbnails_action_cancel))
                }
            } else {
                Button(onClick = viewModel::start) {
                    Text(stringResource(Res.string.admin_thumbnails_action_start))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium)
    }
}
