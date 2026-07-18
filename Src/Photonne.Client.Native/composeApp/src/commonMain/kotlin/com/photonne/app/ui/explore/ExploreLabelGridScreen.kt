package com.photonne.app.ui.explore

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_label_count
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

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
    errorMessage: String?,
    emptyText: String,
    baseUrl: String,
    /** Título del cromo flotante ("Escenas" / "Objetos"). */
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTileClick: (String) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    // Fuente de blur del cromo: la rejilla que scrollea por detrás, de la que
    // las cápsulas son HERMANAS — la regla de Haze.
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val reservedTop = subscreenChromeReservedTop()
    PhotonneRefreshableScreen(
        isRefreshing = isLoading && tiles.isNotEmpty(),
        onRefresh = onRefresh
    ) {
        // El cromo envuelve todas las ramas: las de carga / error / vacío
        // también necesitan su barra (y su botón de volver).
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && tiles.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                errorMessage != null && tiles.isEmpty() ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                tiles.isEmpty() ->
                    EmptyState(
                        icon = Icons.Outlined.Category,
                        title = emptyText
                    )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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

            // Sin acciones: una sola cápsula (volver + título).
            SubscreenFloatingChrome(
                title = title,
                onBack = onBack,
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

/** Cells scrolled past before the back-to-top pill appears (~3 rows at 2 columns). */
private const val SCROLL_TO_TOP_MIN_CELL = 6

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_CELL = 24

@Composable
private fun LabelTileCard(tile: ExploreLabelTile, baseUrl: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
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
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = tile.name.replaceFirstChar { it.titlecase() },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(Res.string.explore_label_count, tile.assetCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
        }
    }
}
