package com.photonne.app.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.Memory
import com.photonne.app.data.models.ObjectLabel
import com.photonne.app.data.models.Person
import com.photonne.app.data.models.SceneLabel
import com.photonne.app.resources.Res
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.albums_badge_pinned
import com.photonne.app.resources.albums_section_pinned
import com.photonne.app.resources.albums_title
import com.photonne.app.resources.collections_customize
import com.photonne.app.resources.archive_title
import com.photonne.app.resources.explore_section_objects
import com.photonne.app.resources.favorites_title
import com.photonne.app.resources.map_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.explore_section_scenes
import com.photonne.app.resources.folders_title
import com.photonne.app.resources.memories_strip_title
import com.photonne.app.resources.organize_inbox_card_subtitle
import com.photonne.app.resources.organize_inbox_count_format
import com.photonne.app.resources.organize_inbox_title
import com.photonne.app.resources.people_title
import com.photonne.app.resources.people_unnamed
import com.photonne.app.resources.search_discover_see_all
import com.photonne.app.resources.tab_collections
import com.photonne.app.resources.tab_search
import com.photonne.app.ui.album.AlbumCover
import com.photonne.app.ui.explore.ExploreLabelTile
import com.photonne.app.ui.explore.LabelTileCard
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.memories.MemoryCardFace
import com.photonne.app.ui.people.PersonAvatar
import com.photonne.app.ui.theme.CollectionCard
import com.photonne.app.ui.theme.CollectionCover
import com.photonne.app.ui.theme.EntryCard
import com.photonne.app.ui.theme.FolderGlyph
import com.photonne.app.ui.theme.IconCircle
import com.photonne.app.ui.theme.OverlayIconBadge
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.SettingsItem
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.contentWidth
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

/** Cuántos elementos enseña cada slider antes de la tarjeta "Ver todo". */
private const val SliderLimit = 20

/** Tarjeta de recuerdo: en retrato y algo menor que la tira de Fotos, para ver más. */
private val MemoryCardWidth = 168.dp
private val MemoryCardHeight = 232.dp

/** Álbumes, carpetas y fijados: portada cuadrada con el nombre debajo. */
private val CollectionTileWidth = 128.dp

/** Escenas y objetos, al tamaño de su rejilla para que se reconozcan. */
private val LabelTileWidth = 100.dp

private val PersonTileWidth = 76.dp
private const val PersonAvatarSize = 68

/**
 * Lo que va en la sección "Fijados": álbumes y carpetas mezclados por fecha de
 * fijado (el último arriba), como pide Colecciones.
 */
sealed interface PinnedEntry {
    val key: String
    data class Album(val album: AlbumSummary) : PinnedEntry {
        override val key: String get() = "album:${album.id}"
    }
    data class Folder(val folder: FolderSummary) : PinnedEntry {
        override val key: String get() = "folder:${folder.id}"
    }
}

fun mergePinned(albums: List<AlbumSummary>, folders: List<FolderSummary>): List<PinnedEntry> {
    val entries = albums.filter { it.isPinned }.map { it.pinnedAt to PinnedEntry.Album(it) } +
        folders.filter { it.isPinned }.map { it.pinnedAt to PinnedEntry.Folder(it) }
    return entries.sortedByDescending { it.first }.map { it.second }
}

/** Todo lo que pinta Colecciones, ya resuelto por el host desde sus ViewModels. */
data class CollectionsContent(
    val memories: List<Memory> = emptyList(),
    val memoryOpeningId: String? = null,
    val pinned: List<PinnedEntry> = emptyList(),
    val people: List<Person> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val albumsLoaded: Boolean = false,
    val folders: List<FolderSummary> = emptyList(),
    val organizePendingCount: Int = 0,
    val scenes: List<SceneLabel> = emptyList(),
    val objects: List<ObjectLabel> = emptyList(),
    val isRefreshing: Boolean = false,
)

class CollectionsActions(
    val onOpenSearch: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpenMemory: (Memory) -> Unit,
    val onSeeAllMemories: () -> Unit,
    val onOpenPinned: (PinnedEntry) -> Unit,
    val onSeeAllPinned: () -> Unit,
    val onOpenPerson: (Person) -> Unit,
    val onSeeAllPeople: () -> Unit,
    val onOpenAlbum: (AlbumSummary) -> Unit,
    val onSeeAllAlbums: () -> Unit,
    val onCreateAlbum: () -> Unit,
    val onOpenFolder: (FolderSummary) -> Unit,
    val onSeeAllFolders: () -> Unit,
    val onOpenOrganize: () -> Unit,
    val onOpenScene: (String) -> Unit,
    val onSeeAllScenes: () -> Unit,
    val onOpenObject: (String) -> Unit,
    val onSeeAllObjects: () -> Unit,
    val onOpenFavorites: () -> Unit,
    val onOpenMap: () -> Unit,
    val onOpenArchived: () -> Unit,
    val onOpenTrash: () -> Unit,
)

