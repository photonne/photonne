package com.photonne.app.ui.map

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.photonne.app.ui.theme.Spacing

/**
 * Atribución de las teselas (condición de uso de OSM y CARTO). Una sola
 * pieza para el mapa y el minimapa del visor, que antes la pintaban con
 * estilos distintos (velo negro con texto blanco frente a superficie
 * translúcida).
 */
@Composable
internal fun MapAttribution(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Chincheta del minimapa del visor. Igual en los dos temas porque las
 * teselas de OSM son siempre claras.
 */
internal val MapPinColor = Color(0xFFE53935)
