package com.photonne.app.ui.devicebackup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.enrichment_card_counts
import com.photonne.app.resources.enrichment_empty
import com.photonne.app.resources.enrichment_load_error
import com.photonne.app.resources.enrichment_retry_all
import com.photonne.app.resources.enrichment_retrying_all
import com.photonne.app.resources.enrichment_summary
import com.photonne.app.resources.enrichment_task_exif
import com.photonne.app.resources.enrichment_task_face_recognition
import com.photonne.app.resources.enrichment_task_image_embedding
import com.photonne.app.resources.enrichment_task_media_recognition
import com.photonne.app.resources.enrichment_task_object_detection
import com.photonne.app.resources.enrichment_task_scene_classification
import com.photonne.app.resources.enrichment_task_text_recognition
import com.photonne.app.resources.enrichment_task_thumbnails
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Lists every asset the user owns that still has at least one enrichment
 * task in flight (Pending/Processing) or that failed all its attempts.
 * Each card shows summary counts, a chip per failed task type (clickable
 * to retry just that one), and a "Retry failed" button per asset.
 */
@Composable
fun EnrichmentStatusScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: EnrichmentStatusViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(top = reservedTop)) {
        if (state.items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        Res.string.enrichment_summary,
                        state.totalInFlight,
                        state.totalFailed
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        when {
            state.isLoading && state.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.loadError != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(Res.string.enrichment_load_error, state.loadError ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            state.items.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(Res.string.enrichment_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + floatingNavBarReservedHeight()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = state.items, key = { it.asset.assetId }) { item ->
                        EnrichmentAssetCard(
                            item = item,
                            onRetryTask = { type ->
                                viewModel.retryTask(item.asset.assetId, type)
                            },
                            onRetryAll = { viewModel.retryAll(item.asset.assetId) }
                        )
                    }
                }
            }
        }
    }
        SubscreenFloatingChrome(
            title = title,
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                isScrollInProgress = { listState.isScrollInProgress },
                scrollToTopMinIndex = 4,
                onScrollToTop = { listState.animateScrollToItem(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
}

@Composable
private fun EnrichmentAssetCard(
    item: EnrichmentAssetItem,
    onRetryTask: (String) -> Unit,
    onRetryAll: () -> Unit
) {
    val asset = item.asset
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.fileName.ifBlank { asset.assetId },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            Res.string.enrichment_card_counts,
                            asset.pending, asset.processing, asset.failed
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (asset.failed > 0) {
                    Button(
                        onClick = onRetryAll,
                        enabled = !item.isRetryingAll
                    ) {
                        if (item.isRetryingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(Res.string.enrichment_retrying_all))
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(stringResource(Res.string.enrichment_retry_all))
                        }
                    }
                }
            }

            if (asset.failedTaskTypes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FailedTaskChips(
                    failedTypes = asset.failedTaskTypes,
                    retryingTypes = item.perTypeRetrying,
                    onClick = onRetryTask
                )
            }

            item.retryError?.takeIf { it.isNotBlank() }?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FailedTaskChips(
    failedTypes: List<String>,
    retryingTypes: Set<String>,
    onClick: (String) -> Unit
) {
    // Wrap chips so they flow across lines on narrow screens.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        failedTypes.forEach { type ->
            val isRetrying = type in retryingTypes
            AssistChip(
                onClick = { if (!isRetrying) onClick(type) },
                label = { Text(taskLabel(type)) },
                leadingIcon = {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

/**
 * Maps a server-side task type string to its localized label. Falls back to
 * the raw string for any future type the server adds before the client knows
 * about it.
 */
@Composable
private fun taskLabel(taskType: String): String {
    val resource: StringResource? = when (taskType.lowercase()) {
        "exif" -> Res.string.enrichment_task_exif
        "thumbnails" -> Res.string.enrichment_task_thumbnails
        "mediarecognition" -> Res.string.enrichment_task_media_recognition
        "facerecognition" -> Res.string.enrichment_task_face_recognition
        "objectdetection" -> Res.string.enrichment_task_object_detection
        "sceneclassification" -> Res.string.enrichment_task_scene_classification
        "textrecognition" -> Res.string.enrichment_task_text_recognition
        "imageembedding" -> Res.string.enrichment_task_image_embedding
        else -> null
    }
    return if (resource != null) stringResource(resource) else taskType
}
