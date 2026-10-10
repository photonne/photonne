package com.photonne.app.ui.grid

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import kotlinx.datetime.LocalDate

/**
 * Uniform square grid layout: every cell is the same size, every row holds
 * the same number of columns, and the row height equals the rendered cell
 * width. Replaced the earlier justified (variable-aspect) layout because a
 * uniform grid makes each bucket's height a pure function of its count
 * (`ceil(count / columns) × cellSize`) — independent of content. That keeps
 * the scrubber exact, kills layout shift when a month hydrates, and removes
 * the need for a whole-library structure index just to estimate heights.
 *
 * Headers between groups become their own entries and don't share rows.
 * Each header carries its group's first cell as [TimelineRowEntry.Header.cover]
 * so the sticky overlay can paint that photo as backdrop without walking the
 * rows list a second time at render time.
 */
internal sealed interface TimelineRowEntry {
    data class Header(
        val key: String,
        val title: String,
        val cover: TimelineItem? = null,
        /** Group total shown trailing the title (Year view). */
        val count: Int? = null
    ) : TimelineRowEntry
    data class Row(val row: TimelineRow) : TimelineRowEntry

    /**
     * One row of an unloaded bucket's skeleton. [cellCount] of [cellsPerRow]
     * slots are filled (the last row of a month may be partial); height
     * equals the cell size, so the reserved space matches EXACTLY what the
     * real rows occupy once the bucket loads — no hydration shift.
     */
    data class SkeletonRow(
        val bucketKey: String,
        val rowIndex: Int,
        val cellCount: Int,
        val cellsPerRow: Int,
        val rowHeightDp: Float
    ) : TimelineRowEntry
}

internal data class TimelineRow(
    val cells: List<TimelineEntry.Cell>,
    val rowHeightDp: Float,
    /**
     * Columns this row is laid out against. Equals [cells].size for a full
     * row; a partial trailing row keeps the group's column count so the
     * render reserves empty slots and the cells keep the uniform size.
     */
    val columns: Int
)

/**
 * Number of columns that fit [containerWidthDp] at the given minimum cell
 * size — mirrors `GridCells.Adaptive(minSize)`: the largest n with
 * `minCellSize·n + spacing·(n-1) ≤ width`, i.e.
 * `floor((width + spacing) / (minCellSize + spacing))`, never below 1.
 */
internal fun columnCountFor(
    containerWidthDp: Float,
    minCellSizeDp: Float,
    spacingDp: Float
): Int {
    if (containerWidthDp <= 0f || minCellSizeDp <= 0f) return 0
    return ((containerWidthDp + spacingDp) / (minCellSizeDp + spacingDp))
        .toInt()
        .coerceAtLeast(1)
}

/**
 * Side of a square cell for [columns] columns: the width left after the
 * inter-cell spacing, split evenly. The row's height is this same value, so
 * every tile is exactly square.
 */
internal fun cellSizeFor(containerWidthDp: Float, columns: Int, spacingDp: Float): Float {
    if (columns <= 0) return 0f
    return (containerWidthDp - (columns - 1) * spacingDp) / columns
}

/**
 * Uniform row packer.
 *
 * Within each group (between headers) cells are chunked into rows of
 * [columnCountFor] columns; every row — full or trailing-partial — gets the
 * same square [cellSizeFor] height. Unloaded buckets ([TimelineEntry.SkeletonBucket])
 * reserve `ceil(count / columns)` skeleton rows of the identical height, so a
 * bucket's reserved scroll space never changes when its content arrives.
 */
