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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
    cellSpacing: Dp = 2.dp
) {
    val shouldLoadMore by remember(hasMore, isAppending, isInitialLoading) {
        derivedStateOf {
            val total = state.layoutInfo.totalItemsCount
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - PREFETCH_THRESHOLD &&
                hasMore && !isAppending && !isInitialLoading
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    LazyColumn(
        state = state,
        verticalArrangement = Arrangement.spacedBy(cellSpacing),
        modifier = modifier.fillMaxSize()
    ) {
        rows.forEach { entry ->
            when (entry) {
                is JustifiedRowEntry.Header -> stickyHeader(key = "h:${entry.key}") {
                    StickyMonthHeader(
                        title = entry.title,
                        cover = entry.cover,
                        baseUrl = baseUrl
                    )
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
                            selectedIds = selectedIds
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cinematic sticky header: 64-dp band painting the group's first asset as
 * backdrop + dark vertical gradient so the white title stays readable on
 * any photo. Falls back to a translucent surface chip when no usable
 * cover exists (group's first item lacks a thumbnail or is local-only).
 */
@Composable
private fun StickyMonthHeader(
    title: String,
    cover: TimelineItem?,
    baseUrl: String
) {
    val usableCover = cover?.takeIf { it.hasThumbnails && !it.isLocalOnly }
    // Swallow taps on the band itself so the user doesn't accidentally
    // open whatever cell is scrolling behind the sticky header, and so
    // the band reads as chrome rather than an interactive asset.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* consume — header is chrome, not an asset */ }
            )
    ) {
        if (usableCover != null) {
            // Heavy blur turns the cover into an ambient color band
            // instead of an openable-looking photo.
            AsyncImage(
                model = "$baseUrl/api/assets/${usableCover.id}/thumbnail?size=Small",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 28.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.40f)
                            )
                        )
                    )
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 16.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            )
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
}

@Composable
private fun JustifiedCellsRow(
    row: JustifiedRow,
    baseUrl: String,
    spacing: Dp,
    onItemClick: (Int) -> Unit,
    onItemLongClick: ((Int) -> Unit)?,
    selectedIds: Set<String>
) {
    Row(
        modifier = Modifier
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

