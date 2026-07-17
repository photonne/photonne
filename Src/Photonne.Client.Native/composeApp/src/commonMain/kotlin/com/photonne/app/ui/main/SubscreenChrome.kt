package com.photonne.app.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import dev.chrisbanes.haze.HazeState
import org.jetbrains.compose.resources.stringResource

/**
 * Height the floating subscreen chrome occupies at the top: the status bar plus
 * a standard app-bar height. The scrolling content does NOT lose this space —
 * it flows underneath — but it must reserve it as top `contentPadding` so the
 * first row isn't parked behind the bar at rest, exactly like the timeline.
 */
@Composable
internal fun subscreenChromeReservedTop(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

/**
 * Everything a subscreen's chrome needs to follow its scrolling content: the
 * hide-on-scroll rhythm, the docked/floating swap, and the back-to-top pill.
 * Lambdas rather than a state object because `LazyListState` and `LazyGridState`
 * expose the same property names but share no common supertype.
 */
internal class SubscreenScroll(
    val firstVisibleItemIndex: () -> Int,
    val firstVisibleItemScrollOffset: () -> Int,
    val isScrollInProgress: () -> Boolean,
    /**
     * Items scrolled past before the back-to-top pill appears, counted in
     * whatever the lazy state indexes — rows for a list, cells for a grid.
     */
    val scrollToTopMinIndex: Int,
    /** Should teleport near the top and animate only the last stretch. */
    val onScrollToTop: suspend () -> Unit
)

/**
 * Floating top chrome for a "Más" subscreen, mirroring the timeline's: docked
 * and opaque at the very top, dissolving into frosted capsules — back + title on
 * the leading side, actions on the trailing one — once the content is scrolled,
 * so it reads through them. Hides while scrolling down and returns on scroll up.
 *
 * The title rides *inside* the leading capsule rather than fading out with the
 * dock as the timeline's wordmark does. The timeline can afford to drop it: its
 * photos say where you are. These screens can't — half of them have no actions,
 * so dropping it left a lone back arrow in the corner naming nothing, and Escenas
 * vs. Objetos are two grids of tiles that look the same.
 *
 * The docked state is a plain opaque backdrop *behind* the same capsules, not a
 * real `TopAppBar`. Two rows of icons (one docked, one floating) is what makes
 * them slide sideways on every swap: M3 insets a `TopAppBar`'s navigation icon
 * by its own private 4.dp while the capsule's outer + inner margins add up to
 * 10.dp. Here the icons are rendered once and only the backdrop crossfades.
 *
 * Drop into a `fillMaxSize` [Box] that already holds the scrolling content; the
 * content should reserve [subscreenChromeReservedTop] as its top padding and be
 * the [hazeState]'s source, so the capsules stay its *sibling* (the Haze rule).
 */
@Composable
internal fun BoxScope.SubscreenFloatingChrome(
    title: String,
    onBack: () -> Unit,
    /**
     * Null on a screen with nothing to scroll (the map): the chrome then never
     * docks and never hides — its capsules just ride over the content.
     */
    scroll: SubscreenScroll?,
    /** Blur source (the scrolling content); falls back to a solid gray when null. */
    hazeState: HazeState? = null,
    /** Reports the hide-on-scroll visibility so the host slides the nav in step. */
    onChromeVisibleChange: (Boolean) -> Unit = {},
    /** Hidden while scrubbing or selecting, where it would just be noise. */
    scrollToTopSuppressed: Boolean = false,
    /**
     * Null renders no trailing capsule at all — several of these screens have no
     * actions, and an empty capsule is just a glass blob hanging in the corner.
     */
    actions: (@Composable RowScope.() -> Unit)? = null,
    /**
     * Photos running edge-to-edge under the status bar need a scrim to keep the
     * clock legible. Screens whose content sits on the theme background don't:
     * there a dark gradient would just be a dirty band across the top.
     */
    statusBarScrim: Boolean = false
) {
    val alwaysFloating = scroll == null
    val chromeVisible = if (scroll != null) {
        ImmersiveChromeEffect(
            firstVisibleItemIndex = scroll.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = scroll.firstVisibleItemScrollOffset,
            isScrollInProgress = scroll.isScrollInProgress,
            onChromeVisibleChange = onChromeVisibleChange
        )
    } else {
        true
    }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (chromeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "subscreenChromeAlpha"
    )
    // `scroll` se construye en línea en cada call site, así que es una instancia
    // nueva en cada recomposición: keyar el remember con ella recrearía el
    // derivedStateOf en cada frame de scroll. Sus lambdas cuelgan del Lazy*State,
    // que sí es estable, así que basta con leer siempre la última.
    val scrollLatest by rememberUpdatedState(scroll)
    val atTop by remember {
        derivedStateOf {
            val s = scrollLatest
            s == null || (s.firstVisibleItemIndex() == 0 && s.firstVisibleItemScrollOffset() == 0)
        }
    }
    val dockedFraction by animateFloatAsState(
        targetValue = if (!alwaysFloating && atTop) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "subscreenChromeDocked"
    )
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    if (statusBarScrim) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(statusBarTop + 16.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
        )
    }

    if (chromeAlpha > 0.01f) {
        // Skipped when faded out, or it would keep swallowing taps meant for the
        // content underneath.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = chromeAlpha }
        ) {
            // Docked backdrop: covers the status bar too, so at rest this reads
            // exactly like the opaque bar the Scaffold used to dock here.
            if (dockedFraction > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarTop + 64.dp)
                        .graphicsLayer { alpha = dockedFraction }
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // weight para que un título largo se trunque en vez de empujar
                // la cápsula de acciones fuera de la pantalla.
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubscreenChromeCapsule(dockedFraction, hazeState) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(Res.string.action_close)
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 14.dp)
                            )
                        }
                    }
                }
                if (actions != null) {
                    SubscreenChromeCapsule(dockedFraction, hazeState) {
                        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
                    }
                }
            }
        }
    }

    if (scroll != null) {
        // Bottom-center, floating above the nav rather than over it.
        ScrollToTopPill(
            firstVisibleItemIndex = scroll.firstVisibleItemIndex,
            isScrollInProgress = scroll.isScrollInProgress,
            minIndex = scroll.scrollToTopMinIndex,
            onScrollToTop = scroll.onScrollToTop,
            suppressed = scrollToTopSuppressed,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = floatingNavBarReservedHeight() + 8.dp)
        )
    }
}

/** Una cápsula del cromo: forma + sombra + cristal que se desvanece a medida
 * que la barra se acopla (acoplada, el fondo lo pone el backdrop de detrás). */
@Composable
private fun SubscreenChromeCapsule(
    dockedFraction: Float,
    hazeState: HazeState?,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        // Transparente: la Surface aporta forma + sombra y recorta el cristal.
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp * (1f - dockedFraction)
    ) {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - dockedFraction }
                    .chromeCapsuleBackdrop(hazeState = hazeState)
            )
            Box(modifier = Modifier.padding(horizontal = 2.dp)) { content() }
        }
    }
}
