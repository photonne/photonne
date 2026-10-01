package com.photonne.app.ui.memories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.data.models.MemoryDetail
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_empty
import com.photonne.app.resources.explore_section_objects
import com.photonne.app.resources.explore_section_scenes
import com.photonne.app.resources.explore_theme_missing
import com.photonne.app.resources.explore_title
import com.photonne.app.ui.collections.SectionTitle
import com.photonne.app.ui.explore.ExploreLabelTile
import com.photonne.app.ui.explore.LabelTileCard
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.contentWidth
import org.jetbrains.compose.resources.stringResource

/** Etiquetas que enseña cada fila de Escenas / Objetos antes de su "ver todo". */
private const val LabelRowLimit = 20

/**
 * Explorar: los recuerdos por tema (viajes, días de playa, favoritos del año…),
 * una fila por tema como era la antigua página de Recuerdos, y al final las
 * Escenas y los Objetos, que también son "explorar por lo que sale en la foto".
 */
@Composable
fun ExploreMemoriesScreen(
    viewModel: MemoryFeedViewModel,
    scenes: List<SceneLabel>,
    objects: List<ObjectLabel>,
    baseUrl: String,
    onOpenMemory: (MemoryDetail) -> Unit,
    onOpenScene: (String) -> Unit,
    onSeeAllScenes: () -> Unit,
    onOpenObject: (String) -> Unit,
    onSeeAllObjects: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    MemoryFeedScaffold(
        title = stringResource(Res.string.explore_title),
        viewModel = viewModel,
        onOpenMemory = onOpenMemory,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        isEmpty = { it.exploreRows.isEmpty() && scenes.isEmpty() && objects.isEmpty() },
        emptyTitle = stringResource(Res.string.explore_empty),
        onRefresh = onRefresh,
    ) { state, open ->
        items(state.exploreRows, key = { "row:${it.key}" }) { row ->
            Column(Modifier.contentWidth()) {
                MemoryThemeRow(row = row, baseUrl = baseUrl, openingId = state.openingId, onClick = open)
            }
        }
        if (scenes.isNotEmpty()) {
            item(key = "scenes") {
                LabelRow(
                    title = stringResource(Res.string.explore_section_scenes),
                    labels = scenes.take(LabelRowLimit).map {
                        ExploreLabelTile(it.label, it.assetCount, it.coverAssetId)
                    },
                    baseUrl = baseUrl,
                    onTitleClick = onSeeAllScenes,
                    onClick = onOpenScene,
                )
            }
        }
        if (objects.isNotEmpty()) {
            item(key = "objects") {
                LabelRow(
                    title = stringResource(Res.string.explore_section_objects),
                    labels = objects.take(LabelRowLimit).map {
                        ExploreLabelTile(it.label, it.assetCount, it.coverAssetId)
                    },
                    baseUrl = baseUrl,
                    onTitleClick = onSeeAllObjects,
                    onClick = onOpenObject,
                )
            }
        }
    }
}

@Composable
private fun LabelRow(
    title: String,
    labels: List<ExploreLabelTile>,
    baseUrl: String,
    onTitleClick: () -> Unit,
    onClick: (String) -> Unit,
) {
    Column(Modifier.contentWidth()) {
        Spacer(Modifier.padding(top = Spacing.sm))
        SectionTitle(title, onClick = onTitleClick)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(labels, key = { it.name }) { tile ->
                Box(Modifier.width(RowCardSize)) {
                    LabelTileCard(tile = tile, baseUrl = baseUrl, onClick = { onClick(tile.name) })
                }
            }
        }
    }
}

/**
 * Un tema de Explorar a pantalla completa (lo que abre su tarjeta en
 * Colecciones): sus años o viajes en rejilla de dos, más grandes que en la fila.
 */
@Composable
fun MemoryThemeScreen(
    viewModel: MemoryFeedViewModel,
    themeKey: String,
    baseUrl: String,
    onOpenMemory: (MemoryDetail) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val row = state.exploreRows.firstOrNull { it.key == themeKey }
    MemoryFeedScaffold(
        title = row?.displayTitle().orEmpty(),
        viewModel = viewModel,
        onOpenMemory = onOpenMemory,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        isEmpty = { feed -> feed.exploreRows.none { it.key == themeKey } },
        emptyTitle = stringResource(Res.string.explore_theme_missing),
    ) { feed, open ->
        val memories = feed.exploreRows.firstOrNull { it.key == themeKey }?.memories.orEmpty()
        items(memories.chunked(2), key = { pair -> "pair:${pair.first().id}" }) { pair ->
            Row(
                modifier = Modifier
                    .contentWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                pair.forEach { memory ->
                    MemoryRowCard(
                        memory = memory,
                        baseUrl = baseUrl,
                        isOpening = feed.openingId == memory.id,
                        onClick = { open(memory) },
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
