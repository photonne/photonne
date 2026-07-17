package com.photonne.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.timeline_scroll_to_top
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Floating "back to top" pill, shared by every screen with a long photo grid.
 * Follows the scrubber's rhythm — appears while the list is scrolling (once a
 * few screens deep) and fades out after a pause — so the overlay chrome comes
 * and goes as one.
 *
 * The scroll-state accessors are lambdas so the same pill works with either a
 * `LazyListState` or a `LazyGridState` — they expose the same property names
 * but share no common supertype. [onScrollToTop] is the caller's own jump: it
 * should teleport near the top and animate only the last stretch, since
 * animating the whole way composes thousands of rows for nothing.
 */
@Composable
internal fun ScrollToTopPill(
    firstVisibleItemIndex: () -> Int,
    isScrollInProgress: () -> Boolean,
    /**
     * Items scrolled past before the pill may appear. Counted in whatever the
     * host's lazy state indexes — rows for a list, cells for a grid — so each
     * caller picks the value that reads as "a few screens deep" for its layout.
     */
    minIndex: Int,
    onScrollToTop: suspend () -> Unit,
    /** Hidden while scrubbing or selecting, where it would just be noise. */
    suppressed: Boolean,
    /** Blur source (the grid); falls back to a solid gray when null. */
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val pastThreshold by remember(minIndex) {
        derivedStateOf { firstVisibleItemIndex() > minIndex }
    }
    val active = pastThreshold && isScrollInProgress()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(active, pastThreshold) {
        when {
            active -> visible = true
            !pastThreshold -> visible = false
            else -> {
                delay(1500)
                visible = false
            }
        }
    }
    AnimatedVisibility(
        visible = visible && !suppressed,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        Surface(
            onClick = { scope.launch { runCatching { onScrollToTop() } } },
            shape = RoundedCornerShape(50),
            // Cristal esmerilado como el resto del cromo: transparente + fondo de
            // blur, contenido en onSurface (blanco en oscuro).
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 2.dp
        ) {
          Box {
            Box(Modifier.matchParentSize().chromeCapsuleBackdrop(hazeState = hazeState))
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(Res.string.timeline_scroll_to_top),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
          }
        }
    }
}
