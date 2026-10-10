package com.photonne.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
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
import coil3.compose.AsyncImage
import com.photonne.app.resources.Res
import com.photonne.app.resources.map_attribution
import com.photonne.app.resources.map_attribution_osm
import com.photonne.app.ui.theme.Spacing
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** Teselas de OpenStreetMap, sin clave (las mismas que el minimapa del visor). */
private const val OSM_TILE_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

/** Latitud que queda en el centro vertical: deja ver casi todas las tierras habitadas. */
private const val CenterLat = 25.0

/**
 * Mapamundi estático (sin marcadores ni gestos) para la tarjeta del Mapa en
 * Colecciones: el mundo entero encajado a lo ancho y recortado arriba y abajo,
 * con su atribución (condición de uso).
 *
 * Con la clave de CARTO del servidor, las mismas teselas que el mapa (a @2x y
 * en el tema de la app). Sin ella CARTO devuelve teselas en blanco con la marca
 * "API KEY REQUIRED", y la tarjeta se quedaba vacía: entonces usa
 * OpenStreetMap, que no pide clave (siempre en claro).
 */
@Composable
internal fun MapPreview(
    tileApiKey: String?,
    modifier: Modifier = Modifier,
) {
    val useCarto = !tileApiKey.isNullOrBlank()
    val darkTiles = useCarto && MaterialTheme.colorScheme.background.luminance() < 0.5f
    val template = remember(darkTiles, tileApiKey) {
        if (useCarto) cartoTileTemplate(darkTiles, tileApiKey).replace(".png", "@2x.png")
        else OSM_TILE_TEMPLATE
    }
    // Píxeles reales de cada tesela: CARTO @2x da 512, OSM 256.
    val tileSourcePx = if (useCarto) 512 else 256
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.background(if (darkTiles) MapBackgroundDark else MapBackgroundLight)
    ) {
        // Menor zoom cuyas teselas, repartidas a lo ancho, no se estiran por
        // encima de su tamaño real (borrosas): pocas teselas y nítidas.
        val widthPx = with(density) { maxWidth.toPx() }
        var zoom = 1
        while (widthPx / (1 shl zoom) > tileSourcePx && zoom < 5) zoom++
        val n = 1 shl zoom
        val tileDp = maxWidth / n
        val tilePx = with(density) { tileDp.toPx() }
        // Desplazamiento vertical para que CenterLat quede en el centro.
        val centerY = latToWorldY(CenterLat, zoom) / TILE_SIZE_PX * tilePx
        val offsetY = (with(density) { maxHeight.toPx() } / 2 - centerY).roundToInt()
        Column(
            modifier = Modifier
                // Sin límite de alto: el mundo es cuadrado y la tarjeta apaisada,
                // así que se pinta entero desde arriba y se desplaza para centrarlo.
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(width = maxWidth, height = tileDp * n)
                .offset { IntOffset(0, offsetY) }
        ) {
            for (tileY in 0 until n) {
                Row {
                    for (tileX in 0 until n) {
                        val subdomain = "abcd"[(tileX + tileY) % 4]
                        AsyncImage(
                            model = template
                                .replace("{s}", subdomain.toString())
                                .replace("{z}", zoom.toString())
                                .replace("{x}", tileX.toString())
                                .replace("{y}", tileY.toString()),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(tileDp)
                        )
                    }
                }
            }
        }
        MapAttribution(
            text = stringResource(
                if (useCarto) Res.string.map_attribution else Res.string.map_attribution_osm
            ),
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.sm)
        )
    }
}
