package com.photonne.app.ui.album

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_edit
import com.photonne.app.resources.action_leave
import com.photonne.app.resources.action_share
import com.photonne.app.resources.album_action_album_actions
import com.photonne.app.resources.album_action_members
import com.photonne.app.resources.album_action_sort
import com.photonne.app.resources.album_empty_subtitle
import com.photonne.app.resources.album_empty_title
import com.photonne.app.resources.album_hero_photos
import com.photonne.app.resources.album_hero_shared
import com.photonne.app.ui.grid.AlbumGridScrubber
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.formatLocalizedMonth
import com.photonne.app.ui.main.chromeCapsuleBackdrop
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.ScrollToTopPill
import com.photonne.app.ui.timeline.captureLocalDate
import com.photonne.app.ui.main.ImmersiveChromeEffect
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AlbumDetailScreen(
    album: AlbumSummary,
    onItemClick: (Int) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    onLeave: () -> Unit,
    viewModel: AlbumDetailViewModel,
    /**
     * Immersive bottom nav: while true the photo grid drives the hide-on-scroll
     * chrome (reported via [onChromeVisibleChange]) and reserves the nav's height
     * at its scroll end so it draws edge-to-edge behind the bar, like Fotos.
     */
    immersive: Boolean = false,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()
    val gridState = rememberLazyGridState()
    // Fuente de blur de TODO el cromo del álbum (barra superior, scrubber, botón
    // de subir). Su fuente es SOLO la rejilla, así que las cápsulas quedan como
    // HERMANAS de ella y no descendientes — la regla de Haze.
    val albumHazeState = remember { HazeState() }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    // La misma barra se acopla arriba (sobre la portada del hero) y flota como
    // cápsula al bajar, y se esconde/vuelve con el scroll igual que en Fotos.
    val chromeVisible = if (immersive) {
        ImmersiveChromeEffect(
            firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
            firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
            isScrollInProgress = { gridState.isScrollInProgress },
            onChromeVisibleChange = onChromeVisibleChange
        )
    } else {
        // Con una selección activa manda la AssetSelectionTopBar del Scaffold y
        // esta barra no se dibuja; fuera de inmersivo no hay nada que ocultar.
        true
    }
    val chromeAlpha by animateFloatAsState(
        targetValue = if (chromeVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "albumChromeAlpha"
    )
    val atTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
    }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Lo que ocupa la barra arriba: status bar + un alto de app-bar estándar. La
    // rejilla NO lo reserva (la portada del hero sube a sangre por debajo, y la
    // barra se apoya encima), pero el scrubber sí arranca por debajo para no
    // montarse sobre la cápsula de acciones.
    val reservedTop = statusBarTop + 64.dp
    // Con una selección activa la nav flotante deja su sitio a la cápsula de
    // acciones, que mide lo mismo: la rejilla sigue a sangre por debajo y sigue
    // reservando el mismo hueco, aunque `immersive` ya esté apagado (la barra no
    // se esconde al hacer scroll mientras haya algo seleccionado).
    val gridContentPadding = if (immersive || state.isSelectionActive) {
        PaddingValues(bottom = floatingNavBarReservedHeight())
    } else PaddingValues(0.dp)

    LaunchedEffect(album.id) { viewModel.open(album.id, album.name, album.description) }

    val heroTitle = state.albumName ?: album.name
    val heroDescription = state.albumDescription ?: album.description
    // While items load, use the cached count from the AlbumSummary so the
    // hero doesn't flash "0 fotos"; once loaded, the live list is the source
    // of truth (so removing everything correctly reads 0).
    val heroAssetCount = if (state.albumId == album.id && !state.isLoading) {
        state.items.size
    } else {
        album.assetCount
    }

    val hero: @Composable () -> Unit = {
        Column {
            AlbumHero(
                title = heroTitle,
                description = heroDescription,
                assetCount = heroAssetCount,
                createdAt = album.createdAt,
                isShared = album.isShared,
                coverUrl = album.coverThumbnailUrl?.let { resolveAlbumCover(it, apiBaseUrl) }
            )
        }
    }

    PhotonneRefreshableScreen(
        isRefreshing = state.isLoading && state.items.isNotEmpty(),
        onRefresh = viewModel::refresh
    ) {
      // El cromo superior envuelve TODAS las ramas: el hero también se pinta
      // mientras carga / si falla / si el álbum está vacío, y sin esto esas
      // pantallas se quedarían sin botón de volver.
      Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && state.items.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize()) {
                    hero()
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            state.error?.userMessage != null && state.items.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize()) {
                    hero()
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(state.error?.userMessage!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            state.items.isEmpty() ->
                Column(modifier = Modifier.fillMaxSize()) {
                    hero()
                    EmptyState(
                        icon = Icons.Outlined.PhotoAlbum,
                        title = stringResource(Res.string.album_empty_title),
                        subtitle = stringResource(Res.string.album_empty_subtitle)
                    )
                }
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Solid backdrop: the grid's 2dp inter-cell gaps are
                    // transparent and the detail draws over the albums list, so
                    // without this the previous screen bleeds through the lines.
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val displayItems = state.displayItems
                AssetGrid(
                    items = displayItems,
                    baseUrl = apiBaseUrl,
                    gridState = gridState,
                    contentPadding = gridContentPadding,
                    onItemClick = { index ->
                        if (state.isSelectionActive) {
                            displayItems.getOrNull(index)?.let { viewModel.toggleSelection(it.id) }
                        } else {
                            onItemClick(index)
                        }
                    },
                    onItemLongClick = { index ->
                        displayItems.getOrNull(index)?.let { viewModel.toggleSelection(it.id) }
                    },
                    selectedIds = state.selection,
                    modifier = Modifier.fillMaxWidth().hazeSource(albumHazeState),
                    header = hero
                )
                AlbumGridScrubber(
                    gridState = gridState,
                    cellCount = displayItems.size,
                    headerCount = 1,
                    hazeState = albumHazeState,
                    // Always surface the capture month of the topmost photo so the
                    // user can see "which date am I in" — the whole point of the
                    // scrubber. In album/custom order it isn't strictly monotonic,
                    // but it still answers the question and is discoverable without
                    // first opening the sort sheet.
                    labelForCellIndex = { i ->
                        displayItems.getOrNull(i)?.fileCreatedAt
                            ?.captureLocalDate()?.let(::formatLocalizedMonth)
                    },
                    onDraggingChange = { dragging -> isScrubbing = dragging },
                    // Arranca la pista por debajo del cromo superior (status bar +
                    // cápsula de acciones), que se ceñe a la esquina superior
                    // derecha: si no, la asa sube por detrás de la cápsula y se
                    // solapan.
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = reservedTop + 8.dp)
                        .padding(bottom = gridContentPadding.calculateBottomPadding())
                )

                // Abajo al centro, para no chocar con el asa del scrubber que
                // baja pegada al borde derecho.
                ScrollToTopPill(
                    firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                    isScrollInProgress = { gridState.isScrollInProgress },
                    minIndex = SCROLL_TO_TOP_MIN_CELL,
                    onScrollToTop = {
                        if (gridState.firstVisibleItemIndex > SCROLL_TO_TOP_SNAP_CELL) {
                            gridState.scrollToItem(SCROLL_TO_TOP_SNAP_CELL)
                        }
                        gridState.animateScrollToItem(0)
                    },
                    suppressed = isScrubbing || state.isSelectionActive,
                    hazeState = albumHazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = gridContentPadding.calculateBottomPadding() + 8.dp)
                )
            }
        }

        // Cromo superior (se salta entero durante la selección, donde manda la
        // AssetSelectionTopBar sólida vía el slot del Scaffold).
        if (!state.isSelectionActive) {
            // Scrim permanente de la status bar, para que el reloj y los
            // indicadores del móvil sigan legibles sobre las fotos una vez la
            // portada del hero se ha ido hacia arriba.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusBarTop + 16.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )
            if (chromeAlpha > 0.01f) {
                // Saltada cuando está fundida a cero: si no, se comería toques
                // destinados a las fotos de debajo.
                AlbumDetailTopBar(
                    atTop = atTop,
                    canEdit = album.canWrite || album.isOwner,
                    canDelete = album.isOwner,
                    canShare = album.canWrite || album.isOwner,
                    canManageMembers = album.isOwner || album.canManagePermissions,
                    canLeave = !album.isOwner,
                    onBack = onBack,
                    onSort = { showSortSheet = true },
                    onShare = onShare,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onManageMembers = onManageMembers,
                    onLeave = onLeave,
                    hazeState = albumHazeState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = chromeAlpha }
                )
            }
        }
      }
    }

    if (showSortSheet) {
        AlbumDetailSortSheet(
            sort = state.sort,
            direction = state.direction,
            onDismiss = { showSortSheet = false },
            onSortChange = viewModel::setSort,
            onDirectionChange = viewModel::setDirection
        )
    }
}