/**
 * Pestaña Colecciones (al estilo de Apple Fotos): todo lo que agrupa fotos en
 * una sola página por secciones. Cada sección es un título que abre su página
 * completa (scroll vertical, filtros, crear…) y un slider horizontal con lo
 * primero. Una sección sin nada que enseñar no se pinta, salvo Álbumes, que
 * ofrece crear el primero.
 *
 * No desliza entre pestañas (ver canSwipeTabs): los sliders se quedan el gesto.
 */
@Composable
fun CollectionsScreen(
    content: CollectionsContent,
    baseUrl: String,
    actions: CollectionsActions,
    /** Secciones visibles, en orden (ver [CollectionsLayout]). */
    sections: List<CollectionSection>,
    onCustomize: () -> Unit,
    onLoad: () -> Unit,
    /**
     * False mientras Colecciones solo se compone como vecina de Fotos en el
     * pager: no se piden recuerdos, personas ni etiquetas hasta que se ve.
     */
    active: Boolean = true,
    scrollToTopTick: Int = 0,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val reservedTop = subscreenChromeReservedTop()
    LaunchedEffect(active) { if (active) onLoad() }
    LaunchedEffect(scrollToTopTick) {
        if (scrollToTopTick > 0) listState.animateScrollToItem(0)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PhotonneRefreshableScreen(
            indicatorTopPadding = reservedTop,
            isRefreshing = content.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                contentPadding = PaddingValues(
                    top = Spacing.sm + reservedTop,
                    bottom = Spacing.lg + floatingNavBarReservedHeight()
                )
            ) {
                if (content.organizePendingCount > 0) {
                    item(key = "organize") {
                        OrganizeCard(count = content.organizePendingCount, onClick = actions.onOpenOrganize)
                    }
                }
                collectionSections(content, baseUrl, actions, sections)
                item(key = "library") { LibraryRows(actions) }
                item(key = "customize") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(
                            onClick = onCustomize,
                            modifier = Modifier.padding(top = Spacing.md)
                        ) {
                            Text(stringResource(Res.string.collections_customize))
                        }
                    }
                }
            }
        }

        SubscreenFloatingChrome(
            title = stringResource(Res.string.tab_collections),
            onBack = null,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                isScrollInProgress = { listState.isScrollInProgress },
                scrollToTopMinIndex = 3,
                onScrollToTop = { listState.animateScrollToItem(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange,
            actions = {
                IconButton(onClick = actions.onOpenSearch) {
                    Icon(PhotonneIcons.Search, contentDescription = stringResource(Res.string.tab_search))
                }
            }
        )
    }
}

