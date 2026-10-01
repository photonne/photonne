package com.photonne.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.Person
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_section_objects
import com.photonne.app.resources.explore_section_scenes
import com.photonne.app.resources.map_title
import com.photonne.app.resources.memories_entry_subtitle
import com.photonne.app.resources.memories_strip_title
import com.photonne.app.resources.people_title
import com.photonne.app.resources.people_unnamed
import com.photonne.app.resources.search_discover_map_subtitle
import com.photonne.app.resources.search_discover_see_all
import com.photonne.app.ui.explore.ExploreLabelTile
import com.photonne.app.ui.explore.LabelTileCard
import com.photonne.app.ui.people.PersonAvatar
import com.photonne.app.ui.theme.EntryCard
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.SectionHeader
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.stringResource

/** Cuántas personas y etiquetas enseña cada fila antes del "Ver todo". */
private const val DiscoverRowLimit = 12

/** Mismo tamaño que las teselas de Escenas/Objetos, para que se reconozcan. */
private val DiscoverTileSize = 100.dp

/**
 * Lo que enseña Buscar antes de escribir nada: puertas a Recuerdos y al Mapa,
 * y filas con las personas, escenas y objetos que más salen. Sustituye al
 * "Empieza a buscar" vacío, y de paso hace visibles secciones que solo vivían
 * dentro de Álbumes. Las facetas son las mismas que ya cargaba la hoja de
 * filtros; una fila sin datos (ML apagado, servidor antiguo) no se pinta.
 *
 * Tocar una persona, escena u objeto busca por ella aquí mismo, como el
 * panel de info del visor; "Ver todo" abre la pantalla completa.
 */
@Composable
internal fun SearchDiscover(
    state: SearchUiState,
    baseUrl: String,
    contentPadding: PaddingValues,
    onOpenMemories: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenScenes: () -> Unit,
    onOpenObjects: () -> Unit,
    onPersonClick: (Person) -> Unit,
    onSceneClick: (String) -> Unit,
    onObjectClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val people = state.people.filterNot { it.isHidden }.take(DiscoverRowLimit)
    val scenes = state.sceneLabels.take(DiscoverRowLimit)
    val objects = state.objectLabels.take(DiscoverRowLimit)

    LazyColumn(
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize()
    ) {
        item(key = "entries") {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                EntryCard(
                    icon = PhotonneIcons.Memories,
                    title = stringResource(Res.string.memories_strip_title),
                    subtitle = stringResource(Res.string.memories_entry_subtitle),
                    onClick = onOpenMemories
                )
                EntryCard(
                    icon = Icons.Outlined.Map,
                    title = stringResource(Res.string.map_title),
                    subtitle = stringResource(Res.string.search_discover_map_subtitle),
                    onClick = onOpenMap
                )
            }
        }
        if (people.isNotEmpty()) {
            item(key = "people") {
                DiscoverSection(
                    title = stringResource(Res.string.people_title),
                    onSeeAll = onOpenPeople
                ) {
                    items(people, key = { it.id }) { person ->
                        PersonChip(person = person, baseUrl = baseUrl, onClick = { onPersonClick(person) })
                    }
                }
            }
        }
        if (scenes.isNotEmpty()) {
            item(key = "scenes") {
                DiscoverSection(
                    title = stringResource(Res.string.explore_section_scenes),
                    onSeeAll = onOpenScenes
                ) {
                    items(scenes, key = { it.label }) { label ->
                        LabelTile(
                            tile = ExploreLabelTile(label.label, label.assetCount, label.coverAssetId),
                            baseUrl = baseUrl,
                            onClick = { onSceneClick(label.label) }
                        )
                    }
                }
            }
        }
        if (objects.isNotEmpty()) {
            item(key = "objects") {
                DiscoverSection(
                    title = stringResource(Res.string.explore_section_objects),
                    onSeeAll = onOpenObjects
                ) {
                    items(objects, key = { it.label }) { label ->
                        LabelTile(
                            tile = ExploreLabelTile(label.label, label.assetCount, label.coverAssetId),
                            baseUrl = baseUrl,
                            onClick = { onObjectClick(label.label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverSection(
    title: String,
    onSeeAll: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(text = title, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) {
                Text(stringResource(Res.string.search_discover_see_all))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content
        )
    }
}

@Composable
private fun PersonChip(person: Person, baseUrl: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        PersonAvatar(person = person, baseUrl = baseUrl, sizeDp = 64)
        Text(
            text = person.name ?: stringResource(Res.string.people_unnamed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LabelTile(tile: ExploreLabelTile, baseUrl: String, onClick: () -> Unit) {
    Box(modifier = Modifier.width(DiscoverTileSize)) {
        LabelTileCard(tile = tile, baseUrl = baseUrl, onClick = onClick)
    }
}
