package com.photonne.app.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val PREFETCH_THRESHOLD = 12

/**
 * Sentinel returned by the server for assets that exist on disk but
 * haven't been indexed yet (timeline endpoint, "Copied" sync status).
 * Multiple such rows can come back in the same page, so the grid must
 * fall back to a path-based key to avoid duplicate-key crashes.
 */
private const val EMPTY_ASSET_ID = "00000000-0000-0000-0000-000000000000"

internal fun assetCellKey(item: TimelineItem, index: Int): String =
    if (item.id.isEmpty() || item.id == EMPTY_ASSET_ID) {
        "u:$index:${item.fullPath}"
    } else item.id

internal sealed interface TimelineEntry {
    data class Header(val key: String, val title: String) : TimelineEntry
    data class Cell(val item: TimelineItem, val index: Int) : TimelineEntry
}

internal fun monthKeyOf(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
}

internal fun monthLabelOf(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return formatLocalizedMonth(date)
}

internal fun dayKeyOf(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val mm = date.monthNumber.toString().padStart(2, '0')
    val dd = date.dayOfMonth.toString().padStart(2, '0')
    return "${date.year}-$mm-$dd"
}

internal fun dayLabelOf(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return formatLocalizedDay(date)
}

internal fun yearKeyOf(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.year.toString()

internal fun yearLabelOf(instant: Instant): String = yearKeyOf(instant)

private fun keyOf(instant: Instant, grouping: TimelineGrouping): String = when (grouping) {
    TimelineGrouping.Year -> yearKeyOf(instant)
    TimelineGrouping.Month -> monthKeyOf(instant)
    TimelineGrouping.Day -> dayKeyOf(instant)
}

private fun labelOf(instant: Instant, grouping: TimelineGrouping): String = when (grouping) {
    TimelineGrouping.Year -> yearLabelOf(instant)
    TimelineGrouping.Month -> monthLabelOf(instant)
    TimelineGrouping.Day -> dayLabelOf(instant)
}

internal fun groupTimelineEntries(
    items: List<TimelineItem>,
    grouping: TimelineGrouping = TimelineGrouping.Month
): List<TimelineEntry> {
    if (items.isEmpty()) return emptyList()
    val out = ArrayList<TimelineEntry>(items.size + 8)
    var lastKey: String? = null
    items.forEachIndexed { index, item ->
        val key = keyOf(item.fileCreatedAt, grouping)
        if (key != lastKey) {
            out += TimelineEntry.Header(
                key = key,
                title = labelOf(item.fileCreatedAt, grouping)
            )
            lastKey = key
        }
        out += TimelineEntry.Cell(item = item, index = index)
    }
    return out
}

/**
 * Merges server timeline items with locally-known device items (both
 * already typed as [TimelineItem] — the latter carry
 * `localUri`/`localThumbnailModel`). The merge:
 *
 * 1. Dedups local items against server items by SHA-256 (when both
 *    sides know it) and, as a fallback, by `(fileName, fileSize)`.
 *    This stops Unknown device entries from double-rendering when the
 *    server already has the photo.
 * 2. Interleaves the survivors with the server list, preserving
 *    descending `fileCreatedAt` order so click callbacks resolve back
 *    to the right entry via the merged list's index.
 */
internal fun mergeTimelineWithLocal(
    server: List<TimelineItem>,
    local: List<TimelineItem>
): List<TimelineItem> {
    if (local.isEmpty()) return server
    val serverChecksums = server.mapNotNullTo(HashSet()) {
        it.checksum?.takeIf { c -> c.isNotBlank() }
    }
    val serverDedupKeys = server.mapTo(HashSet()) { dedupKey(it.fileName, it.fileSize) }
    val dedupedLocal = local.filter { item ->
        val checksum = item.checksum
        if (!checksum.isNullOrBlank() && checksum in serverChecksums) return@filter false
        dedupKey(item.fileName, item.fileSize) !in serverDedupKeys
    }
    if (dedupedLocal.isEmpty()) return server
    if (server.isEmpty()) return dedupedLocal.sortedByDescending { it.fileCreatedAt }
    // Both lists are descending; a manual merge keeps it O(n+m).
    val merged = ArrayList<TimelineItem>(server.size + dedupedLocal.size)
    var i = 0
    var j = 0
    val localSorted = dedupedLocal.sortedByDescending { it.fileCreatedAt }
    while (i < server.size && j < localSorted.size) {
        if (server[i].fileCreatedAt >= localSorted[j].fileCreatedAt) {
            merged += server[i]; i++
        } else {
            merged += localSorted[j]; j++
        }
    }
    while (i < server.size) { merged += server[i]; i++ }
    while (j < localSorted.size) { merged += localSorted[j]; j++ }
    return merged
}

private fun dedupKey(fileName: String, fileSize: Long): String = "$fileName|$fileSize"

/**
 * Finds the grid-entry index of the header that matches [target] (or the
 * closest older bucket) for the given [grouping]. Used both by the
 * "jump to date" affordance and to re-anchor the grid when the user
 * switches zoom levels. Returns -1 when the timeline is empty or no
 * matching header exists.
 */
internal fun findEntryIndexForDate(
    entries: List<TimelineEntry>,
    target: LocalDate,
    grouping: TimelineGrouping
): Int {
    if (entries.isEmpty()) return -1
    val targetKey = targetKeyFor(target, grouping)
    var fallback = -1
    entries.forEachIndexed { index, entry ->
        if (entry is TimelineEntry.Header) {
            if (entry.key == targetKey) return index
            // Headers are emitted in newest-first order (timeline descends).
            // We pick the first header whose key is older or equal as a fallback.
            if (fallback == -1 && entry.key <= targetKey) fallback = index
        }
    }
    return fallback
}

private fun targetKeyFor(target: LocalDate, grouping: TimelineGrouping): String {
    val mm = target.monthNumber.toString().padStart(2, '0')
    val dd = target.dayOfMonth.toString().padStart(2, '0')
    return when (grouping) {
        TimelineGrouping.Year -> target.year.toString()
        TimelineGrouping.Month -> "${target.year}-$mm"
        TimelineGrouping.Day -> "${target.year}-$mm-$dd"
    }
}

/** Backwards-compatible Month-grouping shim for the jump-to-date flow. */
internal fun findEntryIndexForMonth(entries: List<TimelineEntry>, target: LocalDate): Int =
    findEntryIndexForDate(entries, target, TimelineGrouping.Month)

/**
 * Same square grid as [AssetGrid] but with non-sticky month/year
 * headers between groups, used by the timeline. Headers span the full
 * row via [GridItemSpan].
 */
@Composable
fun GroupedAssetGrid(
    items: List<TimelineItem>,
    baseUrl: String,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    hasMore: Boolean = false,
    isAppending: Boolean = false,
    isInitialLoading: Boolean = false,
    onLoadMore: () -> Unit = {},
    selectedIds: Set<String> = emptySet(),
    onItemLongClick: ((Int) -> Unit)? = null,
    grouping: TimelineGrouping = TimelineGrouping.Month,
    cellMinSize: Dp = 110.dp
) {
    val entries = remember(items, grouping) { groupTimelineEntries(items, grouping) }

    val shouldLoadMore by remember(hasMore, isAppending, isInitialLoading) {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - PREFETCH_THRESHOLD &&
                hasMore && !isAppending && !isInitialLoading
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = cellMinSize),
        state = gridState,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = entries,
            key = { entry ->
                when (entry) {
                    is TimelineEntry.Header -> "h:${entry.key}"
                    is TimelineEntry.Cell -> assetCellKey(entry.item, entry.index)
                }
            },
            span = { entry ->
                if (entry is TimelineEntry.Header) GridItemSpan(maxLineSpan)
                else GridItemSpan(1)
            }
        ) { entry ->
            when (entry) {
                is TimelineEntry.Header -> MonthHeader(entry.title)
                is TimelineEntry.Cell -> AssetGridCell(
                    asset = entry.item,
                    baseUrl = baseUrl,
                    onClick = { onItemClick(entry.index) },
                    onLongClick = onItemLongClick?.let { { it(entry.index) } },
                    isSelected = entry.item.id in selectedIds
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