private fun LazyListScope.collectionSections(
    content: CollectionsContent,
    baseUrl: String,
    actions: CollectionsActions,
    sections: List<CollectionSection>,
) {
    for (section in sections) when (section) {
        CollectionSection.Memories -> if (content.memories.isNotEmpty()) {
                item(key = "memories") {
                    SliderSection(
                        title = stringResource(Res.string.memories_strip_title),
                        onTitleClick = actions.onSeeAllMemories,
                        items = content.memories.take(SliderLimit),
                        key = { "memory:${it.id}" },
                        truncated = content.memories.size > SliderLimit,
                        seeAllSize = MemoryCardWidth to MemoryCardHeight,
                    ) { memory ->
                        MemoryTile(
                            memory = memory,
                            baseUrl = baseUrl,
                            isOpening = content.memoryOpeningId == memory.id,
                            onClick = { actions.onOpenMemory(memory) }
                        )
                    }
                }
            }
        CollectionSection.Pinned -> if (content.pinned.isNotEmpty()) {
                item(key = "pinned") {
                    SliderSection(
                        title = stringResource(Res.string.albums_section_pinned),
                        onTitleClick = actions.onSeeAllPinned,
                        items = content.pinned.take(SliderLimit),
                        key = { it.key },
                        truncated = content.pinned.size > SliderLimit,
                        seeAllSize = CollectionTileWidth to CollectionTileWidth,
                    ) { entry ->
                        Box(Modifier.width(CollectionTileWidth)) {
                            PinnedTile(entry = entry, baseUrl = baseUrl, onClick = { actions.onOpenPinned(entry) })
                        }
                    }
                }
            }
        CollectionSection.People -> if (content.people.isNotEmpty()) {
                item(key = "people") {
                    SliderSection(
                        title = stringResource(Res.string.people_title),
                        onTitleClick = actions.onSeeAllPeople,
                        items = content.people.take(SliderLimit),
                        key = { "person:${it.id}" },
                        truncated = content.people.size > SliderLimit,
                        seeAllSize = PersonTileWidth to PersonAvatarSize.dp,
                        spacing = Spacing.sm,
                    ) { person ->
                        PersonTile(person = person, baseUrl = baseUrl, onClick = { actions.onOpenPerson(person) })
                    }
                }
            }
        CollectionSection.Albums -> if (content.albums.isNotEmpty() || content.albumsLoaded) {
                item(key = "albums") {
                    if (content.albums.isEmpty()) {
                        SectionTitle(stringResource(Res.string.albums_title), onClick = actions.onSeeAllAlbums)
                        EntryCard(
                            icon = PhotonneIcons.Add,
                            title = stringResource(Res.string.album_action_new),
                            subtitle = null,
                            onClick = actions.onCreateAlbum,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
                        )
                    } else {
                        SliderSection(
                            title = stringResource(Res.string.albums_title),
                            onTitleClick = actions.onSeeAllAlbums,
                            items = content.albums.take(SliderLimit),
                            key = { "album:${it.id}" },
                            truncated = content.albums.size > SliderLimit,
                            seeAllSize = CollectionTileWidth to CollectionTileWidth,
                        ) { album ->
                            Box(Modifier.width(CollectionTileWidth)) {
                                AlbumTile(album = album, baseUrl = baseUrl, onClick = { actions.onOpenAlbum(album) })
                            }
                        }
                    }
                }
            }
        CollectionSection.Folders -> if (content.folders.isNotEmpty()) {
                item(key = "folders") {
                    SliderSection(
                        title = stringResource(Res.string.folders_title),
                        onTitleClick = actions.onSeeAllFolders,
                        items = content.folders.take(SliderLimit),
                        key = { "folder:${it.id}" },
                        truncated = content.folders.size > SliderLimit,
                        seeAllSize = CollectionTileWidth to CollectionTileWidth,
                    ) { folder ->
                        Box(Modifier.width(CollectionTileWidth)) {
                            FolderTile(folder = folder, baseUrl = baseUrl, onClick = { actions.onOpenFolder(folder) })
                        }
                    }
                }
            }
        CollectionSection.Scenes -> if (content.scenes.isNotEmpty()) {
                item(key = "scenes") {
                    SliderSection(
                        title = stringResource(Res.string.explore_section_scenes),
                        onTitleClick = actions.onSeeAllScenes,
                        items = content.scenes.take(SliderLimit),
                        key = { "scene:${it.label}" },
                        truncated = content.scenes.size > SliderLimit,
                        seeAllSize = LabelTileWidth to LabelTileWidth,
                    ) { label ->
                        Box(Modifier.width(LabelTileWidth)) {
                            LabelTileCard(
                                tile = ExploreLabelTile(label.label, label.assetCount, label.coverAssetId),
                                baseUrl = baseUrl,
                                onClick = { actions.onOpenScene(label.label) }
                            )
                        }
                    }
                }
            }
        CollectionSection.Objects -> if (content.objects.isNotEmpty()) {
                item(key = "objects") {
                    SliderSection(
                        title = stringResource(Res.string.explore_section_objects),
                        onTitleClick = actions.onSeeAllObjects,
                        items = content.objects.take(SliderLimit),
                        key = { "object:${it.label}" },
                        truncated = content.objects.size > SliderLimit,
                        seeAllSize = LabelTileWidth to LabelTileWidth,
                    ) { label ->
                        Box(Modifier.width(LabelTileWidth)) {
                            LabelTileCard(
                                tile = ExploreLabelTile(label.label, label.assetCount, label.coverAssetId),
                                baseUrl = baseUrl,
                                onClick = { actions.onOpenObject(label.label) }
                            )
                        }
                    }
                }
            }
    }
}

/**
 * Título de sección que abre su página completa. Título + chevron son un solo
 * objetivo táctil, como la cabecera de la tira de Recuerdos de Fotos.
 */
@Composable
internal fun SectionTitle(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = Spacing.md, top = Spacing.md)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                heading()
            }
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Icon(
            imageVector = PhotonneIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xxs)
        )
    }
}

@Composable
private fun <T> SliderSection(
    title: String,
    onTitleClick: () -> Unit,
    items: List<T>,
    key: (T) -> String,
    truncated: Boolean,
    seeAllSize: Pair<Dp, Dp>,
    spacing: Dp = Spacing.md,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(title, onClick = onTitleClick)
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items(items, key = key) { itemContent(it) }
            if (truncated) {
                item(key = "see-all") {
                    SeeAllTile(width = seeAllSize.first, height = seeAllSize.second, onClick = onTitleClick)
                }
            }
        }
    }
}

