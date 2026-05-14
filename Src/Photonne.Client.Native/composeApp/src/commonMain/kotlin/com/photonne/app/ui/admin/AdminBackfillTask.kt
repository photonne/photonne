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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.models.BackfillResponse
import com.photonne.app.data.models.GlobalReclusterResponse
import com.photonne.app.data.models.PendingCountResponse
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_backfill_action_clustering
import com.photonne.app.resources.admin_backfill_action_refresh
import com.photonne.app.resources.admin_backfill_action_start
import com.photonne.app.resources.admin_backfill_action_stop
import com.photonne.app.resources.admin_backfill_batch_size_helper
import com.photonne.app.resources.admin_backfill_batch_size_label
import com.photonne.app.resources.admin_backfill_cancel_caption
import com.photonne.app.resources.admin_backfill_clustering_result
import com.photonne.app.resources.admin_backfill_empty_all_processed
import com.photonne.app.resources.admin_backfill_empty_all_queued
import com.photonne.app.resources.admin_backfill_error_load
import com.photonne.app.resources.admin_backfill_in_queue
import com.photonne.app.resources.admin_backfill_only_missing
import com.photonne.app.resources.admin_backfill_session_progress
import com.photonne.app.resources.admin_backfill_status_enqueuing
import com.photonne.app.resources.admin_backfill_status_enqueuing_progress
import com.photonne.app.resources.admin_backfill_summary_done
import com.photonne.app.resources.admin_backfill_summary_stopped
import com.photonne.app.resources.admin_backfill_unprocessed
import kotlinx.coroutines.Job
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

private const val DEFAULT_BATCH_SIZE = 500
private const val MIN_BATCH_SIZE = 1
private const val MAX_BATCH_SIZE = 5000
private const val SETTING_BACKFILL_BATCH = "TaskSettings.BackfillBatchSize"

/**
 * State for the ML backfill screen. Mirrors `MlBackfillCard.razor`:
 * an iterative loop of `POST /backfill` calls that keeps enqueuing
 * batches until the unprocessed counter hits zero or the admin cancels.
 * The session-scoped counters drive the live progress bar; pending
 * counts are refreshed from the server.
 */
data class AdminBackfillUiState(
    val kind: AdminBackfillKind,
    val pending: PendingCountResponse? = null,
    val isLoadingPending: Boolean = false,
    val isRunning: Boolean = false,
    val onlyMissing: Boolean = true,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val globalBatchSize: Int = DEFAULT_BATCH_SIZE,
    val initialUnprocessed: Int = 0,
    val enqueuedThisSession: Int = 0,
    val lastResponse: BackfillResponse? = null,
    val lastClustering: GlobalReclusterResponse? = null,
    val lastSummary: String? = null,
    val errorMessage: String? = null
)

class AdminBackfillViewModel(
    private val repository: AdminRepository,
    private val kind: AdminBackfillKind
) : ViewModel() {

    private val _state = MutableStateFlow(AdminBackfillUiState(kind = kind))
    val state: StateFlow<AdminBackfillUiState> = _state.asStateFlow()
    private var loopJob: Job? = null

    fun loadDefaults() {
        viewModelScope.launch {
            runCatching {
                val raw = repository.getSettingString(SETTING_BACKFILL_BATCH)
                parseBatchSize(raw)
            }.onSuccess { value ->
                _state.update {
                    if (it.isRunning) it
                    else it.copy(globalBatchSize = value, batchSize = value)
                }
            }
        }
        loadPending()
    }

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
        if (_state.value.isRunning) return
        _state.update { it.copy(onlyMissing = value) }
    }

    fun setBatchSize(rawText: String) {
        if (_state.value.isRunning) return
        val parsed = parseBatchSize(rawText)
        _state.update { it.copy(batchSize = parsed) }
    }

    /**
     * Loops POSTs to `/backfill` until either:
     *  - the server reports no remaining unprocessed work (the natural
     *    "all enqueued" end state), or
     *  - the admin presses Stop, or
     *  - the server returns `enqueued == 0` while `total > 0` (a guard
     *    against an infinite spin if something blocks progress).
     * Already-enqueued jobs are picked up by the server's ML worker
     * pool regardless of whether this loop completes — Stop only halts
     * new enqueues.
     */
    fun start() {
        val current = _state.value
        if (current.isRunning) return
        val initialUnprocessed = current.pending?.unprocessed ?: 0
        if (initialUnprocessed <= 0) return

        _state.update {
            it.copy(
                isRunning = true,
                errorMessage = null,
                lastSummary = null,
                lastResponse = null,
                initialUnprocessed = initialUnprocessed,
                enqueuedThisSession = 0
            )
        }
        loopJob = viewModelScope.launch {
            val onlyMissing = _state.value.onlyMissing
            val batchSize = _state.value.batchSize
            try {
                var remaining = initialUnprocessed
                while (remaining > 0) {
                    val response = repository.backfill(
                        kind = kind.apiPath,
                        batchSize = batchSize,
                        onlyMissing = onlyMissing
                    )
                    val enqueued = response.enqueued
                    _state.update {
                        val pending = it.pending
                        it.copy(
                            enqueuedThisSession = it.enqueuedThisSession + enqueued,
                            lastResponse = response,
                            pending = pending?.copy(
                                unprocessed = (response.total - enqueued).coerceAtLeast(0),
                                inQueue = pending.inQueue + enqueued
                            )
                        )
                    }
                    if (enqueued == 0) break
                    remaining = (response.total - enqueued).coerceAtLeast(0)
                }
                _state.update {
                    val totalEnqueued = it.enqueuedThisSession
                    it.copy(
                        isRunning = false,
                        lastSummary = SummaryKind.Done(totalEnqueued).encode()
                    )
                }
                // Refresh authoritative pending count from the server.
                loadPending()
            } catch (t: kotlinx.coroutines.CancellationException) {
                val s = _state.value
                _state.update {
                    it.copy(
                        isRunning = false,
                        lastSummary = SummaryKind.Stopped(
                            enqueued = s.enqueuedThisSession,
                            remaining = (s.pending?.unprocessed ?: 0)
                        ).encode()
                    )
                }
                throw t
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        isRunning = false,
                        errorMessage = e.message ?: "Backfill failed"
                    )
                }
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    fun runClustering() {
        if (_state.value.isRunning) return
        _state.update { it.copy(errorMessage = null, lastClustering = null) }
        viewModelScope.launch {
            runCatching { repository.runFaceClustering() }
                .onSuccess { response ->
                    _state.update { it.copy(lastClustering = response) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(errorMessage = error.message ?: "Clustering failed")
                    }
                }
        }
    }

    private fun parseBatchSize(raw: String?): Int {
        val numeric = raw?.toIntOrNull() ?: DEFAULT_BATCH_SIZE
        return numeric.coerceIn(MIN_BATCH_SIZE, MAX_BATCH_SIZE)
    }
}

