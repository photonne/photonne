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
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val PREFETCH_THRESHOLD = 12

private val SPANISH_MONTHS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)

internal sealed interface TimelineEntry {
    data class Header(val key: String, val title: String) : TimelineEntry
    data class Cell(val item: TimelineItem, val index: Int) : TimelineEntry
}

private fun monthKey(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
}

private fun monthLabel(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val name = SPANISH_MONTHS.getOrElse(date.monthNumber - 1) { date.monthNumber.toString() }
    return "${name.replaceFirstChar { it.uppercase() }} ${date.year}"
}

internal fun groupTimelineEntries(items: List<TimelineItem>): List<TimelineEntry> {
    if (items.isEmpty()) return emptyList()
    val out = ArrayList<TimelineEntry>(items.size + 8)
    var lastKey: String? = null
    items.forEachIndexed { index, item ->
        val key = monthKey(item.fileCreatedAt)
        if (key != lastKey) {
            out += TimelineEntry.Header(key = key, title = monthLabel(item.fileCreatedAt))
            lastKey = key
        }
        out += TimelineEntry.Cell(item = item, index = index)
    }
    return out
}

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
    onLoadMore: () -> Unit = {}
) {
    val entries = remember(items) { groupTimelineEntries(items) }

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
        columns = GridCells.Adaptive(minSize = 110.dp),
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
                    is TimelineEntry.Cell -> entry.item.id
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
                    onClick = { onItemClick(entry.index) }
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