/** Última tarjeta de un slider recortado: lleva a la página completa. */
@Composable
private fun SeeAllTile(width: Dp, height: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconCircle(icon = PhotonneIcons.Chevron, compact = true)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(Res.string.search_discover_see_all),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MemoryTile(memory: Memory, baseUrl: String, isOpening: Boolean, onClick: () -> Unit) {
    MemoryCardFace(
        coverUrl = memory.coverAssetId?.let { "$baseUrl/api/assets/$it/thumbnail?size=Large" },
        contentDescription = memory.title,
        title = memory.title,
        subtitle = memory.subtitle,
        modifier = Modifier
            .size(width = MemoryCardWidth, height = MemoryCardHeight)
            .clickable(enabled = !isOpening, onClick = onClick),
    ) {
        // El feed solo trae la portada: abrir es una petición, así que se dice.
        if (isOpening) {
            Box(
                modifier = Modifier.fillMaxSize().background(PhotonneColors.scrimLight),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@Composable
private fun PersonTile(person: Person, baseUrl: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(PersonTileWidth)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        PersonAvatar(person = person, baseUrl = baseUrl, sizeDp = PersonAvatarSize)
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
internal fun AlbumTile(album: AlbumSummary, baseUrl: String, onClick: () -> Unit) {
    CollectionCard(
        title = album.name,
        thumbnail = { AlbumCover(album, baseUrl, large = true) },
        onClick = onClick,
        count = album.assetCount,
        badges = if (album.isPinned) {
            {
                OverlayIconBadge(
                    icon = PhotonneIcons.PinActive,
                    contentDescription = stringResource(Res.string.albums_badge_pinned)
                )
            }
        } else null
    )
}

/**
 * Carpeta con la foto más reciente de portada (el servidor ya la manda como
 * firstAssetId); el icono de carpeta si está vacía.
 */
@Composable
internal fun FolderTile(folder: FolderSummary, baseUrl: String, onClick: () -> Unit) {
    CollectionCard(
        title = folder.name.ifBlank { folder.path },
        thumbnail = {
            CollectionCover(
                model = folder.firstAssetId?.let { "$baseUrl/api/assets/$it/thumbnail?size=Medium" },
                contentDescription = folder.name
            ) { FolderGlyph(large = true) }
        },
        onClick = onClick,
        count = folder.assetCount,
        badges = if (folder.isPinned) {
            {
                OverlayIconBadge(
                    icon = PhotonneIcons.PinActive,
                    contentDescription = stringResource(Res.string.albums_badge_pinned)
                )
            }
        } else null
    )
}

@Composable
internal fun PinnedTile(entry: PinnedEntry, baseUrl: String, onClick: () -> Unit) {
    when (entry) {
        is PinnedEntry.Album -> AlbumTile(entry.album, baseUrl, onClick)
        is PinnedEntry.Folder -> FolderTile(entry.folder, baseUrl, onClick)
    }
}

/** "Para organizar" arriba del todo, solo con pendientes: es lo único que pide acción. */
@Composable
private fun OrganizeCard(count: Int, onClick: () -> Unit) {
    EntryCard(
        icon = Icons.Outlined.Inbox,
        title = stringResource(Res.string.organize_inbox_title),
        subtitle = stringResource(Res.string.organize_inbox_card_subtitle),
        onClick = onClick,
        emphasized = true,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(
            text = stringResource(Res.string.organize_inbox_count_format, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Pie de Colecciones: destinos sin portada que antes vivían en la rejilla de
 * Más. Filas, no slider: son sitios a los que ir, no colecciones que hojear.
 */
@Composable
private fun LibraryRows(actions: CollectionsActions) {
    SettingsGroup(
        modifier = Modifier
            .contentWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl)
    ) {
        SettingsItem(
            headline = stringResource(Res.string.favorites_title),
            leadingIcon = PhotonneIcons.Favorite,
            onClick = actions.onOpenFavorites,
            headlineMaxLines = 1
        )
        SettingsItem(
            headline = stringResource(Res.string.map_title),
            leadingIcon = PhotonneIcons.Location,
            onClick = actions.onOpenMap,
            headlineMaxLines = 1,
            showDivider = true
        )
        SettingsItem(
            headline = stringResource(Res.string.archive_title),
            leadingIcon = PhotonneIcons.Archive,
            onClick = actions.onOpenArchived,
            headlineMaxLines = 1,
            showDivider = true
        )
        SettingsItem(
            headline = stringResource(Res.string.trash_title),
            leadingIcon = PhotonneIcons.Delete,
            onClick = actions.onOpenTrash,
            headlineMaxLines = 1,
            showDivider = true
        )
    }
}
