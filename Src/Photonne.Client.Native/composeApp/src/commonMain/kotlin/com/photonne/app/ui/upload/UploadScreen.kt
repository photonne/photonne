package com.photonne.app.ui.upload

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.resources.Res
import com.photonne.app.resources.upload_action_add
import com.photonne.app.resources.upload_action_cancel_all
import com.photonne.app.resources.upload_action_clear_finished
import com.photonne.app.resources.upload_empty_subtitle
import com.photonne.app.resources.upload_empty_title
import com.photonne.app.resources.upload_summary
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.theme.EmptyState as SharedEmptyState
import com.photonne.app.ui.theme.PhotonneColors
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
                    is MediaPickerUnavailable -> error.message ?: "El selector no está disponible"
                    else -> error.message ?: "No se pudo abrir el selector"
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.items.isNotEmpty()) {
            Header(
                state = state,
                onPickFiles = onPickFiles,
                onCancelAll = onCancelAll,
                onClearFinished = onClearFinished
            )
        }

        state.pickerError?.let { error ->
            ErrorBanner(message = error, onDismiss = onDismissPickerError)
        }

        if (state.items.isEmpty()) {
            ClickableEmptyState(onPickFiles = onPickFiles)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = floatingNavBarReservedHeight())) {
                items(state.items, key = { it.id }) { item ->
                    UploadRow(
                        item = item,
                        onRetry = { onRetry(item.id) },
                        onRemove = { onRemove(item.id) }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
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
            Spacer(Modifier.weight(1f))
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
    HorizontalDivider()
}

@Composable
private fun ClickableEmptyState(onPickFiles: () -> Unit) {
    SharedEmptyState(
        icon = Icons.Outlined.AddPhotoAlternate,
        title = stringResource(Res.string.upload_empty_title),
        subtitle = stringResource(Res.string.upload_empty_subtitle),
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPickFiles
            )
    )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Thumbnail(item)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.status == UploadStatus.Uploading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(2.dp)
                )
            }
            item.errorMessage?.takeIf { item.status == UploadStatus.Failed }?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        RowAction(item = item, onRetry = onRetry, onRemove = onRemove)
    }
}

@Composable
private fun Thumbnail(item: UploadItem) {
    val isImage = item.mimeType.startsWith("image/", ignoreCase = true)
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (isImage && item.bytes != null) {
            AsyncImage(
                model = item.bytes,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = if (isImage) Icons.Outlined.Image else Icons.Outlined.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
            )
        }
        when (item.status) {
            UploadStatus.Done -> ThumbStatusOverlay(
                Icons.Filled.CheckCircle,
                PhotonneColors.success
            )
            UploadStatus.Skipped -> ThumbStatusOverlay(
                Icons.Filled.CheckCircle,
                Color.White.copy(alpha = 0.9f)
            )
            UploadStatus.Failed -> ThumbStatusOverlay(
                Icons.Filled.ErrorOutline,
                MaterialTheme.colorScheme.error
            )
            UploadStatus.Cancelled -> ThumbStatusOverlay(
                Icons.Filled.ErrorOutline,
                Color.White.copy(alpha = 0.9f)
            )
            else -> Unit
        }
    }
}

@Composable
private fun BoxScope.ThumbStatusOverlay(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun RowAction(
    item: UploadItem,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    when (item.status) {
        UploadStatus.Uploading -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        UploadStatus.Queued -> IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = null)
        }
        UploadStatus.Failed, UploadStatus.Cancelled -> Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = null)
            }
        }
        UploadStatus.Done, UploadStatus.Skipped -> Unit
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
