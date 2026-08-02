package com.photonne.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
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
