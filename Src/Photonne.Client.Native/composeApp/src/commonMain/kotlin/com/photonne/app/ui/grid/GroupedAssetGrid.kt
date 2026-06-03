package com.photonne.app.ui.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
 * Renders the pre-packed [rows] as a justified Apple-Photos-style grid.
 * Each cell in a row shares the row's height; widths are weighted by
 * aspect ratio so the row exactly fills the container width.
 *
 * Month/year headers between groups are emitted via [stickyHeader] so the
 * native sticky behavior takes over — the current section's header pins
 * to the top until the next section's header pushes it up. This avoids
 * the previous overlay approach occluding the first row of each new
 * group.
 *
 * Internal indices stay in the original `items` order — click callbacks
 * resolve against whatever list the caller used to build the entries.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupedAssetGrid(
    rows: List<JustifiedRowEntry>,
    baseUrl: String,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    hasMore: Boolean = false,
    isAppending: Boolean = false,
    isInitialLoading: Boolean = false,
    onLoadMore: () -> Unit = {},
    selectedIds: Set<String> = emptySet(),
    onItemLongClick: ((Int) -> Unit)? = null,
    cellSpacing: Dp = 2.dp,
    header: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null
) {
    // Re-key on hasMore so the snapshotFlow restarts when pagination state
    // flips — the previous version keyed only on `state`, captured `shouldLoadMore`
    // (a delegate over a derivedStateOf whose lambda closed over `hasMore` /
    // `isAppending` / `isInitialLoading` from the FIRST composition) and
    // silently went stale on subsequent recompositions. `isAppending` /
    // `isInitialLoading` are intentionally NOT in the key set — the ViewModel
    // already guards re-entry, and including them would restart the flow
    // twice per load round-trip.
    //
    // Two trigger conditions, OR'd:
    //   (a) the last laid-out row is within PREFETCH_THRESHOLD of the end —
    //       the original "scrolling near the bottom" prefetch.
    //   (b) the laid-out content doesn't fill the viewport — covers the case
    //       where a page's worth of rows is shorter than the screen (large
    //       cells, fast viewport), so the user has nothing to scroll into
    //       and the threshold in (a) never triggers. Without this, the
    //       timeline gets stuck on page 1 until the user pinch-zooms (which
    //       repacks rows and accidentally satisfies (a)).
    LaunchedEffect(state, hasMore) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()
            val lastIndex = last?.index ?: -1
            val contentEnd = last?.let { it.offset + it.size } ?: 0
            val viewportEnd = info.viewportEndOffset
            total > 0 && (
                lastIndex >= total - PREFETCH_THRESHOLD ||
                (contentEnd in 1 until viewportEnd)
            )
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    LazyColumn(
        state = state,
        verticalArrangement = Arrangement.spacedBy(cellSpacing),
        modifier = modifier.fillMaxSize()
    ) {
        header?.invoke(this)
        rows.forEach { entry ->
            when (entry) {
                is JustifiedRowEntry.Header -> stickyHeader(key = "h:${entry.key}") {
                    StickyMonthHeader(title = entry.title)
                }
                is JustifiedRowEntry.Row -> {
                    val first = entry.row.cells.first()
                    item(key = "r:${assetCellKey(first.item, first.index)}") {
                        JustifiedCellsRow(
                            row = entry.row,
                            baseUrl = baseUrl,
                            spacing = cellSpacing,
                            onItemClick = onItemClick,
                            onItemLongClick = onItemLongClick,
                            selectedIds = selectedIds,
                            // Device-only rows are merged in after the gallery
                            // scan finishes; animateItem fades them in and
                            // slides their neighbours instead of hard-popping.
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Solid sticky date header: an opaque surface band with the group title.
 * Uniform across server-indexed and on-device groups (no photo backdrop /
 * gradient) so the timeline reads cleanly while scrolling.
 */
@Composable
private fun StickyMonthHeader(title: String) {
    // Swallow taps on the band itself so the user doesn't accidentally
    // open whatever cell is scrolling behind the sticky header, and so
    // the band reads as chrome rather than an interactive asset.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* consume — header is chrome, not an asset */ }
            )
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun JustifiedCellsRow(
    row: JustifiedRow,
    baseUrl: String,
    spacing: Dp,
    onItemClick: (Int) -> Unit,
    onItemLongClick: ((Int) -> Unit)?,
    selectedIds: Set<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(row.rowHeightDp.dp)
    ) {
        row.cells.forEachIndexed { i, cell ->
            if (i > 0) Spacer(Modifier.width(spacing))
            AssetGridCell(
                asset = cell.item,
                baseUrl = baseUrl,
                onClick = { onItemClick(cell.index) },
                onLongClick = onItemLongClick?.let { { it(cell.index) } },
                isSelected = cell.item.id in selectedIds,
                forceSquare = false,
                modifier = Modifier
                    .weight(aspectRatioOf(cell.item))
                    .fillMaxHeight()
            )
        }
    }
}

