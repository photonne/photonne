package com.photonne.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.data.settings.TimelineGrouping
import com.photonne.app.ui.grid.TimelineEntry
import com.photonne.app.ui.image.AssetThumbnailImage
import com.photonne.app.ui.grid.cellSizeFor
import com.photonne.app.ui.grid.columnCountFor
import com.photonne.app.ui.grid.groupTimelineEntries
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Samsung-Gallery-style continuous pinch zoom for the timeline.
 *
 * The grid is anchored at the top-left visible photo and simply SCALES from
 * there as the pinch grows — nothing translates as a block. At every grid
 * position the content cross-dissolves IN PLACE between the photo that occupies
 * that position in the level we're leaving and the photo that will occupy it in
 * the level we're heading to ("you're already seeing the next asset"). Where the
 * two are the same photo (e.g. the first cells) it just rescales. A trailing
 * cell that has no successor when columns shrink fades to black as it slides off
 * the right edge; a cell that newly appears fades in from black. A position with
 * both an old and a new photo is never blank — they blend.
 *
 * Per-frame cost is a scale (the cell size) plus per-cell alpha — no re-pack.
 */

private const val SPACING_DP = 2f
private const val HEADER_DP = 56f

/** Natural square cell size for a level at [widthDp]. */
internal fun levelNaturalCell(widthDp: Float, cellMinDp: Float): Float {
    val cols = columnCountFor(widthDp, cellMinDp, SPACING_DP)
    return cellSizeFor(widthDp, cols, SPACING_DP)
}

/** One grid position with its occupant in the lo (more-columns) and hi layouts. */
internal data class DissolveSlot(
    val groupIdx: Int,
    val row: Int,
    val col: Int,
    val lo: TimelineItem?,
    val hi: TimelineItem?
)

internal data class DissolvePlan(
    val titles: List<String>,
    /** Per group: row count in the lo (more-columns) / hi layout. */
    val rowsLo: IntArray,
    val rowsHi: IntArray,
    val slots: List<DissolveSlot>,
    /** Natural cell sizes (dp) at the lo / hi level — virtualCell rides between. */
    val cellLo: Float,
    val cellHi: Float,
    /** Slot kept fixed on screen (the anchor photo's position). */
    val anchorSlotIndex: Int,
    val baseUrl: String
)

/**
 * Builds the per-position dissolve plan for [items] (grouped by [grouping] —
 * always the level we're starting from, so headers never blink during the
 * gesture), transitioning between the column counts of [loCellMinDp] (more
 * columns) and [hiCellMinDp] (fewer). The first group starts at the anchor
 * photo, so the visible top-left stays put. When the bracket crosses into a
 * level with a different grouping, the far side is approximated with this
 * grouping for the transient frames and the live grid (with the real headers)
 * takes over on release.
 */
internal fun buildDissolvePlan(
    items: List<TimelineItem>,
    grouping: TimelineGrouping,
    loCellMinDp: Float,
    hiCellMinDp: Float,
    widthDp: Float,
    anchorId: String?,
    baseUrl: String
): DissolvePlan? {
    if (items.isEmpty() || widthDp <= 0f) return null
    val cLo = columnCountFor(widthDp, loCellMinDp, SPACING_DP)
    val cHi = columnCountFor(widthDp, hiCellMinDp, SPACING_DP)
    if (cLo <= 0 || cHi <= 0) return null
    val cellLo = cellSizeFor(widthDp, cLo, SPACING_DP)
    val cellHi = cellSizeFor(widthDp, cHi, SPACING_DP)

    val titles = ArrayList<String>()
    val groups = ArrayList<ArrayList<TimelineItem>>()
    groupTimelineEntries(items, grouping).forEach { e ->
        when (e) {
            is TimelineEntry.Header -> { titles.add(e.title); groups.add(ArrayList()) }
            is TimelineEntry.Cell -> {
                if (groups.isEmpty()) { titles.add(""); groups.add(ArrayList()) }
                groups[groups.size - 1].add(e.item)
            }
            is TimelineEntry.SkeletonBucket -> Unit
        }
    }
    if (groups.isEmpty()) return null

    val cMax = maxOf(cLo, cHi)
    val rowsLo = IntArray(groups.size)
    val rowsHi = IntArray(groups.size)
    val slots = ArrayList<DissolveSlot>()
    groups.forEachIndexed { g, photos ->
        val n = photos.size
        rowsLo[g] = ceil(n / cLo.toFloat()).toInt()
        rowsHi[g] = ceil(n / cHi.toFloat()).toInt()
        val rMax = maxOf(rowsLo[g], rowsHi[g])
        for (r in 0 until rMax) {
            for (c in 0 until cMax) {
                val loIdx = r * cLo + c
                val hiIdx = r * cHi + c
                val lo = if (c < cLo && loIdx < n) photos[loIdx] else null
                val hi = if (c < cHi && hiIdx < n) photos[hiIdx] else null
                if (lo != null || hi != null) slots += DissolveSlot(g, r, c, lo, hi)
            }
        }
    }
    // The anchor photo is the lo occupant of one slot (the position kept fixed
    // on screen). Fall back to its hi occupant, then to the first slot.
    val anchorSlotIndex = slots.indexOfFirst { it.lo?.id == anchorId }
        .let { if (it >= 0) it else slots.indexOfFirst { s -> s.hi?.id == anchorId } }
        .coerceAtLeast(0)
    return DissolvePlan(titles, rowsLo, rowsHi, slots, cellLo, cellHi, anchorSlotIndex, baseUrl)
}

