package com.photonne.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.data.settings.HideableStorageSpaces
import com.photonne.app.data.settings.SpaceBytes
import com.photonne.app.data.settings.StorageSpace
import com.photonne.app.resources.Res
import com.photonne.app.resources.storage_label_total
import com.photonne.app.resources.storage_space_hidden
import com.photonne.app.resources.storage_space_library
import com.photonne.app.resources.storage_space_personal
import com.photonne.app.resources.storage_space_shared
import com.photonne.app.ui.format.humanBytes
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

fun ChartPalette.colorOf(space: StorageSpace): Color = when (space) {
    StorageSpace.Personal -> spacePersonal
    StorageSpace.Shared -> spaceShared
    StorageSpace.Library -> spaceLibrary
}

@Composable
fun storageSpaceName(space: StorageSpace): String = stringResource(
    when (space) {
        StorageSpace.Personal -> Res.string.storage_space_personal
        StorageSpace.Shared -> Res.string.storage_space_shared
        StorageSpace.Library -> Res.string.storage_space_library
    }
)

/**
 * La barra de un reparto por espacio. Un espacio oculto sigue en ella, rayado
 * y gris, para que no parezca que ha desaparecido.
 */
@Composable
fun StorageSpacesBar(
    bytes: SpaceBytes,
    hidden: Set<StorageSpace>,
    modifier: Modifier = Modifier,
    barHeight: Dp = 12.dp,
    fillFraction: Float = 1f,
) {
    val palette = rememberChartPalette()
    val hiddenLabel = stringResource(Res.string.storage_space_hidden)
    val present = StorageSpace.entries.filter { bytes[it] > 0L }
    val names = present.associateWith { storageSpaceName(it) }
    StackedBar(
        segments = present.map { space ->
            val off = space in hidden
            StackedSegment(
                value = bytes[space].toFloat(),
                color = if (off) palette.spaceHidden else palette.colorOf(space),
                hatched = off
            )
        },
        modifier = modifier,
        barHeight = barHeight,
        trackColor = MaterialTheme.colorScheme.surface,
        fillFraction = fillFraction,
        description = present.joinToString(", ") { space ->
            "${names.getValue(space)} ${humanBytes(bytes[space])}" + if (space in hidden) " ($hiddenLabel)" else ""
        }
    )
}

/**
 * Barra + una fila por espacio con su tamaño. Carpetas compartidas y
 * Bibliotecas externas llevan un interruptor para quitarlas del total; el
 * personal cuenta siempre. El total de abajo respeta lo ocultado.
 */
@Composable
fun StorageSpacesBreakdown(
    bytes: SpaceBytes,
    hidden: Set<StorageSpace>,
    onToggle: (StorageSpace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberChartPalette()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StorageSpacesBar(bytes, hidden)
        Spacer(Modifier.size(Spacing.xxs))
        StorageSpace.entries.filter { bytes[it] > 0L }.forEach { space ->
            val off = space in hidden
            val name = storageSpaceName(space)
            val hideable = space in HideableStorageSpaces
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (hideable) Modifier.toggleable(
                            value = !off,
                            role = Role.Switch,
                            onValueChange = { onToggle(space) }
                        ) else Modifier
                    )
                    .padding(vertical = if (hideable) 0.dp else Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpaceSwatch(
                    color = if (off) palette.spaceHidden else palette.colorOf(space),
                    hatched = off
                )
                Spacer(Modifier.width(Spacing.sm))
                val textColor = if (off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    humanBytes(bytes[space]),
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    textDecoration = if (off) TextDecoration.LineThrough else null
                )
                if (hideable) {
                    Spacer(Modifier.width(Spacing.sm))
                    // El Row ya es el interruptor (toggleable) y lee el nombre: este solo lo pinta.
                    Switch(checked = !off, onCheckedChange = null)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xxs))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.storage_label_total),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(humanBytes(bytes.visible(hidden)), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SpaceSwatch(color: Color, hatched: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(10.dp)) {
        val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        if (hatched) drawHatched(color, Offset.Zero, size, radius)
        else drawRoundRect(color = color, size = size, cornerRadius = radius)
    }
}
