package com.photonne.app.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.grid.JustifiedRowEntry
import com.photonne.app.ui.grid.formatLocalizedMonth
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Don't bother with a scrubber for content that barely scrolls. */
private const val MIN_ROWS_FOR_SCRUBBER = 40

/** Sticky-header band height — must match StickyMonthHeader. */
private const val HEADER_HEIGHT_DP = 56f

private val ThumbHeight = 48.dp
private val ThumbWidth = 6.dp

/**
 * Fast-scroll scrubber pinned to the right edge. Appears while the list is
 * scrolling (or being scrubbed) and fades out after a pause. Dragging the
 * thumb jumps the grid and shows a bubble with the month-year under the
 * thumb.
 *
 * The mapping between track fraction and list position is EXACT, not an
 * item-count approximation: with the bucket model every row's height is
 * known up front (headers 56dp, real rows their packed height, skeleton
 * rows the target height), so a prefix-sum array converts fraction ↔ row
 * in O(log n) — see [rowHeightPrefixSums] / [rowIndexForFraction].
 */
@Composable
internal fun TimelineScrubber(
    gridState: LazyListState,
    rows: List<JustifiedRowEntry>,
    /** Items the grid's optional header lambda emits before the rows. */
    headerItemCount: Int,
    modifier: Modifier = Modifier
) {
    if (rows.size < MIN_ROWS_FOR_SCRUBBER) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val prefix = remember(rows) { rowHeightPrefixSums(rows) }
    val labels = remember(rows) { scrubberRowLabels(rows) }
    val totalDp = prefix.last()
    if (totalDp <= 0f) return

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val scrollFraction by remember(prefix, headerItemCount) {
        derivedStateOf {
            val first = gridState.firstVisibleItemIndex - headerItemCount
            if (first <= 0) {
                0f
            } else {
                val offsetDp = with(density) { gridState.firstVisibleItemScrollOffset.toDp() }.value
                ((prefix[first.coerceIn(0, prefix.lastIndex)] + offsetDp) / totalDp)
                    .coerceIn(0f, 1f)
            }
        }
    }
    val fraction = if (isDragging) dragFraction else scrollFraction

    // Visible while scrolling/scrubbing; fades out shortly after.
    val active = isDragging || gridState.isScrollInProgress
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "scrubberAlpha")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(160.dp) // room for the bubble to the left of the thumb
            .graphicsLayer { this.alpha = alpha }
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(density) { ThumbHeight.toPx() }
        val thumbY = ((trackHeightPx - thumbHeightPx) * fraction).roundToInt()

        fun scrubTo(y: Float) {
            val usable = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
            val f = ((y - thumbHeightPx / 2) / usable).coerceIn(0f, 1f)
            dragFraction = f
            val row = rowIndexForFraction(prefix, f)
            scrollJob?.cancel()
            scrollJob = scope.launch {
                gridState.scrollToItem(row + headerItemCount)
            }
        }

        // Touch strip along the right edge. Only interactive while the
        // scrubber is visible, so a hidden scrubber never hijacks edge
        // drags meant for the grid.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(32.dp)
                .then(
                    if (visible) {
                        Modifier.pointerInput(prefix, headerItemCount) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    scrubTo(offset.y)
                                },
                                onVerticalDrag = { change, _ ->
                                    change.consume()
                                    scrubTo(change.position.y)
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        )

        Surface(
            shape = RoundedCornerShape(50),
            color = if (isDragging) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY) }
                .padding(end = 4.dp)
                .width(ThumbWidth)
                .height(ThumbHeight)
        ) {}

        if (isDragging) {
            val label = labels.getOrNull(rowIndexForFraction(prefix, dragFraction)).orEmpty()
            if (label.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, thumbY) }
                        .padding(end = 24.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Cumulative height (dp, spacing included) before each row entry;
 * `prefix[i]` is the content offset of row `i`, `prefix.last()` the total
 * content height. Exact because every entry's height is known up front.
 */
internal fun rowHeightPrefixSums(
    rows: List<JustifiedRowEntry>,
    headerHeightDp: Float = HEADER_HEIGHT_DP,
    spacingDp: Float = 2f
): FloatArray {
    val prefix = FloatArray(rows.size + 1)
    rows.forEachIndexed { i, entry ->
        val height = when (entry) {
            is JustifiedRowEntry.Header -> headerHeightDp
            is JustifiedRowEntry.Row -> entry.row.rowHeightDp
            is JustifiedRowEntry.SkeletonRow -> entry.rowHeightDp
        }
        prefix[i + 1] = prefix[i] + height + spacingDp
    }
    return prefix
}

/** Row index whose height span contains `fraction × totalHeight` (binary search). */
internal fun rowIndexForFraction(prefix: FloatArray, fraction: Float): Int {
    if (prefix.size <= 1) return 0
    val target = prefix.last() * fraction.coerceIn(0f, 1f)
    var lo = 0
    var hi = prefix.size - 2
    while (lo < hi) {
        val mid = (lo + hi) / 2
        if (prefix[mid + 1] <= target) lo = mid + 1 else hi = mid
    }
    return lo
}

/**
 * Bubble label for every row, carried forward from the preceding header's
 * key: "yyyy" → the year, "yyyy-MM(-dd)" → the localized month-year. Keys,
 * not titles, so Day-grouping headers still resolve to their month.
 */
internal fun scrubberRowLabels(rows: List<JustifiedRowEntry>): List<String> {
    var current = ""
    return rows.map { entry ->
        if (entry is JustifiedRowEntry.Header) current = labelForHeaderKey(entry.key)
        current
    }
}

private fun labelForHeaderKey(key: String): String {
    val parts = key.split('-')
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return key
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return year.toString()
    if (month < 1 || month > 12) return year.toString()
    return formatLocalizedMonth(LocalDate(year, month, 1))
}
