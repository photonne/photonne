package com.photonne.app.ui.grid

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import kotlinx.datetime.LocalDate

/**
 * Apple-Photos-style justified row layout: cells in a row share the same
 * height; their widths are proportional to their aspect ratios; each row
 * fills the container width exactly.
 *
 * Headers between groups become their own entries and don't share rows.
 * Each header carries its group's first cell as [Header.cover] so the
 * cinematic sticky overlay can paint that photo as backdrop without
 * walking the rows list a second time at render time.
 */
internal sealed interface JustifiedRowEntry {
    data class Header(
        val key: String,
        val title: String,
        val cover: TimelineItem? = null,
        /** Group total shown trailing the title (Year view). */
        val count: Int? = null
    ) : JustifiedRowEntry
    data class Row(val row: JustifiedRow) : JustifiedRowEntry

    /**
     * One row of an unloaded bucket's skeleton. [cellCount] of [cellsPerRow]
     * slots are filled (the last row of a month may be partial); height
     * equals the target row height so the reserved space matches what the
     * real justified rows will occupy, give or take aspect-ratio variance.
     */
    data class SkeletonRow(
        val bucketKey: String,
        val rowIndex: Int,
        val cellCount: Int,
        val cellsPerRow: Int,
        val rowHeightDp: Float
    ) : JustifiedRowEntry
}

internal data class JustifiedRow(
    val cells: List<TimelineEntry.Cell>,
    val rowHeightDp: Float
)

private const val MIN_ASPECT = 0.5f      // 1:2 portrait
private const val MAX_ASPECT = 2.5f      // 5:2 landscape — clamps panoramas
private const val FALLBACK_ASPECT = 1.0f // square when dimensions unknown

/**
 * Aspect ratio used to pack a [TimelineItem] in the row layout. Clamps to
 * a sane range so a single 10:1 panorama (or a 1:5 cropped portrait)
 * can't squash an entire row to a sliver. Items without dimensions fall
 * back to square.
 */
internal fun aspectRatioOf(item: TimelineItem): Float {
    val w = item.width
    val h = item.height
    if (w == null || h == null || w <= 0 || h <= 0) return FALLBACK_ASPECT
    val raw = w.toFloat() / h.toFloat()
    return raw.coerceIn(MIN_ASPECT, MAX_ASPECT)
}

/**
 * Greedy row packer.
 *
 * Within each group (between headers) we accumulate cells, projecting
 * the resulting row height if we were to flush. As soon as adding a new
 * cell would push the projected height **below** the target, we flush
 * the row *including* that cell — the row height then equals the
 * projected (shrunken) value. Trailing cells with no more incoming
 * items (i.e., before the next header or at the end of the list) flush
 * at the target height instead of stretching to fill, which avoids
 * dramatically taller last-rows.
 */
internal fun packJustifiedRows(
    entries: List<TimelineEntry>,
    containerWidthDp: Float,
    targetRowHeightDp: Float,
    spacingDp: Float
): List<JustifiedRowEntry> {
    if (containerWidthDp <= 0f || targetRowHeightDp <= 0f) return emptyList()
    if (entries.isEmpty()) return emptyList()

    val out = ArrayList<JustifiedRowEntry>(entries.size / 4 + 8)
    val pending = ArrayList<TimelineEntry.Cell>()

    fun flushPartial() {
        // Trailing row of a group: keep target row height, don't stretch
        // to fill (would make a single trailing landscape giant).
        if (pending.isEmpty()) return
        out += JustifiedRowEntry.Row(
            JustifiedRow(cells = pending.toList(), rowHeightDp = targetRowHeightDp)
        )
        pending.clear()
    }

    entries.forEach { entry ->
        when (entry) {
            is TimelineEntry.Header -> {
                flushPartial()
                // Cover left null here; filled in by the second pass
                // below once we know the next emitted cell.
                out += JustifiedRowEntry.Header(entry.key, entry.title, count = entry.count)
            }
            is TimelineEntry.SkeletonBucket -> {
                flushPartial()
                // Square-cell estimate: how many target-height cells fit a
                // row. The real justified layout will differ a little per
                // row (aspect ratios), but stays deterministic per count.
                val cellsPerRow = (containerWidthDp / (targetRowHeightDp + spacingDp))
                    .toInt().coerceAtLeast(1)
                val rowCount = (entry.count + cellsPerRow - 1) / cellsPerRow
                repeat(rowCount) { i ->
                    out += JustifiedRowEntry.SkeletonRow(
                        bucketKey = entry.bucketKey,
                        rowIndex = i,
                        cellCount = minOf(cellsPerRow, entry.count - i * cellsPerRow),
                        cellsPerRow = cellsPerRow,
                        rowHeightDp = targetRowHeightDp
                    )
                }
            }
            is TimelineEntry.Cell -> {
                pending += entry
                val totalAspect = pending.fold(0f) { acc, c -> acc + aspectRatioOf(c.item) }
                val spacingTotal = (pending.size - 1).coerceAtLeast(0) * spacingDp
                val availableWidth = containerWidthDp - spacingTotal
                val projectedHeight = if (totalAspect > 0f)
                    availableWidth / totalAspect
                else targetRowHeightDp

                if (projectedHeight <= targetRowHeightDp) {
                    // Row would be at or below target — flush including this cell.
                    out += JustifiedRowEntry.Row(
                        JustifiedRow(
                            cells = pending.toList(),
                            rowHeightDp = projectedHeight.coerceAtLeast(targetRowHeightDp * 0.55f)
                        )
                    )
                    pending.clear()
                }
            }
        }
    }
    flushPartial()
    // Second pass: stamp each header with its group's first cell so the
    // sticky overlay can render the cinematic backdrop without walking
    // the list again per scroll frame.
    return out.mapIndexed { idx, entry ->
        if (entry !is JustifiedRowEntry.Header) return@mapIndexed entry
        var j = idx + 1
        var cover: TimelineItem? = null
        while (j < out.size) {
            when (val next = out[j]) {
                is JustifiedRowEntry.Row -> {
                    cover = next.row.cells.firstOrNull()?.item
                    break
                }
                // Don't walk into the next group looking for a cover.
                is JustifiedRowEntry.Header -> break
                // Skeleton rows have no cover; a later loaded bucket in the
                // same group (Year mode) can still provide one.
                is JustifiedRowEntry.SkeletonRow -> j++
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
    rows: List<JustifiedRowEntry>,
    maxRows: Int
): List<JustifiedRowEntry> {
    if (maxRows <= 0) return rows.filterIsInstance<JustifiedRowEntry.Header>()
    val out = ArrayList<JustifiedRowEntry>(rows.size)
    var rowsInGroup = 0
    rows.forEach { entry ->
        when (entry) {
            is JustifiedRowEntry.Header -> {
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
    rows: List<JustifiedRowEntry>,
    target: LocalDate,
    grouping: TimelineGrouping
): Int {
    if (rows.isEmpty()) return -1
    val targetKey = targetKeyForRow(target, grouping)
    var fallback = -1
    rows.forEachIndexed { index, entry ->
        if (entry is JustifiedRowEntry.Header) {
            if (entry.key == targetKey) return index
            if (fallback == -1 && entry.key <= targetKey) fallback = index
        }
    }
    return fallback
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
