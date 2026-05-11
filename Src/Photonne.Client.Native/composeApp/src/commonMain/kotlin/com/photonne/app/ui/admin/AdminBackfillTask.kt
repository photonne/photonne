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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.BackfillResponse
import com.photonne.app.data.models.GlobalReclusterResponse
import com.photonne.app.data.models.PendingCountResponse
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_backfill_action_clustering
import com.photonne.app.resources.admin_backfill_action_run
import com.photonne.app.resources.admin_backfill_clustering_result
import com.photonne.app.resources.admin_backfill_in_queue
import com.photonne.app.resources.admin_backfill_only_missing
import com.photonne.app.resources.admin_backfill_pending
import com.photonne.app.resources.admin_backfill_response
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Identifies one of the five ML backfill endpoints
 *  (`/api/admin/maintenance/{kind}/backfill`). The clustering button is
 *  shown only when `clustering` is true (face recognition only). */
enum class AdminBackfillKind(val apiPath: String, val showClustering: Boolean) {
    FaceRecognition("face-recognition", true),
    ObjectDetection("object-detection", false),
    SceneClassification("scene-classification", false),
    TextRecognition("text-recognition", false),
    ImageEmbedding("image-embedding", false)
}

data class AdminBackfillUiState(
    val kind: AdminBackfillKind,
    val pending: PendingCountResponse? = null,
    val isLoadingPending: Boolean = false,
    val isRunning: Boolean = false,
    val lastResponse: BackfillResponse? = null,
    val lastClustering: GlobalReclusterResponse? = null,
    val errorMessage: String? = null,
    val onlyMissing: Boolean = true
)

class AdminBackfillViewModel(
    private val repository: AdminRepository,
    private val kind: AdminBackfillKind
) : ViewModel() {

    private val _state = MutableStateFlow(AdminBackfillUiState(kind = kind))
    val state: StateFlow<AdminBackfillUiState> = _state.asStateFlow()

    fun loadPending() {
        if (_state.value.isLoadingPending) return
        _state.update { it.copy(isLoadingPending = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.pendingCount(kind.apiPath) }
                .onSuccess { pending ->
                    _state.update { it.copy(pending = pending, isLoadingPending = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoadingPending = false,
                            errorMessage = error.message ?: "Could not load pending"
                        )
                    }
                }
        }
    }

    fun setOnlyMissing(value: Boolean) {
        _state.update { it.copy(onlyMissing = value) }
    }

    fun runBackfill() {
        if (_state.value.isRunning) return
        val onlyMissing = _state.value.onlyMissing
        _state.update {
            it.copy(isRunning = true, errorMessage = null, lastResponse = null)
        }
        viewModelScope.launch {
            runCatching {
                repository.backfill(
                    kind = kind.apiPath,
                    batchSize = null,
                    onlyMissing = onlyMissing
                )
            }
                .onSuccess { response ->
                    _state.update {
                        it.copy(isRunning = false, lastResponse = response)
                    }
                    loadPending()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunning = false,
                            errorMessage = error.message ?: "Backfill failed"
                        )
                    }
                }
        }
    }

    fun runClustering() {
        if (_state.value.isRunning) return
        _state.update { it.copy(isRunning = true, errorMessage = null, lastClustering = null) }
        viewModelScope.launch {
            runCatching { repository.runFaceClustering() }
                .onSuccess { response ->
                    _state.update { it.copy(isRunning = false, lastClustering = response) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isRunning = false,
                            errorMessage = error.message ?: "Clustering failed"
                        )
                    }
                }
        }
    }
}

@Composable
fun AdminBackfillScreen(viewModel: AdminBackfillViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadPending() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.errorMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
        }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_backfill_pending),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        state.pending?.unprocessed?.toString() ?: "—",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.admin_backfill_in_queue),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        state.pending?.inQueue?.toString() ?: "—",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_backfill_only_missing),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = state.onlyMissing,
                onCheckedChange = viewModel::setOnlyMissing,
                enabled = !state.isRunning
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
            }
            Button(onClick = viewModel::runBackfill, enabled = !state.isRunning) {
                Text(stringResource(Res.string.admin_backfill_action_run))
            }
        }

        state.lastResponse?.let { response ->
            Text(
                stringResource(
                    Res.string.admin_backfill_response,
                    response.enqueued,
                    response.total
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (state.kind.showClustering) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = viewModel::runClustering,
                    enabled = !state.isRunning
                ) {
                    Text(stringResource(Res.string.admin_backfill_action_clustering))
                }
            }
            state.lastClustering?.let { result ->
                Text(
                    stringResource(
                        Res.string.admin_backfill_clustering_result,
                        result.ownersProcessed,
                        result.personsCreated
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
