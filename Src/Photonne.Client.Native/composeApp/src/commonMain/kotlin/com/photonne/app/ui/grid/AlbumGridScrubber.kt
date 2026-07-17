package com.photonne.app.ui.grid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.main.chromeCapsuleBackdrop
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Don't bother with a scrubber until the album spans several screens. Gated on
 * cell count (not rows) so it needs no column count: ~60 cells is roughly 20
 * rows at 3 columns — the point where plain scrolling gets tedious.
 */
private const val MIN_CELLS_FOR_SCRUBBER = 60

/** How often the throttled applier teleports the grid while scrubbing (~15×/s). */
private const val SCRUB_APPLY_INTERVAL_MS = 64L

/** Touch target around the handle — the ONLY draggable area. */
private val HandleTouchWidth = 48.dp
private val HandleTouchHeight = 64.dp

/** Visual handle: a vertical pill with up/down chevrons. */
private val HandleWidth = 28.dp
private val HandleHeight = 52.dp

/**
 * Fast-scroll scrubber for the album's uniform [LazyVerticalGrid], drawn as a
 * right-edge overlay (it takes no layout space from the grid). Appears while
 * the grid scrolls (or is scrubbed) and fades out after a pause. Only the
 * handle itself is draggable — a full-height strip would hijack ordinary
 * scrolls near the right edge the moment the scrubber became visible.
 *
 * Because every cell is the same square, the fraction ↔ cell-index mapping is
 * exact arithmetic (no prefix sums needed): `LazyGridState.scrollToItem` takes
 * a per-item index and every cell is one item, so column packing never changes
 * the mapping — [headerCount] offsets past the hero at grid index 0.
 *
 * When [labelForCellIndex] is non-null (the album is date-sorted) a bubble next
 * to the handle shows the month-year of the topmost/target cell. In album order
 * dates aren't monotonic, so the host passes null and no bubble is drawn.
 */
@Composable
internal fun AlbumGridScrubber(
    gridState: LazyGridState,
    cellCount: Int,
    headerCount: Int = 1,
    labelForCellIndex: ((Int) -> String?)?,
    /** Reports the drag so the host can stand other chrome down while scrubbing. */
    onDraggingChange: (Boolean) -> Unit = {},
    /** Blur source (the album grid); falls back to a solid gray when null. */
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    if (cellCount < MIN_CELLS_FOR_SCRUBBER) return
    val scope = rememberCoroutineScope()
    val lastCell = (cellCount - 1).coerceAtLeast(0)
    // Read live inside the drag/label closures without restarting the gesture.
    val lastCellLatest by rememberUpdatedState(lastCell)
    val headerCountLatest by rememberUpdatedState(headerCount)
    val labelProvider by rememberUpdatedState(labelForCellIndex)
    val onDraggingChangeLatest by rememberUpdatedState(onDraggingChange)

    fun cellIndexForFraction(f: Float): Int =
        (f.coerceIn(0f, 1f) * lastCellLatest).roundToInt()

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Decoupled, throttled applier: the drag callback only writes a float (so
    // the thumb tracks the finger at full frame rate); this loop teleports the
    // grid to the LATEST fraction a few times per second.
    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        var lastIndex = -1
        while (true) {
            val index = headerCountLatest + cellIndexForFraction(dragFraction)
            if (index != lastIndex) {
                lastIndex = index
                runCatching { gridState.scrollToItem(index) }
            }
            delay(SCRUB_APPLY_INTERVAL_MS)
        }
    }

    // Row-granular reverse map: ignores the hero's tall height and the intra-row
    // scroll offset (same approximation the timeline tolerates for its memories
    // strip). Uniform cells make prefix sums / layoutInfo unnecessary.
    val scrollFraction by remember {
        derivedStateOf {
            val last = lastCellLatest
            val c = (gridState.firstVisibleItemIndex - headerCountLatest).coerceAtLeast(0)
            if (last == 0) 0f else (c.toFloat() / last).coerceIn(0f, 1f)
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
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "albumScrubberAlpha")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(200.dp) // room for the bubble; plain Boxes don't eat touches
            .graphicsLayer { this.alpha = alpha }
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val touchHeightPx = with(LocalDensity.current) { HandleTouchHeight.toPx() }
        val usableTrackPx = (trackHeightPx - touchHeightPx).coerceAtLeast(1f)
        // Deferred state read: the offset lambda resolves during the PLACEMENT
        // phase, so the handle tracks every scroll frame without recomposing.
        val handleOffset: androidx.compose.ui.unit.Density.() -> IntOffset = {
            val f = if (isDragging) dragFraction else scrollFraction
            IntOffset(0, (usableTrackPx * f).roundToInt())
        }

        val usableTrack by rememberUpdatedState(usableTrackPx)
        // Drag-on-the-handle only, delta-based (the pointer input sits on the
        // element that moves). Keyed on Unit; every mutable input is read through
        // a rememberUpdatedState holder so the gesture survives recompositions.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(handleOffset)
                .width(HandleTouchWidth)
                .height(HandleTouchHeight)
                .then(
                    if (visible) {
                        Modifier.pointerInput(Unit) {
                            fun endDrag() {
                                isDragging = false
                                onDraggingChangeLatest(false)
                                scope.launch {
                                    val index = headerCountLatest + cellIndexForFraction(dragFraction)
                                    runCatching { gridState.scrollToItem(index) }
                                }
                            }
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragFraction = scrollFraction
                                    isDragging = true
                                    onDraggingChangeLatest(true)
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragFraction = (dragFraction + dragAmount / usableTrack)
                                        .coerceIn(0f, 1f)
                                },
                                onDragEnd = ::endDrag,
                                onDragCancel = ::endDrag
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
                // Cristal esmerilado, gemelo del scrubber del timeline. El mango
                // pierde el realce primaryContainer al arrastrar: ahora va siempre
                // cristal, a juego con la píldora de fecha.
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .width(HandleWidth)
                    .height(HandleHeight)
            ) {
              Box {
                Box(Modifier.matchParentSize().chromeCapsuleBackdrop(hazeState = hazeState))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
              }
            }
        }

        // Date bubble next to the handle — only when the album is date-sorted.
        // While dragging it follows the finger's target cell; while scrolling it
        // tracks the topmost cell. derivedStateOf so it only recomposes when the
        // month label changes, not every scroll frame.
        val handleLabel by remember {
            derivedStateOf {
                val provider = labelProvider ?: return@derivedStateOf ""
                val idx = if (isDragging) {
                    cellIndexForFraction(dragFraction)
                } else {
                    (gridState.firstVisibleItemIndex - headerCountLatest).coerceAtLeast(0)
                }
                provider(idx).orEmpty()
            }
        }
        if (handleLabel.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(50),
                // Cristal esmerilado, a juego con el mango.
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(handleOffset)
                    // Nudge down so it sits centred against the handle rather
                    // than at its top edge (handle touch area is 64dp tall).
                    .offset(y = 18.dp)
                    .padding(end = HandleTouchWidth + 6.dp)
            ) {
              Box {
                Box(Modifier.matchParentSize().chromeCapsuleBackdrop(hazeState = hazeState))
                Text(
                    text = handleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }
        }
    }
}
