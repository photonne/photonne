package com.photonne.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.resources.Res
import com.photonne.app.resources.map_attribution
import com.photonne.app.ui.theme.Spacing
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** Teselas @2x (512 px) en 128 dp: nítidas en pantallas densas. */
private val PreviewTileDp = 128.dp

/**
 * Fondo de mapa estático (sin marcadores ni gestos) centrado en [centerLat],
 * [centerLng]: la tarjeta del Mapa en Colecciones. Mismas teselas CARTO que el
 * mapa, en el tema de la app, con su atribución (condición de uso).
 */
@Composable
internal fun MapPreview(
    centerLat: Double,
    centerLng: Double,
    zoom: Int,
    tileApiKey: String?,
    modifier: Modifier = Modifier,
) {
    val darkTiles = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val template = remember(darkTiles, tileApiKey) {
        cartoTileTemplate(darkTiles, tileApiKey).replace(".png", "@2x.png")
    }
    val z = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    val n = 1 shl z
    // Posición del centro en teselas (con fracción) a este zoom.
    val worldX = lonToWorldX(centerLng, z) / TILE_SIZE_PX
    val worldY = latToWorldY(centerLat, z) / TILE_SIZE_PX
    val centerTileX = floor(worldX).toInt()
    val centerTileY = floor(worldY).toInt()
    val fracX = worldX - centerTileX
    val fracY = worldY - centerTileY
    val density = LocalDensity.current

    Box(
        modifier = modifier.background(if (darkTiles) MapBackgroundDark else MapBackgroundLight)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tilePx = with(density) { PreviewTileDp.toPx() }
            val boxWpx = with(density) { maxWidth.toPx() }
            val boxHpx = with(density) { maxHeight.toPx() }
            val halfX = ceil(boxWpx / 2f / tilePx).toInt() + 1
            val halfY = ceil(boxHpx / 2f / tilePx).toInt() + 1
            val gridOffsetX = (boxWpx / 2.0 - (halfX + fracX) * tilePx).roundToInt()
            val gridOffsetY = (boxHpx / 2.0 - (halfY + fracY) * tilePx).roundToInt()
            Column(
                modifier = Modifier
                    .requiredSize(
                        width = PreviewTileDp * (2 * halfX + 1),
                        height = PreviewTileDp * (2 * halfY + 1)
                    )
                    .offset { IntOffset(gridOffsetX, gridOffsetY) }
            ) {
                for (dy in -halfY..halfY) {
                    Row {
                        for (dx in -halfX..halfX) {
                            val tileX = (((centerTileX + dx) % n) + n) % n
                            val tileY = centerTileY + dy
                            if (tileY < 0 || tileY >= n) {
                                Box(Modifier.size(PreviewTileDp))
                                continue
                            }
                            val subdomain = "abcd"[(((tileX + tileY) % 4) + 4) % 4]
                            AsyncImage(
                                model = template
                                    .replace("{s}", subdomain.toString())
                                    .replace("{z}", z.toString())
                                    .replace("{x}", tileX.toString())
                                    .replace("{y}", tileY.toString()),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(PreviewTileDp)
                            )
                        }
                    }
                }
            }
        }
        MapAttribution(
            text = stringResource(Res.string.map_attribution),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.sm)
        )
    }
}
