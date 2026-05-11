package com.photonne.app.ui.upload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.upload_action_add
import com.photonne.app.resources.upload_action_cancel_all
import com.photonne.app.resources.upload_action_clear_finished
import com.photonne.app.resources.upload_empty_subtitle
import com.photonne.app.resources.upload_empty_title
import com.photonne.app.resources.upload_status_cancelled
import com.photonne.app.resources.upload_status_done
import com.photonne.app.resources.upload_status_failed
import com.photonne.app.resources.upload_status_queued
import com.photonne.app.resources.upload_status_skipped
import com.photonne.app.resources.upload_status_uploading
import com.photonne.app.resources.upload_summary
import org.jetbrains.compose.resources.stringResource

@Composable
fun UploadScreen(
    state: UploadUiState,
    onPicked: (List<PickedFile>) -> Unit,
    onPickerError: (String) -> Unit,
    onRetry: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit,
    onDismissPickerError: () -> Unit
) {
    val pickMedia = rememberMediaPicker(onPicked)
    val onPickFiles: () -> Unit = {
        runCatching { pickMedia() }.onFailure { error ->
            onPickerError(
                when (error) {
                    is MediaPickerUnavailable -> error.message ?: "Picker not available"
                    else -> error.message ?: "Could not open the picker"
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            state = state,
            onPickFiles = onPickFiles,
            onCancelAll = onCancelAll,
            onClearFinished = onClearFinished
        )

        state.pickerError?.let { error ->
            ErrorBanner(message = error, onDismiss = onDismissPickerError)
        }

        if (state.items.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { item ->
                    UploadRow(
                        item = item,
                        onRetry = { onRetry(item.id) },
                        onRemove = { onRemove(item.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: UploadUiState,
    onPickFiles: () -> Unit,
    onCancelAll: () -> Unit,
    onClearFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onPickFiles) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.upload_action_add))
            }
            if (state.isUploading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            }
            if (state.pendingCount > 0) {
                TextButton(onClick = onCancelAll) {
                    Text(stringResource(Res.string.upload_action_cancel_all))
                }
            }
            if (state.items.size > state.pendingCount) {
                TextButton(onClick = onClearFinished) {
                    Text(stringResource(Res.string.upload_action_clear_finished))
                }
            }
        }
        if (state.items.isNotEmpty()) {
            Text(
                stringResource(
                    Res.string.upload_summary,
                    state.doneCount,
                    state.skippedCount,
                    state.failedCount,
                    state.pendingCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                stringResource(Res.string.upload_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(Res.string.upload_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = null)
        }
    }
}

@Composable
private fun UploadRow(
    item: UploadItem,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusBadge(item.status)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.status == UploadStatus.Failed || item.status == UploadStatus.Cancelled) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
            }
            if (item.status != UploadStatus.Uploading) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        }
        if (item.status == UploadStatus.Uploading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        item.errorMessage?.takeIf { item.status == UploadStatus.Failed }?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun StatusBadge(status: UploadStatus) {
    val (label, color) = when (status) {
        UploadStatus.Queued -> stringResource(Res.string.upload_status_queued) to
            MaterialTheme.colorScheme.onSurfaceVariant
        UploadStatus.Uploading -> stringResource(Res.string.upload_status_uploading) to
            MaterialTheme.colorScheme.primary
        UploadStatus.Done -> stringResource(Res.string.upload_status_done) to
            Color(0xFF2E7D32)
        UploadStatus.Skipped -> stringResource(Res.string.upload_status_skipped) to
            MaterialTheme.colorScheme.onSurfaceVariant
        UploadStatus.Failed -> stringResource(Res.string.upload_status_failed) to
            MaterialTheme.colorScheme.error
        UploadStatus.Cancelled -> stringResource(Res.string.upload_status_cancelled) to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(24.dp)
    ) {
        when (status) {
            UploadStatus.Done -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            UploadStatus.Failed -> Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            else -> Unit
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private fun formatBytes(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return "${kb.format1()} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${mb.format1()} MB"
    val gb = mb / 1024.0
    return "${gb.format1()} GB"
}

private fun Double.format1(): String {
    val rounded = ((this * 10).toLong().toDouble()) / 10.0
    val s = rounded.toString()
    return if (s.endsWith(".0")) s.dropLast(2) else s
}
