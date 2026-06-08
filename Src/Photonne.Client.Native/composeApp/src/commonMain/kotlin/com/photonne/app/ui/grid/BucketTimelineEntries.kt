package com.photonne.app.ui.grid

import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import com.photonne.app.data.timeline.TimelineBucketState
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Server-bucket month key ("yyyy-MM") of an instant. Uses UTC on purpose:
 * the server groups buckets by the stored CapturedAt instant, so the client
 * must derive the same key or items would render under a neighbouring
 * month's header near midnight at month boundaries. Day-level headers inside
 * a loaded bucket still use the device timezone (cosmetic, see
 * docs/timeline-buckets.md).
 */
internal fun bucketKeyOf(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.UTC).date
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
}

/**
 * The flattened, render-ready form of the bucket timeline.
 *
 * [mergedItems] is the concatenation of every loaded bucket's items (server
 * merged with device-local), in display order — cell indices in [entries]
 * point into it, exactly like the old flat-list contract, so the click
 * callbacks and the detail pager keep working unchanged.
 */
internal data class BucketEntriesResult(
    val entries: List<TimelineEntry>,
    val mergedItems: List<TimelineItem>,
    /** Loaded buckets' slices of [mergedItems], in display order. */
    val loadedRanges: List<BucketRange>,
    /** Every renderable bucket key, newest first (loaded or not). */
    val bucketOrder: List<String>
)

/**
 * One loaded bucket's slice of `mergedItems`. Ranges sharing a [runId] are
 * temporally contiguous (no unloaded month between them), so a detail pager
 * opened inside the run can swipe across its whole span without silently
 * skipping months.
 */
internal data class BucketRange(
    val bucketKey: String,
    val range: IntRange,
    val runId: Int
)

/**
 * Builds the timeline entry list from bucket states plus device-local items.
 *
 * - Loaded buckets contribute real cells (server items merged with that
 *   month's local items via [mergeTimelineWithLocal]).
 * - Unloaded buckets contribute a single [TimelineEntry.SkeletonBucket]
 *   whose count reserves EXACT height (uniform grid → height is a pure
 *   function of the count, so hydration never shifts scroll). Local items of
 *   an unloaded month stay hidden until it loads — dedup against the server
 *   needs the server's items.
 * - Months that exist only on the device become synthetic loaded buckets.
 * - Headers follow [grouping]: one per year, one per month (from the bucket
 *   key, so header and bucket can never disagree), or one per local day
 *   inside loaded buckets.
 */
internal fun buildBucketEntries(
    buckets: List<TimelineBucketState>,
    localItems: List<TimelineItem>,
    grouping: TimelineGrouping
): BucketEntriesResult {
    val localByMonth = localItems.groupBy { bucketKeyOf(it.fileCreatedAt) }
    val serverByKey = buckets.associateBy { it.key }
    val allKeys = (buckets.map { it.key } + localByMonth.keys)
        .distinct()
        .sortedDescending()

    val entries = ArrayList<TimelineEntry>(allKeys.size * 4)
    val merged = ArrayList<TimelineItem>()
    val ranges = ArrayList<BucketRange>()
    val order = ArrayList<String>(allKeys.size)
    var lastHeaderKey: String? = null
    var runId = 0

    fun emitGroupHeader(key: String, title: String) {
        if (key != lastHeaderKey) {
            entries += TimelineEntry.Header(key = key, title = title)
            lastHeaderKey = key
        }
    }

    // Emits one bucket's cells (plus day headers when needed).
    fun emitBucketCells(bucketKey: String, items: List<TimelineItem>) {
        order += bucketKey
        val start = merged.size

        fun emitCell(item: TimelineItem) {
            merged += item
            entries += TimelineEntry.Cell(item = item, index = merged.size - 1)
        }

        if (grouping == TimelineGrouping.Day) {
            // Day headers derive from the items themselves (device timezone).
            var lastDayKey: String? = null
            items.forEach { item ->
                val dayKey = dayKeyOf(item.fileCreatedAt)
                if (dayKey != lastDayKey) {
                    emitGroupHeader(key = dayKey, title = dayLabelOf(item.fileCreatedAt))
                    lastDayKey = dayKey
                }
                emitCell(item)
            }
        } else {
            emitGroupHeader(
                key = monthOrYearKey(bucketKey, grouping),
                title = monthOrYearLabel(bucketKey, grouping)
            )
            items.forEach(::emitCell)
        }

        ranges += BucketRange(bucketKey, start until merged.size, runId)
    }

    for (bucketKey in allKeys) {
        val server = serverByKey[bucketKey]
        val local = localByMonth[bucketKey].orEmpty()

        if (server != null && !server.isLoaded) {
            if (server.count == 0) continue
            order += bucketKey
            emitGroupHeader(
                key = monthOrYearKey(bucketKey, grouping),
                title = monthOrYearLabel(bucketKey, grouping)
            )
            entries += TimelineEntry.SkeletonBucket(bucketKey = bucketKey, count = server.count)
            // An unloaded month interrupts pager continuity.
            runId++
            continue
        }

        val items = when {
            server?.items != null -> mergeTimelineWithLocal(server.items, local)
            else -> local.sortedByDescending { it.fileCreatedAt }
        }
        if (items.isEmpty()) continue
        emitBucketCells(bucketKey = bucketKey, items = items)
    }

    return BucketEntriesResult(
        entries = entries,
        mergedItems = merged,
        loadedRanges = ranges,
        bucketOrder = order
    )
}

