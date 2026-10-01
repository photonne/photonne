package com.photonne.app.ui.explore

import androidx.compose.foundation.background
import com.photonne.app.ui.theme.PhotonneColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.photonne.app.resources.explore_search_placeholder
import com.photonne.app.resources.search_no_results
import com.photonne.app.ui.main.SearchFieldPill
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.error.UiError
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_label_count
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.Spacing

/**
 * Tile shown in the Scenes / Objects grids. Each label renders a representative
 * cover asset (chosen server-side as the highest-confidence detection among the
 * most recent assets) cropped to fill, with the name + count overlaid. When no
 * cover is available — older server, or a label whose assets are all filtered
 * out — the tinted square shows through instead, and it also backs the image
 * while it loads or if it fails.
 */
internal data class ExploreLabelTile(
    val name: String,
    val assetCount: Int,
    val coverAssetId: String? = null
)

@Composable
internal fun ExploreLabelGridScreen(
    tiles: List<ExploreLabelTile>,
    isLoading: Boolean,
    error: UiError?,
    emptyText: String,
    baseUrl: String,
    /** Título del cromo flotante ("Escenas" / "Objetos"). */
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTileClick: (String) -> Unit,
    /** Texto del buscador de la cápsula (filtra en el servidor). */
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    isSearching: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    // Abierto si ya había texto (volver a la pantalla no esconde el filtro).
    var searchOpen by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    // Fuente de blur del cromo: la rejilla que scrollea por detrás, de la que
    // las cápsulas son HERMANAS — la regla de Haze.
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val reservedTop = subscreenChromeReservedTop()
    PhotonneRefreshableScreen(
        indicatorTopPadding = reservedTop,
        isRefreshing = isLoading && tiles.isNotEmpty(),
        onRefresh = onRefresh
    ) {
        // El cromo envuelve todas las ramas: las de carga / error / vacío
        // también necesitan su barra (y su botón de volver).
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && tiles.isEmpty() && query.isBlank() ->
                    AssetGridSkeleton(cellMinSize = LabelTileMinSize, contentPadding = PaddingValues(top = reservedTop))
                error != null && tiles.isEmpty() ->
                    com.photonne.app.ui.error.FullScreenError(
                        error = error,
                        onRetry = onRefresh,
                        modifier = Modifier.padding(top = reservedTop)
                    )
                tiles.isEmpty() && query.isNotBlank() ->
                    if (isSearching) {
                        AssetGridSkeleton(cellMinSize = LabelTileMinSize, contentPadding = PaddingValues(top = reservedTop))
                    } else {
                        EmptyState(
                            icon = PhotonneIcons.NoResults,
                            title = stringResource(Res.string.search_no_results)
                        )
                    }
                tiles.isEmpty() ->
                    EmptyState(
                        icon = Icons.Outlined.Category,
                        title = emptyText
                    )
                else -> LazyVerticalGrid(
                    // Adaptive: 3 columnas en un móvil compacto, más en tablet y
                    // escritorio, sin estirar miniaturas Small a media pantalla.
                    columns = GridCells.Adaptive(minSize = LabelTileMinSize),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        // Reserva el cromo flotante: las fichas pasan por debajo
                        // al scrollear, pero en reposo la primera fila no queda
                        // escondida detrás de la barra.
                        top = 16.dp + reservedTop,
                        end = 16.dp,
                        bottom = 16.dp + floatingNavBarReservedHeight()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                ) {
                    items(tiles, key = { it.name }) { tile ->
                        LabelTileCard(
                            tile = tile,
                            baseUrl = baseUrl,
                            onClick = { onTileClick(tile.name) }
                        )
                    }
                }
            }

            // Buscador en la cápsula, como Álbumes: atrás lo cierra (y quita el
            // filtro); la lupa lo abre.
            SubscreenFloatingChrome(
                title = if (searchOpen) "" else title,
                onBack = if (searchOpen) {
                    {
                        searchOpen = false
                        onQueryChange("")
                    }
                } else onBack,
                titleContent = if (searchOpen) {
                    {
                        SearchFieldPill(
                            value = query,
                            onValueChange = onQueryChange,
                            onClear = { onQueryChange("") },
                            placeholder = stringResource(Res.string.explore_search_placeholder),
                            autofocus = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else null,
                actions = {
                    if (!searchOpen) {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(
                                PhotonneIcons.Search,
                                contentDescription = stringResource(Res.string.explore_search_placeholder)
                            )
                        }
                    }
                },
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
                onChromeVisibleChange = onChromeVisibleChange
            )
        }
    }
}

/** Fits three columns even on a 360 dp phone: (360 − 2·16 − 2·12) / 3 ≈ 101 dp. */
private val LabelTileMinSize = 100.dp

/** Cells scrolled past before the back-to-top pill appears (~3 rows at 3 columns). */
private const val SCROLL_TO_TOP_MIN_CELL = 9

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_CELL = 24

@Composable
internal fun LabelTileCard(tile: ExploreLabelTile, baseUrl: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            )
            if (tile.coverAssetId != null) {
                AsyncImage(
                    model = "$baseUrl/api/assets/${tile.coverAssetId}/thumbnail?size=Small",
                    contentDescription = tile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(PhotonneColors.scrimMedium)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = tile.name.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = PhotonneColors.onScrim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(Res.string.explore_label_count, tile.assetCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = PhotonneColors.onScrimMuted,
                    maxLines = 1
                )
            }
        }
    }
}
