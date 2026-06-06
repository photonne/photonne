package com.photonne.app.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
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

/** Touch target around the handle — the ONLY draggable area. */
private val HandleTouchWidth = 48.dp
private val HandleTouchHeight = 64.dp

/** Visual handle: a vertical pill with up/down chevrons. */
private val HandleWidth = 28.dp
private val HandleHeight = 52.dp

/**
 * Fast-scroll scrubber pinned to the right edge, drawn as an overlay (it
 * takes no layout space from the grid). Appears while the list is scrolling
 * (or being scrubbed) and fades out after a pause. Only the handle itself is
 * draggable — a full-height touch strip would hijack ordinary scrolls near
 * the right edge the moment the scrubber became visible. Dragging jumps the
 * grid and shows a bubble with the month-year under the handle.
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
    // rememberUpdatedState: rows are REBUILT whenever a bucket's content
    // arrives. Keying the gesture (or the derived fraction) on them would
    // cancel an in-progress drag mid-scrub — the "frozen scrubber right
    // after a month loads" bug. The stable holders below let everything
    // read the freshest data without ever restarting.
    val prefix by rememberUpdatedState(remember(rows) { rowHeightPrefixSums(rows) })
    val labels by rememberUpdatedState(remember(rows) { scrubberRowLabels(rows) })
    val headerCount by rememberUpdatedState(headerItemCount)
    if (prefix.last() <= 0f) return

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    val scrollFraction by remember {
        derivedStateOf {
            val p = prefix
            val first = gridState.firstVisibleItemIndex - headerCount
            if (first <= 0 || p.size <= 1) {
                0f
            } else {
                val offsetDp = with(density) { gridState.firstVisibleItemScrollOffset.toDp() }.value
                ((p[first.coerceIn(0, p.lastIndex)] + offsetDp) / p.last())
                    .coerceIn(0f, 1f)
            }
        }
    }

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
            .width(200.dp) // room for the bubble; plain Boxes don't eat touches
            .graphicsLayer { this.alpha = alpha }
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val touchHeightPx = with(density) { HandleTouchHeight.toPx() }
        val usableTrackPx = (trackHeightPx - touchHeightPx).coerceAtLeast(1f)
        // Deferred state read: the offset lambda resolves during the
        // PLACEMENT phase, so the handle tracks every scroll frame without
        // recomposing this composable — reading the fraction in composition
        // is what made the handle visibly lag behind the list.
        val handleOffset: androidx.compose.ui.unit.Density.() -> IntOffset = {
            val f = if (isDragging) dragFraction else scrollFraction
            IntOffset(0, (usableTrackPx * f).roundToInt())
        }

        val usableTrack by rememberUpdatedState(usableTrackPx)
        // Drag-on-the-handle only. Delta-based: the pointer input sits on
        // the element that moves, so absolute positions would feed back
        // into themselves. Keyed on Unit on purpose — every mutable input
        // is read through a rememberUpdatedState holder, so the gesture
        // survives bucket loads, resizes and zoom changes mid-drag.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(handleOffset)
                .width(HandleTouchWidth)
                .height(HandleTouchHeight)
                .then(
                    if (visible) {
                        Modifier.pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFraction = scrollFraction
                                    isDragging = true
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragFraction = (dragFraction + dragAmount / usableTrack)
                                        .coerceIn(0f, 1f)
                                    val row = rowIndexForFraction(prefix, dragFraction)
                                    scrollJob?.cancel()
                                    scrollJob = scope.launch {
                                        gridState.scrollToItem(row + headerCount)
                                    }
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isDragging) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .width(HandleWidth)
                    .height(HandleHeight)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (isDragging) {
            val label = labels.getOrNull(rowIndexForFraction(prefix, dragFraction)).orEmpty()
            if (label.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(handleOffset)
                        .padding(end = HandleTouchWidth + 8.dp)
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
