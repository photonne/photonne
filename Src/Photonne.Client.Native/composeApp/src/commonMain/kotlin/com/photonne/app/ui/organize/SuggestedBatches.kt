package com.photonne.app.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.OrganizeSuggestion
import com.photonne.app.resources.Res
import com.photonne.app.resources.organize_suggestions_see_all
import com.photonne.app.resources.organize_suggestions_title
import com.photonne.app.resources.organize_year_photo_count
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/**
 * La bandeja como lista de DECISIONES, no de fotos.
 *
 * Un muro cronológico presenta datos: te toca a ti inventar los grupos y
 * sacarlos del scroll a base de selección. Estos lotes los trae ya formados el
 * servidor — un viaje, una persona, una escena, un mes — así que la pantalla
 * pasa de "aquí tienes tus 1.240 fotos" a "aquí tienes cinco decisiones", y de
 * un scroll infinito a una lista que encoge.
 *
 * Tocar un lote lo selecciona entero y abre la rejilla: a partir de ahí manda
 * la barra de selección de siempre, con su mover, su revisión editable y su
 * apartar. No hay un segundo camino que mantener.
 */
@Composable
internal fun SuggestedBatchesList(
    suggestions: List<OrganizeSuggestion>,
    baseUrl: String,
    contentPadding: PaddingValues,
    onPick: (OrganizeSuggestion) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        header?.let { item(key = "header") { it() } }
        item(key = "suggestions-title") {
            Text(
                stringResource(Res.string.organize_suggestions_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }
        items(suggestions, key = { it.key }) { suggestion ->
            SuggestionRow(
                suggestion = suggestion,
                baseUrl = baseUrl,
                onClick = { onPick(suggestion) },
            )
        }
        item(key = "see-all") {
            // Salida, no puerta de entrada: la rejilla plana sigue estando
            // entera para lo que los lotes no cubran.
            TextButton(
                onClick = onSeeAll,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
            ) {
                Text(stringResource(Res.string.organize_suggestions_see_all))
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: OrganizeSuggestion,
    baseUrl: String,
    onClick: () -> Unit,
) {
    val subtitle = remember(suggestion.from, suggestion.to) {
        formatSuggestionSpan(suggestion.from, suggestion.to)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (suggestion.coverAssetId != null) {
                AsyncImage(
                    model = "$baseUrl/api/assets/${suggestion.coverAssetId}/thumbnail?size=Small",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = iconFor(suggestion.kind),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    suggestion.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = listOfNotNull(
                    subtitle,
                    stringResource(Res.string.organize_year_photo_count, suggestion.count),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** El icono solo dice de dónde sale el lote; todos se comportan igual. */
private fun iconFor(kind: String): ImageVector = when (kind) {
    "trip" -> Icons.Outlined.Place
    "person" -> Icons.Outlined.Person
    "scene" -> Icons.Outlined.Landscape
    else -> Icons.Outlined.CalendarMonth
}

/**
 * "mar 2026" o "mar 2026 – abr 2026". El servidor manda ya los extremos como
 * "yyyy-MM", así que aquí no hay que analizar fechas completas.
 */
private fun formatSuggestionSpan(from: String?, to: String?): String? {
    val fromLabel = from?.let(::monthLabel) ?: return null
    val toLabel = to?.let(::monthLabel) ?: return fromLabel
    return if (fromLabel == toLabel) fromLabel else "$fromLabel – $toLabel"
}

private fun monthLabel(raw: String): String? {
    if (raw.length < 7) return null
    val year = raw.substring(0, 4).toIntOrNull() ?: return null
    val month = raw.substring(5, 7).toIntOrNull() ?: return null
    if (month !in 1..12) return null
    return com.photonne.app.ui.grid.formatLocalizedMonth(
        kotlinx.datetime.LocalDate(year, month, 1)
    )
}