internal fun packUniformRows(
    entries: List<TimelineEntry>,
    containerWidthDp: Float,
    minCellSizeDp: Float,
    spacingDp: Float
): List<TimelineRowEntry> {
    if (containerWidthDp <= 0f || minCellSizeDp <= 0f) return emptyList()
    if (entries.isEmpty()) return emptyList()

    val columns = columnCountFor(containerWidthDp, minCellSizeDp, spacingDp)
    val cellSize = cellSizeFor(containerWidthDp, columns, spacingDp)

    val out = ArrayList<TimelineRowEntry>(entries.size / columns + 8)
    val pending = ArrayList<TimelineEntry.Cell>(columns)

    fun flushRow() {
        if (pending.isEmpty()) return
        out += TimelineRowEntry.Row(
            TimelineRow(cells = pending.toList(), rowHeightDp = cellSize, columns = columns)
        )
        pending.clear()
    }

    entries.forEach { entry ->
        when (entry) {
            is TimelineEntry.Header -> {
                flushRow()
                // Cover left null here; filled in by the second pass below
                // once we know the next emitted cell.
                out += TimelineRowEntry.Header(entry.key, entry.title, count = entry.count)
            }
            is TimelineEntry.SkeletonBucket -> {
                flushRow()
                val rowCount = (entry.count + columns - 1) / columns
                repeat(rowCount) { i ->
                    out += TimelineRowEntry.SkeletonRow(
                        bucketKey = entry.bucketKey,
                        rowIndex = i,
                        cellCount = minOf(columns, entry.count - i * columns),
                        cellsPerRow = columns,
                        rowHeightDp = cellSize
                    )
                }
            }
            is TimelineEntry.Cell -> {
                pending += entry
                if (pending.size == columns) flushRow()
            }
        }
    }
    flushRow()
    // Second pass: stamp each header with its group's first cell so the
    // sticky overlay can render the cinematic backdrop without walking the
    // list again per scroll frame.
    return out.mapIndexed { idx, entry ->
        if (entry !is TimelineRowEntry.Header) return@mapIndexed entry
        var j = idx + 1
        var cover: TimelineItem? = null
        while (j < out.size) {
            when (val next = out[j]) {
                is TimelineRowEntry.Row -> {
                    cover = next.row.cells.firstOrNull()?.item
                    break
                }
                // Don't walk into the next group looking for a cover.
                is TimelineRowEntry.Header -> break
                // Skeleton rows have no cover; a later loaded bucket in the
                // same group (Year mode) can still provide one.
                is TimelineRowEntry.SkeletonRow -> j++
            }
        }
        entry.copy(cover = cover)
    }
}

/**
 * Caps every header group to [maxRows] row entries, dropping the rest. The
 * Year view uses this so a 10.000-photo year occupies the same height as a
 * 50-photo one — the per-year count in the header tells the user the group
 * is a sample, and clicking dives into the Month view for the full content.
 */
internal fun truncateRowsPerGroup(
    rows: List<TimelineRowEntry>,
    maxRows: Int
): List<TimelineRowEntry> {
    if (maxRows <= 0) return rows.filterIsInstance<TimelineRowEntry.Header>()
    val out = ArrayList<TimelineRowEntry>(rows.size)
    var rowsInGroup = 0
    rows.forEach { entry ->
        when (entry) {
            is TimelineRowEntry.Header -> {
                rowsInGroup = 0
                out += entry
            }
            else -> {
                if (rowsInGroup < maxRows) {
                    out += entry
                    rowsInGroup++
                }
            }
        }
    }
    return out
}

/**
 * Returns the index in [rows] of the header that matches [target] for the
 * given [grouping], or the closest older header as a fallback. -1 when
 * no matching header is present. Mirrors [findEntryIndexForDate] but
 * operates on packed rows so timeline scrolling can target the correct
 * `LazyListState` index after the rewrite.
 */
internal fun findRowIndexForDate(
    rows: List<TimelineRowEntry>,
    target: LocalDate,
    grouping: TimelineGrouping
): Int {
    if (rows.isEmpty()) return -1
    val targetKey = targetKeyForRow(target, grouping)
    var fallback = -1
    rows.forEachIndexed { index, entry ->
        if (entry is TimelineRowEntry.Header) {
            if (entry.key == targetKey) return index
            if (fallback == -1 && entry.key <= targetKey) fallback = index
        }
    }
    return fallback
}

/**
 * Index of the row containing [assetId], or -1. Used by the Year→Month
 * click-to-zoom to land exactly on the clicked asset once its bucket's
 * content is in the rows (until then the month header is the anchor).
 */
internal fun findRowIndexForAsset(rows: List<TimelineRowEntry>, assetId: String): Int {
    rows.forEachIndexed { index, entry ->
        if (entry is TimelineRowEntry.Row && entry.row.cells.any { it.item.id == assetId }) {
            return index
        }
    }
    return -1
}

private fun targetKeyForRow(target: LocalDate, grouping: TimelineGrouping): String {
    val mm = target.monthNumber.toString().padStart(2, '0')
    val dd = target.dayOfMonth.toString().padStart(2, '0')
    return when (grouping) {
        TimelineGrouping.Year -> target.year.toString()
        TimelineGrouping.Month -> "${target.year}-$mm"
        TimelineGrouping.Day -> "${target.year}-$mm-$dd"
    }
}