/** Encoded form for the "last summary" line, so the composable can
 *  render localized text from a serializable tag. */
private sealed class SummaryKind {
    abstract fun encode(): String

    data class Done(val enqueued: Int) : SummaryKind() {
        override fun encode() = "done:$enqueued"
    }

    data class Stopped(val enqueued: Int, val remaining: Int) : SummaryKind() {
        override fun encode() = "stopped:$enqueued:$remaining"
    }
}

@Composable
fun AdminBackfillScreen(viewModel: AdminBackfillViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadDefaults() }

    val unprocessed = state.pending?.unprocessed ?: 0
    val inQueue = state.pending?.inQueue ?: 0
    val sessionPct = if (state.initialUnprocessed > 0) {
        (state.enqueuedThisSession.toFloat() / state.initialUnprocessed.toFloat())
            .coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        state.errorMessage?.let { msg ->
            Text(
                stringResource(Res.string.admin_backfill_error_load, msg),
                color = MaterialTheme.colorScheme.error
            )
        }

        // Pending counters — two stat cells side by side.
        StatGridCard(
            columns = 2,
            items = listOf(
                StatGridItem(
                    label = stringResource(Res.string.admin_backfill_unprocessed),
                    value = unprocessed.toString(),
                    valueColor = if (unprocessed > 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                StatGridItem(
                    label = stringResource(Res.string.admin_backfill_in_queue),
                    value = inQueue.toString(),
                    valueColor = if (inQueue > 0) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        )

        // Empty states ------------------------------------------------
        if (!state.isRunning && unprocessed == 0 && inQueue == 0) {
            EmptyStateCard(
                text = stringResource(Res.string.admin_backfill_empty_all_processed),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        } else if (!state.isRunning && unprocessed == 0 && inQueue > 0) {
            EmptyStateCard(
                text = stringResource(Res.string.admin_backfill_empty_all_queued),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        // "Only missing" toggle (re-process all vs. only missing).
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

        // Batch size override (numeric input, defaults to the global).
        OutlinedTextField(
            value = state.batchSize.toString(),
            onValueChange = viewModel::setBatchSize,
            label = { Text(stringResource(Res.string.admin_backfill_batch_size_label)) },
            supportingText = {
                Text(
                    stringResource(
                        Res.string.admin_backfill_batch_size_helper,
                        state.globalBatchSize
                    )
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !state.isRunning,
            modifier = Modifier.fillMaxWidth()
        )

        // Live progress while the loop is running.
        if (state.isRunning) {
            Text(
                stringResource(
                    Res.string.admin_backfill_session_progress,
                    state.enqueuedThisSession,
                    state.initialUnprocessed
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val statusText = if (state.enqueuedThisSession > 0) {
                stringResource(
                    Res.string.admin_backfill_status_enqueuing_progress,
                    state.enqueuedThisSession,
                    state.initialUnprocessed
                )
            } else {
                stringResource(Res.string.admin_backfill_status_enqueuing)
            }
            TaskProgressCard(
                statusText = statusText,
                progress = sessionPct
            )
        } else {
            state.lastSummary?.let { summary ->
                LastSummaryCard(summary = summary)
            }
        }

        // Action buttons (Start / Stop + Refresh).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.isRunning) {
                Button(onClick = viewModel::stop) {
                    Text(stringResource(Res.string.admin_backfill_action_stop))
                }
                Spacer(Modifier.size(4.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Button(
                    onClick = viewModel::start,
                    enabled = unprocessed > 0
                ) {
                    Text(stringResource(Res.string.admin_backfill_action_start))
                }
                TextButton(
                    onClick = viewModel::loadPending,
                    enabled = !state.isLoadingPending
                ) {
                    Text(stringResource(Res.string.admin_backfill_action_refresh))
                }
            }
        }

        Text(
            stringResource(Res.string.admin_backfill_cancel_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Face-only: re-cluster button + result line.
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

@Composable
private fun EmptyStateCard(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color,
            contentColor = contentColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun LastSummaryCard(summary: String) {
    // Decode the "done:N" or "stopped:N:R" tags emitted by the VM so
    // the rendered text stays localized through `stringResource`.
    val parts = summary.split(":")
    when (parts.firstOrNull()) {
        "done" -> {
            val n = parts.getOrNull(1)?.toIntOrNull() ?: 0
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(
                    stringResource(Res.string.admin_backfill_summary_done, n),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        "stopped" -> {
            val n = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val r = parts.getOrNull(2)?.toIntOrNull() ?: 0
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text(
                    stringResource(Res.string.admin_backfill_summary_stopped, n, r),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