/**
 * Renders a [DissolvePlan]. [virtualCell] (dp) is read live inside the measure
 * pass and each cell's alpha, so the whole zoom is a per-frame scale + alpha
 * update with no recomposition.
 */
@Composable
internal fun ZoomDissolveLayer(
    plan: DissolvePlan,
    virtualCell: () -> Float,
    /** Screen-y (dp) where the anchor photo's row top is kept fixed. */
    anchorScreenYdp: Float,
    /** Top-left mode: skip the first group's header (the base chrome shows it). */
    skipFirstHeader: Boolean,
    modifier: Modifier = Modifier
) {
    fun fractionOf(vc: Float): Float {
        val span = plan.cellHi - plan.cellLo
        if (span <= 0.0001f) return 0f
        return ((vc - plan.cellLo) / span).coerceIn(0f, 1f)
    }
    Layout(
        modifier = modifier,
        content = {
            // Headers first (one per group), then the cells.
            plan.titles.forEach { ReflowHeaderBand(it) }
            plan.slots.forEach { s ->
                Box(modifier = Modifier) {
                    s.lo?.let { item ->
                        AssetThumbnailImage(
                            item = item,
                            baseUrl = plan.baseUrl,
                            size = "Small",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = 1f - fractionOf(virtualCell()) }
                        )
                    }
                    s.hi?.let { item ->
                        AssetThumbnailImage(
                            item = item,
                            baseUrl = plan.baseUrl,
                            size = "Small",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = fractionOf(virtualCell()) }
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val vcDp = virtualCell()
        val f = fractionOf(vcDp)
        val vcPx = vcDp.dp.roundToPx().coerceAtLeast(1)
        val spacingPx = SPACING_DP.dp.roundToPx()
        // Cell-to-cell step INCLUDING the inter-cell gap — must match the real
        // grid (col * (cellSize + spacing)); otherwise cells render edge-to-edge
        // during the gesture and snap apart (the "refresh") when the base takes
        // over on release.
        val step = vcPx + spacingPx
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val headerHpx = HEADER_DP.dp.roundToPx()
        val g = plan.titles.size

        // Vertical layout (relative, origin 0): each group's content-row count
        // interpolates lo→hi so the grid grows/shrinks smoothly and lower groups
        // never blank or jump. Everything is then shifted so the anchor slot's
        // row top lands at [anchorScreenYdp] — content above it (focal/Year zoom)
        // gets negative y and is drawn too.
        val headerY = IntArray(g)
        val contentY = IntArray(g)
        val hasHeader = BooleanArray(g) { plan.titles[it].isNotBlank() }
        var y = 0
        for (gi in 0 until g) {
            val hH = if (hasHeader[gi]) headerHpx else 0
            if (gi == 0 && skipFirstHeader) {
                headerY[0] = -hH - spacingPx
            } else {
                headerY[gi] = y
                if (hasHeader[gi]) y += hH + spacingPx
            }
            contentY[gi] = y
            val effRows = plan.rowsLo[gi] + (plan.rowsHi[gi] - plan.rowsLo[gi]) * f
            y += (effRows * step).roundToInt()
        }
        val anchor = plan.slots.getOrNull(plan.anchorSlotIndex)
        val anchorNatY = if (anchor != null) {
            contentY[anchor.groupIdx] + anchor.row * step
        } else 0
        val shiftY = anchorScreenYdp.dp.roundToPx() - anchorNatY

        val headerPls = (0 until g).map {
            measurables[it].measure(Constraints.fixed(w, if (hasHeader[it]) headerHpx else 0))
        }
        val slotPls = ArrayList<Pair<androidx.compose.ui.layout.Placeable, Long>>(plan.slots.size)
        plan.slots.forEachIndexed { i, s ->
            val x = s.col * step
            val yy = contentY[s.groupIdx] + s.row * step + shiftY
            if (yy + vcPx >= 0 && yy <= h && x <= w) {
                val pl = measurables[g + i].measure(Constraints.fixed(vcPx, vcPx))
                slotPls += pl to packXY(x, yy)
            }
        }
        val firstHeader = if (skipFirstHeader) 1 else 0
        layout(w, h) {
            for (gi in firstHeader until g) {
                val hy = headerY[gi] + shiftY
                if (hasHeader[gi] && hy + headerHpx >= 0 && hy <= h) {
                    headerPls[gi].place(0, hy)
                }
            }
            slotPls.forEach { (pl, xy) -> pl.place(unpackX(xy), unpackY(xy)) }
        }
    }
}

private fun packXY(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
private fun unpackX(v: Long): Int = (v shr 32).toInt()
private fun unpackY(v: Long): Int = v.toInt()

@Composable
private fun ReflowHeaderBand(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
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