/**
 * Top chrome for an open album, mirroring the timeline's: at the very top it
 * rides bare over the hero's darkened cover, and once the grid is scrolled it
 * dissolves into frosted capsules hugging the top corners, so the photos read
 * through them. The caller supplies [atTop] and fades the whole thing via a
 * `graphicsLayer { alpha }`.
 *
 * Two capsules rather than the timeline's one: the album needs a back button on
 * the leading side, where the timeline has its wordmark. The icons are rendered
 * once and never move between the two states — only the backdrop behind them
 * crossfades, and their tint follows it.
 */
@Composable
private fun AlbumDetailTopBar(
    atTop: Boolean,
    canEdit: Boolean,
    canDelete: Boolean,
    canShare: Boolean,
    canManageMembers: Boolean,
    canLeave: Boolean,
    onBack: () -> Unit,
    onSort: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    onLeave: () -> Unit,
    /**
     * Fuente de blur de las cápsulas, pasada explícitamente (es la rejilla que
     * scrollea por detrás). Sin ella las cápsulas caen al gris de reserva.
     */
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val dockedFraction by animateFloatAsState(
        targetValue = if (atTop) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "albumTopBarDocked"
    )
    // Acoplados sobre la portada oscurecida los iconos van en blanco; con el
    // cristal esmerilado detrás toman el color de contenido del cromo (casi
    // blanco en oscuro, casi negro en claro, que es lo legible sobre el gris).
    val iconTint = lerp(MaterialTheme.colorScheme.onSurface, Color.White, dockedFraction)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp, start = 8.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        AlbumChromeCapsule(dockedFraction = dockedFraction, hazeState = hazeState) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close),
                    tint = iconTint
                )
            }
        }
        AlbumChromeCapsule(dockedFraction = dockedFraction, hazeState = hazeState) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSort) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(Res.string.album_action_sort),
                        tint = iconTint
                    )
                }
                if (canShare) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(Res.string.action_share),
                            tint = iconTint
                        )
                    }
                }
                if (canEdit || canDelete || canManageMembers || canLeave) {
                    AlbumActionsOverflowMenu(
                        canEdit = canEdit,
                        canDelete = canDelete,
                        canManageMembers = canManageMembers,
                        canLeave = canLeave,
                        tint = iconTint,
                        onEdit = onEdit,
                        onDelete = onDelete,
                        onManageMembers = onManageMembers,
                        onLeave = onLeave
                    )
                }
            }
        }
    }
}

