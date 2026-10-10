package com.photonne.app.ui.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Con el scrubber oculto (sin scroll en curso), un toque en la columna derecha
 * de la rejilla debe llegar a la celda: ni el carril de ratón ni el mango
 * pueden quedarse con él.
 */
@OptIn(ExperimentalTestApi::class)
class ScrubberTouchPassThroughTest {

    @Test
    fun tapOnRightEdgeCellReachesGridWhileScrubberHidden() = runComposeUiTest {
        val clicks = mutableListOf<Int>()
        setContent {
            val gridState = rememberLazyGridState()
            Box(Modifier.size(300.dp, 600.dp).testTag("root")) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(300) { i ->
                        Box(Modifier.size(100.dp).clickable { clicks += i })
                    }
                }
                AlbumGridScrubber(
                    gridState = gridState,
                    cellCount = 300,
                    headerCount = 0,
                    labelForCellIndex = null,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        // Celdas de la columna derecha (x ∈ 200..300 dp): a 10 dp del borde,
        // dentro del carril de 48 dp, en la fila de arriba (donde descansa el
        // mango oculto) y en una de en medio.
        onNodeWithTag("root").performTouchInput {
            click(Offset((290.dp).toPx(), (30.dp).toPx()))
        }
        onNodeWithTag("root").performTouchInput {
            click(Offset((290.dp).toPx(), (350.dp).toPx()))
        }
        waitForIdle()
        assertEquals(listOf(2, 11), clicks)
    }

    @Test
    fun visibleHandleStillScrubsTheGrid() = runComposeUiTest {
        lateinit var gridState: androidx.compose.foundation.lazy.grid.LazyGridState
        setContent {
            gridState = rememberLazyGridState()
            Box(Modifier.size(300.dp, 600.dp).testTag("root")) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(300) { Box(Modifier.size(100.dp)) }
                }
                AlbumGridScrubber(
                    gridState = gridState,
                    cellCount = 300,
                    headerCount = 0,
                    labelForCellIndex = null,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        // Un scroll corto por la izquierda revela el scrubber; el reloj se
        // congela para que no le dé tiempo a ocultarse.
        onNodeWithTag("root").performTouchInput { swipeUp(startY = 400f, endY = 300f) }
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(100)
        val before = gridState.firstVisibleItemIndex
        // El mango está casi arriba (fracción pequeña): arrastrarlo media pista.
        onNodeWithTag("root").performTouchInput {
            swipe(
                start = Offset((276.dp).toPx(), (32.dp).toPx()),
                end = Offset((276.dp).toPx(), (300.dp).toPx()),
                durationMillis = 400
            )
        }
        mainClock.advanceTimeBy(500)
        mainClock.autoAdvance = true
        waitForIdle()
        assertTrue(
            gridState.firstVisibleItemIndex > before + 60,
            "el mango no movió la rejilla: ${gridState.firstVisibleItemIndex} (antes $before)"
        )
    }
}