/**
 * Builds the compressed Year-view entry list from per-year summaries: one
 * header per year (carrying the year's total count) followed by the sampled
 * items as cells. No skeletons and no bucket plumbing — the summaries are
 * self-contained, so [BucketEntriesResult.loadedRanges]/[BucketEntriesResult.bucketOrder]
 * stay empty (Year-view clicks navigate to the Month view, never open the
 * detail pager, and bucket visibility loading is disabled at that level).
 */
internal fun buildYearSummaryEntries(
    summaries: List<com.photonne.app.data.models.TimelineYearSummary>
): BucketEntriesResult {
    val entries = ArrayList<TimelineEntry>()
    val merged = ArrayList<TimelineItem>()
    summaries
        .sortedByDescending { it.year } // newest first, defensively re-pinned
        .forEach { summary ->
            if (summary.items.isEmpty()) return@forEach
            entries += TimelineEntry.Header(
                key = summary.year.toString(),
                title = summary.year.toString(),
                count = summary.count
            )
            summary.items.forEach { item ->
                merged += item
                entries += TimelineEntry.Cell(item = item, index = merged.size - 1)
            }
        }
    return BucketEntriesResult(
        entries = entries,
        mergedItems = merged,
        loadedRanges = emptyList(),
        bucketOrder = emptyList()
    )
}

/**
 * The contiguous loaded slice of `mergedItems` around [mergedIndex]: all
 * ranges sharing the clicked range's run. Returns the slice plus its start
 * offset so callers can rebase the clicked index; null when the index isn't
 * inside any loaded range (can't happen from a real cell click).
 */
internal fun contiguousRunAround(
    mergedIndex: Int,
    result: BucketEntriesResult
): Pair<List<TimelineItem>, Int>? {
    val clicked = result.loadedRanges.firstOrNull { mergedIndex in it.range } ?: return null
    val run = result.loadedRanges.filter { it.runId == clicked.runId }
    val start = run.first().range.first
    val end = run.last().range.last
    return result.mergedItems.subList(start, end + 1) to start
}

/**
 * Expands the visible bucket keys with their immediate neighbours in
 * [BucketEntriesResult.bucketOrder] so scrolling into a month never starts
 * from a cold skeleton. Preserves order, drops duplicates.
 */
internal fun expandWithNeighborBuckets(
    visible: List<String>,
    bucketOrder: List<String>
): List<String> {
    if (visible.isEmpty() || bucketOrder.isEmpty()) return visible
    val indexByKey = bucketOrder.withIndex().associate { (i, k) -> k to i }
    val out = LinkedHashSet<String>()
    visible.forEach { key ->
        val i = indexByKey[key] ?: return@forEach
        out += key
        bucketOrder.getOrNull(i - 1)?.let { out += it }
        bucketOrder.getOrNull(i + 1)?.let { out += it }
    }
    return out.toList()
}

private fun monthOrYearKey(bucketKey: String, grouping: TimelineGrouping): String =
    when (grouping) {
        TimelineGrouping.Year -> bucketKey.substringBefore('-')
        // Day grouping only lands here for unloaded buckets, whose day
        // breakdown is unknown — fall back to the month header.
        TimelineGrouping.Month, TimelineGrouping.Day -> bucketKey
    }

private fun monthOrYearLabel(bucketKey: String, grouping: TimelineGrouping): String {
    val year = bucketKey.substringBefore('-').toIntOrNull() ?: return bucketKey
    if (grouping == TimelineGrouping.Year) return year.toString()
    val month = bucketKey.substringAfter('-').toIntOrNull() ?: return bucketKey
    return formatLocalizedMonth(LocalDate(year, month, 1))
}
