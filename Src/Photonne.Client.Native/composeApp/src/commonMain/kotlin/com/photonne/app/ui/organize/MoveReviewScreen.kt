package com.photonne.app.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.YearGroup
import com.photonne.app.resources.Res
import com.photonne.app.resources.organize_move_action_count
import com.photonne.app.resources.organize_move_review_subtitle_by_year
import com.photonne.app.resources.organize_move_review_subtitle_default
import com.photonne.app.resources.organize_move_review_title
import com.photonne.app.resources.organize_year_photo_count
import com.photonne.app.ui.main.CompactNavBarContentHeight
import com.photonne.app.ui.main.FloatingNavBarBottomMargin
import com.photonne.app.ui.main.FloatingNavBarHorizontalMargin
import com.photonne.app.ui.main.FloatingNavBarShape
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen "Revisar antes de mover": every asset about to move, grouped by
 * capture year with section headers, over a lazy grid so "todas" the thumbnails
 * stay cheap (only visible cells load). Shared by the manual and condition move
 * flows.
 *
 * Es un overlay MODAL: se hospeda en `App.kt` FUERA del `MainScaffold`, así que
 * tapa la nav flotante en vez de quedar por debajo de ella (era lo que dejaba el
 * botón de confirmar inalcanzable en el flujo por condiciones). Por eso reserva
 * abajo el hueco de SU cápsula de confirmar, no el de la nav — que aquí no hay —
 * aunque midan lo mismo a propósito.
 *
 * Cromo estándar de subpantalla ([SubscreenFloatingChrome]): rejilla a sangre bajo
 * la status bar, cápsulas esmeriladas que se acoplan arriba del todo y se ocultan
 * al bajar, y píldora de volver arriba.
 *
 * @param movedTotal total count for the title ("Se moverán N fotos").
 * @param organizeByYear whether the move will actually create Year subfolders —
 *   only tweaks the wording, the grouping is shown either way.
 */
@Composable
fun MoveReviewScreen(
    movedTotal: Int,
    groups: List<YearGroup>,
    baseUrl: String,
    isMoving: Boolean,
    organizeByYear: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()

    // El "atrás" del sistema lo encadena el handler único de App.kt, como con el
    // visor y el recuerdo abierto.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = subscreenChromeReservedTop(),
                    bottom = floatingNavBarReservedHeight(),
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "subtitle") {
                    Text(
                        if (organizeByYear) stringResource(Res.string.organize_move_review_subtitle_by_year)
                        else stringResource(Res.string.organize_move_review_subtitle_default),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                groups.forEach { group ->
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "year-${group.year}",
                    ) {
                        YearHeader(year = group.year, count = group.count)
                    }
                    items(group.assetIds, key = { it }) { id ->
                        AsyncImage(
                            model = "$baseUrl/api/assets/$id/thumbnail?size=Small",
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }

            SubscreenFloatingChrome(
                title = stringResource(Res.string.organize_move_review_title, movedTotal),
                onBack = { if (!isMoving) onBack() },
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                    firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { gridState.isScrollInProgress },
                    scrollToTopMinIndex = SCROLL_TO_TOP_MIN_CELL,
                    onScrollToTop = {
                        if (gridState.firstVisibleItemIndex > SCROLL_TO_TOP_SNAP_CELL) {
                            gridState.scrollToItem(SCROLL_TO_TOP_SNAP_CELL)
                        }
                        gridState.animateScrollToItem(0)
                    }
                ),
                hazeState = hazeState,
                // Fotos a sangre bajo la status bar: sin scrim el reloj se pierde
                // sobre según qué miniatura.
                statusBarScrim = true,
            )

            ConfirmMoveCapsule(
                label = stringResource(Res.string.organize_move_action_count, movedTotal),
                enabled = !isMoving && movedTotal > 0,
                isMoving = isMoving,
                onClick = onConfirm,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * La acción de confirmar, como cápsula flotante: comparte forma, altura y
 * márgenes con la nav y con las barras de selección (misma familia), pero va
 * rellena de `primary` porque es LA acción de la pantalla, no un contenedor de
 * iconos. No lleva cristal — encima de una rejilla de fotos, un botón primario
 * translúcido deja de leerse como botón.
 */
@Composable
private fun ConfirmMoveCapsule(
    label: String,
    enabled: Boolean,
    isMoving: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(
                start = FloatingNavBarHorizontalMargin,
                end = FloatingNavBarHorizontalMargin,
                bottom = FloatingNavBarBottomMargin,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = FloatingNavBarShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CompactNavBarContentHeight)
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isMoving) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun YearHeader(year: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp, start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            year.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(Res.string.organize_year_photo_count, count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Cells scrolled past before the back-to-top pill appears (~3 rows). */
private const val SCROLL_TO_TOP_MIN_CELL = 12

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_CELL = 48
