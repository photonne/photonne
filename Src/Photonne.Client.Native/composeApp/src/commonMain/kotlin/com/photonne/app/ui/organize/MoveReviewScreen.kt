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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import com.photonne.app.resources.organize_review_exclude
import com.photonne.app.resources.organize_review_include
import com.photonne.app.ui.selection.GroupSelectionState
import com.photonne.app.ui.selection.selectionStateOf
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
    /**
     * Confirma el movimiento. Recibe null cuando no se ha quitado nada — el
     * llamante puede entonces usar su camino original (en el flujo por
     * condiciones, resolver en el servidor en vez de enviar miles de ids).
     */
    onConfirm: (keptIds: List<String>?) -> Unit,
) {
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    // Quitar de la selección aquí es lo único que separa "revisar" de "mirar":
    // el movimiento es físico y no se deshace, así que la última pantalla antes
    // de confirmarlo tiene que dejar corregir.
    var excluded by remember(groups) { mutableStateOf(emptySet<String>()) }
    val keptTotal = movedTotal - excluded.size
    fun toggleAsset(id: String) {
        excluded = if (id in excluded) excluded - id else excluded + id
    }
    fun toggleYear(group: YearGroup) {
        val ids = group.assetIds
        excluded = if (ids.all { it in excluded }) excluded - ids.toSet()
        else excluded + ids
    }

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
                        YearHeader(
                            year = group.year,
                            count = group.count - group.assetIds.count { it in excluded },
                            state = excluded.selectionStateOf(group.assetIds),
                            onToggle = { toggleYear(group) },
                        )
                    }
                    items(group.assetIds, key = { it }) { id ->
                        ReviewCell(
                            id = id,
                            baseUrl = baseUrl,
                            excluded = id in excluded,
                            enabled = !isMoving,
                            onToggle = { toggleAsset(id) },
                        )
                    }
                }
            }

            SubscreenFloatingChrome(
                title = stringResource(Res.string.organize_move_review_title, keptTotal),
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
                label = stringResource(Res.string.organize_move_action_count, keptTotal),
                enabled = !isMoving && keptTotal > 0,
                isMoving = isMoving,
                onClick = {
                    onConfirm(
                        if (excluded.isEmpty()) null
                        else groups.flatMap { it.assetIds }.filterNot { it in excluded }
                    )
                },
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

/**
 * Una foto de la revisión. Tocarla la quita del movimiento (o la devuelve): se
 * atenúa y muestra una marca, en vez de desaparecer, para que se pueda
 * recuperar sin salir y volver a entrar.
 */
@Composable
private fun ReviewCell(
    id: String,
    baseUrl: String,
    excluded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val includedLabel = stringResource(Res.string.organize_review_exclude)
    val excludedLabel = stringResource(Res.string.organize_review_include)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .toggleable(
                value = !excluded,
                enabled = enabled,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .semantics {
                contentDescription = if (excluded) excludedLabel else includedLabel
            },
    ) {
        AsyncImage(
            model = "$baseUrl/api/assets/$id/thumbnail?size=Small",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(if (excluded) 0.35f else 1f),
        )
        if (excluded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.RemoveCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun YearHeader(
    year: Int,
    count: Int,
    state: GroupSelectionState,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                // Marcado = el año entero se va a mover.
                value = state == GroupSelectionState.None,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            )
            .padding(top = 12.dp, bottom = 4.dp, start = 8.dp, end = 8.dp),
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
            modifier = Modifier.weight(1f),
        )
        // Quitar un año entero de un toque: en un movimiento por condiciones,
        // descartar "todo 2019" es la corrección más habitual.
        TriStateCheckbox(
            state = when (state) {
                // OJO: aquí "seleccionado" es lo que SE VA A MOVER, y el
                // conjunto que se guarda es el de EXCLUIDOS. Van invertidos.
                GroupSelectionState.All -> ToggleableState.Off
                GroupSelectionState.None -> ToggleableState.On
                GroupSelectionState.Partial -> ToggleableState.Indeterminate
            },
            onClick = onToggle,
        )
    }
}

/** Cells scrolled past before the back-to-top pill appears (~3 rows). */
private const val SCROLL_TO_TOP_MIN_CELL = 12

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_CELL = 48