/** Una cápsula del cromo del álbum: forma + sombra + cristal que se desvanece
 * a medida que la barra se acopla (arriba del todo el fondo lo pone la portada). */
@Composable
private fun AlbumChromeCapsule(
    dockedFraction: Float,
    hazeState: HazeState?,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        // Transparente: la Surface aporta forma + sombra y recorta el cristal.
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 4.dp * (1f - dockedFraction)
    ) {
        Box {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - dockedFraction }
                    .chromeCapsuleBackdrop(hazeState = hazeState)
            )
            Box(modifier = Modifier.padding(horizontal = 2.dp)) { content() }
        }
    }
}

/**
 * Edge-to-edge album cover hero: a darkened, blurred cover carrying the title,
 * count and date. Mirrors the PWA mobile layout (cover background → gradient →
 * title + count + date → description). The back/sort/overflow controls are NOT
 * here — they float above it in [AlbumDetailTopBar], which outlives the hero's
 * trip up the scroll.
 */
@Composable
private fun AlbumHero(
    title: String,
    description: String?,
    assetCount: Int,
    createdAt: Instant,
    isShared: Boolean,
    coverUrl: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .background(MaterialTheme.colorScheme.primary)
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(8.dp)
            )
        }

        // Darkening gradient so the white title stays readable on any cover
        // (and, at rest, the docked chrome's white icons with it).
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.30f),
                            Color.Black.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (isShared) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.Group,
                        contentDescription = stringResource(Res.string.album_hero_shared),
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.size(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroMetaItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    text = pluralStringResource(
                        Res.plurals.album_hero_photos,
                        assetCount,
                        assetCount
                    )
                )
                HeroMetaItem(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    text = formatLocalizedMonth(
                        createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    )
                )
            }

            if (!description.isNullOrBlank()) {
                Spacer(Modifier.size(12.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
        }
    }
}

@Composable
private fun HeroMetaItem(icon: @Composable () -> Unit, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun AlbumActionsOverflowMenu(
    canEdit: Boolean,
    canDelete: Boolean,
    canManageMembers: Boolean,
    canLeave: Boolean,
    tint: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    onLeave: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(Res.string.album_action_album_actions),
                tint = tint
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_edit)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() }
                )
            }
            if (canManageMembers) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.album_action_members)) },
                    leadingIcon = {
                        Icon(Icons.Outlined.People, contentDescription = null)
                    },
                    onClick = { menuOpen = false; onManageMembers() }
                )
            }
            if (canLeave) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_leave)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    },
                    onClick = { menuOpen = false; onLeave() }
                )
            }
            if (canDelete) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(Res.string.action_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

/**
 * Cells scrolled past before the back-to-top pill can appear. The album grid
 * indexes CELLS (the timeline's list indexes rows), so this counts ~3 rows at
 * the usual 3-4 adaptive columns, plus the hero at index 0.
 */
private const val SCROLL_TO_TOP_MIN_CELL = 12

/** Where the tap teleports to before animating the rest of the way up — the
 * same ~12 rows of composition the timeline is willing to animate through. */
private const val SCROLL_TO_TOP_SNAP_CELL = 48

private fun resolveAlbumCover(coverUrl: String, baseUrl: String): String {
    if (coverUrl.startsWith("http://", ignoreCase = true) ||
        coverUrl.startsWith("https://", ignoreCase = true)
    ) {
        return coverUrl
    }
    val sep = if (coverUrl.startsWith("/")) "" else "/"
    return "$baseUrl$sep$coverUrl"
}
