package com.photonne.app.ui.admin

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.AdminEnrichmentFailureDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_enrichment_failures_attempts
import com.photonne.app.resources.admin_enrichment_failures_badge_permanent
import com.photonne.app.resources.admin_enrichment_failures_badge_suppressed
import com.photonne.app.resources.admin_enrichment_failures_empty
import com.photonne.app.resources.admin_enrichment_failures_filter_all
import com.photonne.app.resources.admin_enrichment_failures_load_error
import com.photonne.app.resources.admin_enrichment_failures_load_more
import com.photonne.app.resources.admin_enrichment_failures_retry
import com.photonne.app.resources.admin_enrichment_failures_retry_all
import com.photonne.app.resources.admin_enrichment_failures_retrying_all
import com.photonne.app.resources.admin_enrichment_failures_suppress
import com.photonne.app.resources.admin_enrichment_failures_total
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
import com.photonne.app.ui.theme.EmptyState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Admin registry of every enrichment task that failed (or was dismissed)
 * across all users: cause, attempt count and owner per row, filter chips by
 * task type, and the retry/suppress actions. The file name opens the asset in
 * the viewer so the admin can decide what to do with the offending file.
 */
@Composable
fun AdminEnrichmentFailuresScreen(
    title: String,
    initialType: String?,
    onBack: () -> Unit,
    viewModel: AdminEnrichmentFailuresViewModel,
    onOpenAsset: (AdminEnrichmentFailureDto) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.start(initialType) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                            stringResource(Res.string.admin_enrichment_failures_load_error, state.loadError ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                state.items.isEmpty() && state.typeFilter == null && state.countsByType.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.TaskAlt,
                    title = stringResource(Res.string.admin_enrichment_failures_empty)
                )
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp,
                            top = 8.dp + reservedTop,
                            bottom = 8.dp + floatingNavBarReservedHeight()
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item("header") {
                            FailuresHeader(
                                total = state.total,
                                countsByType = state.countsByType,
                                typeFilter = state.typeFilter,
                                isRetryingAll = state.isRetryingAll,
                                onFilter = { viewModel.setFilter(it) },
                                onRetryAll = { viewModel.retryAll() }
                            )
                        }
                        items(items = state.items, key = { it.failure.taskId }) { item ->
                            FailureCard(
                                item = item,
                                onOpenAsset = { onOpenAsset(item.failure) },
                                onRetry = { viewModel.retry(item.failure.taskId) },
                                onSuppress = { viewModel.suppress(item.failure.taskId) }
                            )
                        }
                        if (state.nextCursor != null) {
                            item("load-more") {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        TextButton(onClick = { viewModel.loadMore() }) {
                                            Text(stringResource(Res.string.admin_enrichment_failures_load_more))
                                        }
                                    }
                                }
                            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FailuresHeader(
    total: Int,
    countsByType: Map<String, Int>,
    typeFilter: String?,
    isRetryingAll: Boolean,
    onFilter: (String?) -> Unit,
    onRetryAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.admin_enrichment_failures_total, total),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (total > 0) {
                Button(onClick = onRetryAll, enabled = !isRetryingAll) {
                    if (isRetryingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(Res.string.admin_enrichment_failures_retrying_all))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(Res.string.admin_enrichment_failures_retry_all))
                    }
                }
            }
        }
        if (countsByType.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = typeFilter == null,
                    onClick = { onFilter(null) },
                    label = { Text(stringResource(Res.string.admin_enrichment_failures_filter_all)) }
                )
                countsByType.entries.sortedBy { it.key }.forEach { (type, count) ->
                    FilterChip(
                        selected = typeFilter.equals(type, ignoreCase = true),
                        onClick = { onFilter(type) },
                        label = { Text("${taskLabel(type)} ($count)") }
                    )
                }
            }
        }
    }
}

@Composable
private fun FailureCard(
    item: AdminEnrichmentFailureItem,
    onOpenAsset: () -> Unit,
    onRetry: () -> Unit,
    onSuppress: () -> Unit
) {
    val failure = item.failure
    val isSuppressed = failure.status.equals("Suppressed", ignoreCase = true)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = failure.fileName.ifBlank { failure.assetId },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAsset)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    failure.ownerName?.takeIf { it.isNotBlank() },
                    stringResource(Res.string.admin_enrichment_failures_attempts, failure.attemptCount)
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeChip(taskLabel(failure.taskType))
                when {
                    isSuppressed -> StatusBadge(
                        stringResource(Res.string.admin_enrichment_failures_badge_suppressed),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    failure.isPermanent -> StatusBadge(
                        stringResource(Res.string.admin_enrichment_failures_badge_permanent),
                        MaterialTheme.colorScheme.error
                    )
                }
            }

            failure.errorMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            item.actionError?.takeIf { it.isNotBlank() }?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                } else {
                    if (!isSuppressed) {
                        TextButton(onClick = onSuppress) {
                            Icon(
                                Icons.Outlined.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(Res.string.admin_enrichment_failures_suppress))
                        }
                    }
                    TextButton(onClick = onRetry) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(Res.string.admin_enrichment_failures_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun StatusBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
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
