package com.photonne.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Drives the immersive bottom-nav hide/show for a scrollable screen, mirroring
 * the Timeline: the chrome hides while the user scrolls DOWN and returns the
 * moment they scroll up, the list settles, or it's parked at the top.
 *
 * The scroll-state accessors are lambdas so the same logic works with either a
 * `LazyListState` or a `LazyGridState` — they expose the same property names
 * but share no common supertype.
 *
 * Reports every change through [onChromeVisibleChange], and restores the chrome
 * to visible when it leaves composition (switching sub-tab, opening a detail,
 * leaving the tab) so the nav never stays hidden on a screen that no longer
 * owns the scroll.
 */
@Composable
internal fun ImmersiveChromeEffect(
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
    isScrollInProgress: () -> Boolean,
    onChromeVisibleChange: (Boolean) -> Unit
) {
    var chromeVisible by remember { mutableStateOf(true) }
    // Small dead-zone so micro-scrolls and fling jitter don't flip the chrome.
    val thresholdPx = with(LocalDensity.current) { 10.dp.toPx() }
    val atTop by remember {
        derivedStateOf {
            firstVisibleItemIndex() == 0 && firstVisibleItemScrollOffset() == 0
        }
    }
    LaunchedEffect(thresholdPx) {
        var prevIndex = firstVisibleItemIndex()
        var prevOffset = firstVisibleItemScrollOffset()
        snapshotFlow { firstVisibleItemIndex() to firstVisibleItemScrollOffset() }
            .collect { (index, offset) ->
                // Rows have variable heights, so index can't be turned into
                // pixels: within one row compare the offset; when the first
                // visible index changes take its sign as the scroll direction.
                val delta = if (index != prevIndex) {
                    (index - prevIndex).toFloat() * (thresholdPx + 1f)
                } else {
                    (offset - prevOffset).toFloat()
                }
                if (delta > thresholdPx) chromeVisible = false
                else if (delta < -thresholdPx) chromeVisible = true
                prevIndex = index
                prevOffset = offset
            }
    }
    // Bring the chrome back a beat after scrolling stops.
    LaunchedEffect(Unit) {
        snapshotFlow { isScrollInProgress() }.collectLatest { scrolling ->
            if (!scrolling) {
                delay(160)
                chromeVisible = true
            }
        }
    }
    // Parked at the very top always shows the bar.
    LaunchedEffect(atTop) { if (atTop) chromeVisible = true }
    LaunchedEffect(chromeVisible) { onChromeVisibleChange(chromeVisible) }
    DisposableEffect(Unit) { onDispose { onChromeVisibleChange(true) } }
}
