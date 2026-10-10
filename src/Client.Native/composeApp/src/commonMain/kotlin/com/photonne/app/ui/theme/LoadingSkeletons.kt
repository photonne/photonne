package com.photonne.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholders de carga con la FORMA de lo que va a llegar.
 *
 * Un spinner centrado dice "espera" y nada más: la pantalla salta de vacía a
 * llena y el contenido aparece de golpe. Un esqueleto con la silueta correcta
 * dice además "va a haber una rejilla aquí", reserva el espacio y hace que la
 * llegada del contenido sea un relleno en vez de un salto.
 *
 * El timeline ya lo hacía con sus buckets; esto lleva el mismo trato al resto.
 */

/** Rejilla de miniaturas cuadradas, misma geometría que `AssetGrid`. */
@Composable
fun AssetGridSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    cellMinSize: Dp = 110.dp,
    // De sobra para llenar cualquier pantalla; el Lazy solo compone lo visible.
    cellCount: Int = 36
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = cellMinSize),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        // Inerte: tocar un hueco no debe hacer nada, y desplazarlo tampoco
        // tiene sentido cuando no hay contenido debajo.
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(cellCount) {
            SkeletonBlock(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                cornerRadius = 0.dp
            )
        }
    }
}

/**
 * Lista de filas con miniatura + dos líneas de texto: la silueta de los
 * álbumes, las carpetas y las personas.
 */
@Composable
fun ListRowsSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowCount: Int = 8,
    thumbnailSize: Dp = 56.dp
) {
    Column(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        repeat(rowCount) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SkeletonBlock(modifier = Modifier.size(thumbnailSize))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    // Anchos alternos: filas idénticas se leen como un patrón
                    // y delatan que es un placeholder más de la cuenta.
                    SkeletonChip(width = if (index % 2 == 0) 160.dp else 120.dp, height = 14.dp)
                    SkeletonChip(width = 80.dp, height = 12.dp)
                }
            }
        }
    }
}

/**
 * Rejilla de portadas con su nombre debajo: la silueta de los álbumes en
 * rejilla (y de cualquier [CollectionCard]). Mismas columnas y separación que
 * la rejilla real para que el contenido caiga en su sitio.
 */
@Composable
fun GridTilesSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    cellMinSize: Dp = 100.dp,
    spacing: Dp = Spacing.md,
    cellCount: Int = 24
) {
    CaptionedGridSkeleton(
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
        contentPadding = contentPadding,
        cellMinSize = cellMinSize,
        horizontalSpacing = spacing,
        verticalSpacing = spacing,
        cellCount = cellCount
    )
}

/**
 * Rejilla de caras redondas con el nombre debajo: la silueta de Personas.
 */
@Composable
fun GridCirclesSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    cellMinSize: Dp = 88.dp,
    horizontalSpacing: Dp = 6.dp,
    verticalSpacing: Dp = 10.dp,
    cellCount: Int = 30
) {
    CaptionedGridSkeleton(
        shape = CircleShape,
        modifier = modifier,
        contentPadding = contentPadding,
        cellMinSize = cellMinSize,
        horizontalSpacing = horizontalSpacing,
        verticalSpacing = verticalSpacing,
        cellCount = cellCount,
        centered = true
    )
}

@Composable
private fun CaptionedGridSkeleton(
    shape: Shape,
    modifier: Modifier,
    contentPadding: PaddingValues,
    cellMinSize: Dp,
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    cellCount: Int,
    centered: Boolean = false
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = cellMinSize),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(cellCount) { index ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(shape)
                        .goldShimmer()
                )
                SkeletonChip(width = if (index % 2 == 0) 64.dp else 48.dp, height = 12.dp)
            }
        }
    }
}

/**
 * Filas con cabecera y un carrusel de tarjetas: la silueta de Recuerdos (una
 * fila por tema, tarjetas de [cardWidth]×[cardHeight] con [MemoryCardShape]).
 */
@Composable
fun CardRowsSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowCount: Int = 4,
    cardWidth: Dp = 150.dp,
    cardHeight: Dp = 190.dp,
    cardSpacing: Dp = 10.dp,
    cardsPerRow: Int = 4
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(top = Spacing.sm)
    ) {
        repeat(rowCount) { index ->
            SkeletonChip(
                width = if (index % 2 == 0) 140.dp else 110.dp,
                height = 16.dp,
                modifier = Modifier.padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = 20.dp,
                    bottom = Spacing.sm
                )
            )
            Row(
                // Sin scroll: la fila se recorta por la derecha como el
                // carrusel real, que asoma la siguiente tarjeta.
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing)
            ) {
                repeat(cardsPerRow) {
                    Box(
                        modifier = Modifier
                            .size(width = cardWidth, height = cardHeight)
                            .clip(MemoryCardShape)
                            .goldShimmer()
                    )
                }
            }
        }
    }
}
