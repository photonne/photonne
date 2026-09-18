package com.photonne.app.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.TaskAlt
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
import com.photonne.app.data.api.EnrichmentFailureKind
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_enrichment_failures_also_retrying
import com.photonne.app.resources.admin_enrichment_failures_also_suppressed
import com.photonne.app.resources.admin_enrichment_failures_attempts
import com.photonne.app.resources.admin_enrichment_failures_badge_permanent
import com.photonne.app.resources.admin_enrichment_failures_badge_suppressed
import com.photonne.app.resources.admin_enrichment_failures_empty
import com.photonne.app.resources.admin_enrichment_failures_filter_all
import com.photonne.app.resources.admin_enrichment_failures_kind_needs_action
import com.photonne.app.resources.admin_enrichment_failures_kind_permanent
import com.photonne.app.resources.admin_enrichment_failures_kind_transient
import com.photonne.app.resources.admin_enrichment_failures_load_more
import com.photonne.app.resources.admin_enrichment_failures_retry
import com.photonne.app.resources.admin_enrichment_failures_retry_all
import com.photonne.app.resources.admin_enrichment_failures_retry_count
import com.photonne.app.resources.admin_enrichment_failures_section_cause
import com.photonne.app.resources.admin_enrichment_failures_section_task
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
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.theme.PrimaryActionButton
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.resources.admin_enrichment_failures_retry_all_confirm
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
    val state by viewModel.state.collectAsState()
    LaunchedEffect(initialType) { viewModel.start(initialType) }
    var confirmRetryAll by remember { mutableStateOf(false) }

    AdminListScaffold(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        isLoading = state.isLoading,
        // With a filter on, "nothing" is an answer to the filter: the chips
        // have to stay on screen to take it off again.
        isEmpty = state.items.isEmpty() && state.typeFilter == null &&
            state.kindFilter == null && state.countsByType.isEmpty(),
        error = state.loadError,
        onRefresh = viewModel::refresh,
        emptyIcon = Icons.Outlined.TaskAlt,
        emptyTitle = stringResource(Res.string.admin_enrichment_failures_empty),
        resultMessage = state.resultMessage,
        onResultShown = viewModel::consumeResult,
        onDismissError = viewModel::dismissError,
        header = {
            item("header") {
                FailuresHeader(
                    total = state.total,
                    retrying = state.retrying,
                    suppressed = state.suppressed,
                    countsByType = state.countsByType,
                    countsByKind = state.countsByKind,
                    typeFilter = state.typeFilter,
                    kindFilter = state.kindFilter,
                    isRetryingAll = state.isRetryingAll,
                    onFilter = { viewModel.setFilter(it) },
                    onKindFilter = { viewModel.setKindFilter(it) },
                    onRetryAll = { confirmRetryAll = true }
                )
            }
        }
    ) {
        items(items = state.items, key = { it.failure.taskId }) { item ->
            FailureCard(
                item = item,
                onOpenAsset = { onOpenAsset(item.failure) },
                onRetry = { viewModel.retry(item.failure.taskId) },
                onSuppress = { viewModel.suppress(item.failure.taskId) }
            )
        }
        val cursor = state.nextCursor
        if (cursor != null) {
            item("load-more") {
                // Pages on its own as the end of the list scrolls in, like the
                // rest of the app. The button is only the way back after a page
                // that failed (the error is in the banner on top).
                LaunchedEffect(cursor) { viewModel.loadMore() }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.loadError != null && !state.isLoadingMore) {
                        TextButton(onClick = { viewModel.loadMore() }) {
                            Text(stringResource(Res.string.admin_enrichment_failures_load_more))
                        }
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

    if (confirmRetryAll) {
        ConfirmActionDialog(
            title = stringResource(Res.string.admin_enrichment_failures_retry_all),
            message = stringResource(Res.string.admin_enrichment_failures_retry_all_confirm, state.total),
            confirmLabel = stringResource(Res.string.admin_enrichment_failures_retry),
            isDestructive = false,
            isSubmitting = false,
            onDismiss = { confirmRetryAll = false },
            onConfirm = {
                confirmRetryAll = false
                viewModel.retryAll()
            }
        )
    }
}

@Composable
private fun FailuresHeader(
    total: Int,
    retrying: Int,
    suppressed: Int,
    countsByType: Map<String, Int>,
    countsByKind: Map<String, Int>,
    typeFilter: String?,
    kindFilter: EnrichmentFailureKind?,
    isRetryingAll: Boolean,
    onFilter: (String?) -> Unit,
    onKindFilter: (EnrichmentFailureKind?) -> Unit,
    onRetryAll: () -> Unit
) {
    val allLabel = stringResource(Res.string.admin_enrichment_failures_filter_all)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(
            text = stringResource(Res.string.admin_enrichment_failures_total, total),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        // The total and the chips count what's waiting for the admin — the same
        // number Run Tasks calls "N con errores". The list also carries these
        // two, so say so, or the rows don't add up to the title.
        val aside = listOfNotNull(
            stringResource(Res.string.admin_enrichment_failures_also_retrying, retrying)
                .takeIf { retrying > 0 },
            stringResource(Res.string.admin_enrichment_failures_also_suppressed, suppressed)
                .takeIf { suppressed > 0 }
        )
        if (aside.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = aside.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Two filters, two named blocks. They used to be two unlabelled wraps of
        // identical chips 6dp apart — the same gap as between chips — so nobody
        // could tell where "which task" ended and "why it failed" began.
        // The chips count what's out of attempts, so a type whose rows are all
        // still retrying has none — and that's exactly the type Run Tasks sends
        // here after a sweep. The filter in force always gets its chip, at zero:
        // a filter you can't see is a filter you can't take off.
        val typeCounts = if (typeFilter != null && countsByType.keys.none { it.equals(typeFilter, ignoreCase = true) })
            countsByType + (typeFilter to 0) else countsByType
        if (typeCounts.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            FilterSection(
                title = stringResource(Res.string.admin_enrichment_failures_section_task),
                options = listOf(FilterOption<String?>(null, allLabel)) +
                    typeCounts.entries.sortedBy { it.key }.map { (type, count) ->
                        FilterOption(type, "${taskLabel(type)} ($count)")
                    },
                isSelected = { it.equals(typeFilter, ignoreCase = true) },
                onSelect = onFilter
            )
        }

        // By cause. It's the one that answers the question the admin actually
        // has in front of a wall of failures — whether retrying will achieve
        // anything — and it scopes the button below, so the transient ones can
        // be retried without dragging along the files that will fail again
        // identically.
        val kinds = countsByKind.entries
            .mapNotNull { (raw, count) ->
                val kind = EnrichmentFailureKind.from(raw)
                if (count > 0 && kind != EnrichmentFailureKind.Unknown) kind to count else null
            }
            .sortedBy { it.first.ordinal }
        if (kinds.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            FilterSection(
                title = stringResource(Res.string.admin_enrichment_failures_section_cause),
                options = listOf(FilterOption<EnrichmentFailureKind?>(null, allLabel)) +
                    kinds.map { (kind, count) ->
                        FilterOption(kind, "${stringResource(kindTitle(kind))} ($count)")
                    },
                isSelected = { it == kindFilter },
                onSelect = onKindFilter
            )
        }

        // Under the filters because it acts on what they leave, and it says how
        // many that is.
        if (total > 0) {
            Spacer(Modifier.height(Spacing.md))
            PrimaryActionButton(
                label = stringResource(Res.string.admin_enrichment_failures_retry_count, total),
                onClick = onRetryAll,
                isLoading = isRetryingAll
            )
        }
    }
}

private data class FilterOption<T>(val value: T, val label: String)

/**
 * One named filter: its label and a single line of chips that scrolls sideways.
 * A wrap grew to three or four lines with every task type failing and pushed
 * the list off the first screen.
 */
@Composable
private fun <T> FilterSection(
    title: String,
    options: List<FilterOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit
) {
    val listState = rememberLazyListState()
    // The screen can open with a filter already on (a Run Tasks row, a
    // notification): bring that chip into view rather than leave it off-screen.
    val selectedIndex = options.indexOfFirst { isSelected(it.value) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(Spacing.xxs))
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(options.size) { index ->
            val option = options[index]
            FilterChip(
                selected = isSelected(option.value),
                onClick = { onSelect(option.value) },
                label = { Text(option.label) }
            )
        }
    }
}

/** Label and colour for a failure's cause, or null when there's nothing worth
 *  saying (an unclassified row — a failure from before the server recorded
 *  this, or one nothing recognised). */
@Composable
private fun kindLabel(kind: EnrichmentFailureKind): Pair<String, androidx.compose.ui.graphics.Color>? =
    when (kind) {
        EnrichmentFailureKind.Unknown -> null
        EnrichmentFailureKind.Transient ->
            stringResource(Res.string.admin_enrichment_failures_kind_transient) to
                MaterialTheme.colorScheme.onSurfaceVariant
        EnrichmentFailureKind.Permanent ->
            stringResource(Res.string.admin_enrichment_failures_kind_permanent) to
                MaterialTheme.colorScheme.error
        EnrichmentFailureKind.NeedsAction ->
            stringResource(Res.string.admin_enrichment_failures_kind_needs_action) to
                MaterialTheme.colorScheme.primary
    }

private fun kindTitle(kind: EnrichmentFailureKind): StringResource = when (kind) {
    EnrichmentFailureKind.Transient -> Res.string.admin_enrichment_failures_kind_transient
    EnrichmentFailureKind.Permanent -> Res.string.admin_enrichment_failures_kind_permanent
    EnrichmentFailureKind.NeedsAction -> Res.string.admin_enrichment_failures_kind_needs_action
    EnrichmentFailureKind.Unknown -> Res.string.admin_enrichment_failures_filter_all
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
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(
                text = failure.fileName.ifBlank { failure.assetId },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAsset)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = listOfNotNull(
                    failure.ownerName?.takeIf { it.isNotBlank() },
                    stringResource(Res.string.admin_enrichment_failures_attempts, failure.attemptCount)
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.sm))
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
                // What kind of failure, which is the part that decides whether
                // retrying is worth anything. "Agotado" only says the attempts
                // ran out — that happens to a corrupt file and to a library
                // whose ML container was down, and those need opposite answers.
                kindLabel(failure.kind)?.let { (label, color) ->
                    StatusBadge(label, color)
                }
            }

            failure.errorMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // The service's own token, under the sentence. It's what makes a
            // wall of failures readable as one cause repeated N times rather
            // than N separate problems.
            failure.failureCode?.takeIf { it.isNotBlank() }?.let { code ->
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item.actionError?.takeIf { it.isNotBlank() }?.let { msg ->
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(Spacing.md))
                } else {
                    if (!isSuppressed) {
                        TextButton(onClick = onSuppress) {
                            Icon(
                                Icons.Outlined.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(Spacing.xs))
                            Text(stringResource(Res.string.admin_enrichment_failures_suppress))
                        }
                    }
                    TextButton(onClick = onRetry) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(Spacing.xs))
                        Text(stringResource(Res.string.admin_enrichment_failures_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String) {
    MetricPill(
        label,
        container = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        content = MaterialTheme.colorScheme.onSurface
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
