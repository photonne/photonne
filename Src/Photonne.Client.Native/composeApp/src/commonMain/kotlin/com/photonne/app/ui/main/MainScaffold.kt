package com.photonne.app.ui.main

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.AddToPhotos
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.photonne.app.resources.Res
import androidx.compose.material3.TextButton
import com.photonne.app.resources.action_collaborators
import com.photonne.app.resources.action_rename
import com.photonne.app.resources.action_share
import com.photonne.app.resources.album_card_action_leave
import com.photonne.app.resources.albums_action_filters
import com.photonne.app.resources.albums_action_search
import com.photonne.app.resources.albums_title
import com.photonne.app.resources.notifications_action_mark_all_read
import com.photonne.app.resources.filters_action_active
import com.photonne.app.resources.folders_action_filters
import com.photonne.app.resources.folders_action_search
import com.photonne.app.resources.archive_action_unarchive
import com.photonne.app.resources.archive_action_unarchive_all
import com.photonne.app.resources.selection_action_deselect_all
import com.photonne.app.resources.selection_action_download
import com.photonne.app.resources.selection_action_select_all
import com.photonne.app.resources.folder_action_actions
import com.photonne.app.resources.folder_action_move
import com.photonne.app.resources.folder_action_new
import com.photonne.app.resources.folder_selection_move
import com.photonne.app.resources.folder_discovery_add
import com.photonne.app.resources.folder_discovery_remove
import com.photonne.app.resources.people_action_hide
import com.photonne.app.resources.people_action_hide_hidden
import com.photonne.app.resources.people_action_merge
import com.photonne.app.resources.people_action_recluster
import com.photonne.app.resources.people_action_rename
import com.photonne.app.resources.people_action_show_hidden
import com.photonne.app.resources.people_action_suggestions
import com.photonne.app.resources.people_action_suggestions_accept_all
import com.photonne.app.resources.people_action_suggestions_dismiss_all
import com.photonne.app.resources.people_action_unhide
import com.photonne.app.resources.people_action_unlink
import com.photonne.app.resources.people_suggestions_title
import com.photonne.app.resources.trash_action_delete_forever
import com.photonne.app.resources.trash_action_empty
import com.photonne.app.resources.trash_action_restore
import com.photonne.app.resources.trash_action_restore_all
import com.photonne.app.resources.folders_title
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_edit
import com.photonne.app.resources.action_jump_to_date
import com.photonne.app.resources.action_more
import com.photonne.app.resources.action_refresh
import com.photonne.app.resources.album_action_members
import com.photonne.app.resources.album_action_new
import com.photonne.app.resources.app_name
import com.photonne.app.resources.asset_action_set_cover
import com.photonne.app.resources.selection_action_add_to_album
import com.photonne.app.resources.selection_action_archive
import com.photonne.app.resources.selection_action_close
import com.photonne.app.resources.selection_action_more
import com.photonne.app.resources.selection_action_remove_from_album
import com.photonne.app.resources.selection_action_trash
import com.photonne.app.resources.selection_count
import com.photonne.app.resources.selection_label_add_to_album
import com.photonne.app.resources.selection_label_deselect_all
import com.photonne.app.resources.selection_label_download
import com.photonne.app.resources.selection_label_leave
import com.photonne.app.resources.selection_label_members
import com.photonne.app.resources.selection_label_more
import com.photonne.app.resources.selection_label_move
import com.photonne.app.resources.selection_label_remove
import com.photonne.app.resources.selection_label_select_all
import com.photonne.app.resources.selection_label_set_cover
import com.photonne.app.resources.selection_label_share
import com.photonne.app.resources.selection_label_trash
import com.photonne.app.resources.tab_albums
import com.photonne.app.resources.tab_folders
import com.photonne.app.resources.tab_search
import com.photonne.app.resources.tab_more
import com.photonne.app.resources.tab_timeline
import com.photonne.app.resources.timeline_device_loading
import com.photonne.app.resources.upload_title
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.theme.photonneLogoPainter
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class MainTab {
    Timeline,
    Search,
    Albums,
    Folders,
    More
}

@Composable
fun MainScaffold(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    topBar: @Composable () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    moreTabUnreadCount: Int = 0,
    /**
     * When true the content is NOT padded by the top system inset, so it draws
     * to the very top of the screen (under the status bar). Used by the
     * immersive timeline, whose floating top bar handles the status-bar inset
     * itself. The bottom inset is still applied so content clears the nav bar.
     */
    edgeToEdgeTop: Boolean = false,
    /**
     * Immersive timeline: while false the bottom navigation slides down off
     * screen (hidden on scroll), reappearing when it flips back to true. The
     * slot still reserves the bar's height, so the grid never reflows.
     */
    bottomBarVisible: Boolean = true,
    /**
     * Immersive timeline: while true the content is NOT padded by the bottom
     * inset either, so the grid draws full-bleed *behind* the bottom navigation
     * (the bar overlays the photos and reveals them when it slides away). The
     * grid itself reserves the bar's height at its scroll end so the last row
     * still clears it.
     */
    edgeToEdgeBottom: Boolean = false,
    content: @Composable () -> Unit
) {
    val resolvedBottomBar = bottomBar ?: {
        MainNavigationBar(selectedTab, onTabSelected, moreTabUnreadCount)
    }
    // Fuente de blur compartida por el cromo flotante. La publica el composition
    // local para que la nav (y la barra de selección) difumine el contenido de la
    // pantalla activa aunque viva en un slot aparte del Scaffold: la nav es
    // HERMANA del contenido (no descendiente), así que no infringe la regla de
    // Haze. Las pantallas con scroll propio (timeline, álbum, visor, mapa) vuelven
    // a publicar su propio estado para sus cápsulas internas.
    val chromeHazeState = remember { HazeState() }
    CompositionLocalProvider(LocalChromeHazeState provides chromeHazeState) {
    Scaffold(
        topBar = topBar,
        bottomBar = {
            var barHeightPx by remember { mutableStateOf(0) }
            // The bar only slides — no fade. Fading it out mid-slide read as a
            // cut rather than as movement, so the capsule now travels the whole
            // way out on a spring, which decelerates naturally instead of
            // running a fixed curve to a hard stop.
            //
            // It travels a bit past its own height because the capsule's shadow
            // draws *outside* its bounds: stopping at exactly barHeightPx would
            // park that shadow on the screen edge as a faint halo (the alpha
            // used to hide it).
            val shadowSlackPx = with(LocalDensity.current) { 12.dp.toPx() }
            val offsetY by animateFloatAsState(
                targetValue = if (bottomBarVisible) 0f else barHeightPx + shadowSlackPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "bottomBarOffset"
            )
            Box(
                modifier = Modifier
                    .onSizeChanged { barHeightPx = it.height }
                    .graphicsLayer { translationY = offsetY }
            ) {
                resolvedBottomBar()
            }
        }
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = if (edgeToEdgeTop || edgeToEdgeBottom) {
            PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = if (edgeToEdgeTop) 0.dp else padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = if (edgeToEdgeBottom) 0.dp else padding.calculateBottomPadding()
            )
        } else {
            padding
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .hazeSource(chromeHazeState)
        ) {
            content()
        }
    }
    }
}

// Altura del contenido de la barra inferior (sin contar el inset del sistema ni
// los márgenes de la cápsula). Material3 usa 80.dp por defecto; la compactamos
// para que se coma menos pantalla.
//
// Esta geometría (alto, márgenes y forma) es `internal` porque la comparte todo
// lo que flota abajo, incluida la barra de acciones del visor, que vive fuera de
// este Scaffold. Lo que el visor NO comparte es el color: allí el cromo va sobre
// la foto a sangre y tiene su propia paleta.
internal val CompactNavBarContentHeight = 64.dp

// Márgenes que despegan la cápsula de los bordes de la pantalla.
internal val FloatingNavBarHorizontalMargin = 12.dp
internal val FloatingNavBarBottomMargin = 8.dp
// Aire entre el final del contenido y el borde de la cápsula. Reservar solo su
// altura exacta deja el último elemento pegado debajo: como la cápsula es
// translúcida y su sombra se derrama hacia arriba, eso se lee como "tapado"
// aunque técnicamente quede libre.
private val FloatingNavBarContentGap = 12.dp
// Cápsula completa, a juego con la píldora flotante del timeline.
internal val FloatingNavBarShape = RoundedCornerShape(percent = 50)
// Aire entre el borde de la cápsula y el velo del elemento activo. Simétrico con
// [FloatingNavBarItemsPadding] (el margen horizontal exterior) para que el pill
// concéntrico deje el mismo hueco por los cuatro lados.
private val FloatingNavItemMargin = 6.dp
// Aire entre el pill del primer/último ítem y el borde de la cápsula, a juego
// con el margen vertical para que el velo quede centrado en ella.
private val FloatingNavBarItemsPadding = 6.dp
// Hueco entre pills. Como la cápsula ya no reparte el ancho de la pantalla,
// este gap es lo único que separa un ítem del siguiente.
private val FloatingNavItemGap = 4.dp
// Ancho de cada ítem de las barras flotantes, COMPARTIDO por la nav y por las
// barras de selección (asset/álbum/carpeta): no baja de este mínimo y lleva este
// padding horizontal, y luego el EqualWidthRow iguala todos entre sí. Que ambos
// ítems apliquen la misma pareja es lo que hace que un ítem de selección mida lo
// mismo que uno de la nav en vez de quedar ceñido y estrecho.
private val FloatingNavItemMinWidth = 64.dp
private val FloatingNavItemContentPadding = 14.dp

/**
 * Hueco que debe reservar al final de su scroll una pantalla que dibuja a
 * sangre por debajo de la nav flotante: todo lo que ocupa la cápsula (inset del
 * sistema + su margen + su contenido) más un respiro, para que el último
 * elemento no quede lamiendo el borde de la barra.
 */
@Composable
internal fun floatingNavBarReservedHeight(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        FloatingNavBarBottomMargin + CompactNavBarContentHeight +
        FloatingNavBarContentGap

@Composable
private fun MainNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    moreTabUnreadCount: Int = 0
) {
    // El inset del sistema y los márgenes van *fuera* de la Surface: es lo que la
    // despega de los bordes y la hace flotar, en vez de que pinte el fondo por
    // detrás del inset como haría una barra acoplada. La cápsula se ajusta a sus
    // ítems y va centrada; el margen horizontal solo es el tope por si la fila
    // llegara a rozar los bordes (etiquetas largas, pantalla estrecha).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(
                start = FloatingNavBarHorizontalMargin,
                end = FloatingNavBarHorizontalMargin,
                bottom = FloatingNavBarBottomMargin
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = FloatingNavBarShape,
            // Transparente: aporta forma + sombra y RECORTA el cristal a la
            // cápsula. El esmerilado lo pinta el Box de fondo; el contenido va en
            // `onSurface` (blanco en oscuro). `tonalElevation` sería un no-op aquí.
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp
        ) {
          Box {
            Box(Modifier.matchParentSize().chromeCapsuleBackdrop())
            EqualWidthRow(
                modifier = Modifier
                    .height(CompactNavBarContentHeight)
                    .padding(horizontal = FloatingNavBarItemsPadding)
                    .selectableGroup(),
                horizontalGap = FloatingNavItemGap
            ) {
                val timelineActive = selectedTab == MainTab.Timeline
                FloatingNavBarItem(
                    selected = timelineActive,
                    onClick = { onTabSelected(MainTab.Timeline) },
                    label = stringResource(Res.string.tab_timeline),
                    icon = {
                        Icon(
                            if (timelineActive) Icons.Filled.PhotoLibrary
                            else Icons.Outlined.PhotoLibrary,
                            contentDescription = null
                        )
                    }
                )
                val albumsActive = selectedTab == MainTab.Albums
                FloatingNavBarItem(
                    selected = albumsActive,
                    onClick = { onTabSelected(MainTab.Albums) },
                    label = stringResource(Res.string.tab_albums),
                    icon = {
                        Icon(
                            if (albumsActive) Icons.Filled.Collections
                            else Icons.Outlined.Collections,
                            contentDescription = null
                        )
                    }
                )
                val foldersActive = selectedTab == MainTab.Folders
                FloatingNavBarItem(
                    selected = foldersActive,
                    onClick = { onTabSelected(MainTab.Folders) },
                    label = stringResource(Res.string.tab_folders),
                    icon = {
                        Icon(
                            if (foldersActive) Icons.Filled.Folder else Icons.Outlined.Folder,
                            contentDescription = null
                        )
                    }
                )
                val moreActive = selectedTab == MainTab.More
                FloatingNavBarItem(
                    selected = moreActive,
                    onClick = { onTabSelected(MainTab.More) },
                    label = stringResource(Res.string.tab_more),
                    icon = {
                        val moreIcon = if (moreActive) Icons.Filled.GridView else Icons.Outlined.GridView
                        if (moreTabUnreadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(
                                            if (moreTabUnreadCount > 99) "99+"
                                            else moreTabUnreadCount.toString()
                                        )
                                    }
                                }
                            ) {
                                Icon(moreIcon, contentDescription = null)
                            }
                        } else {
                            Icon(moreIcon, contentDescription = null)
                        }
                    }
                )
            }
          }
        }
    }
}

/**
 * Fila que iguala a TODOS sus hijos al ancho del más ancho (mide el
 * `maxIntrinsicWidth` de cada uno y aplica el mayor a todos), y los ENCOGE en
 * proporción si no caben en el ancho disponible para no desbordar. Así los ítems
 * de la nav y de la barra de selección se ven regulares entre sí en vez de cada
 * uno ceñido a su etiqueta.
 */
@Composable
private fun EqualWidthRow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Layout(modifier = modifier, content = content) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(0, 0) {}
        val gapPx = horizontalGap.roundToPx()
        val count = measurables.size
        val totalGap = gapPx * (count - 1)
        val widest = measurables.maxOf { it.maxIntrinsicWidth(constraints.maxHeight) }
        val hasBoundedWidth = constraints.maxWidth != Constraints.Infinity
        val itemWidth = if (hasBoundedWidth) {
            val available = ((constraints.maxWidth - totalGap) / count).coerceAtLeast(0)
            minOf(widest, available)
        } else {
            widest
        }
        val itemConstraints = Constraints(
            minWidth = itemWidth,
            maxWidth = itemWidth,
            minHeight = 0,
            maxHeight = constraints.maxHeight
        )
        val placeables = measurables.map { it.measure(itemConstraints) }
        val rowWidth = itemWidth * count + totalGap
        val width = if (hasBoundedWidth) rowWidth.coerceAtMost(constraints.maxWidth) else rowWidth
        val height = (placeables.maxOfOrNull { it.height } ?: 0)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, (height - placeable.height) / 2)
                x += itemWidth + gapPx
            }
        }
    }
}

/**
 * Un botón de la nav flotante. No usamos [NavigationBarItem] porque su
 * indicador solo envuelve el icono y no es configurable: aquí el sombreado del
 * elemento activo cubre el botón entero (icono + etiqueta).
 *
 * El área táctil es el propio pill (48.dp de alto, el mínimo recomendado), así
 * el ripple queda recortado a la forma que se ve en vez de derramarse por toda
 * la celda.
 *
 * El pill se mide por su contenido (con un mínimo) en vez de repartirse el ancho
 * de la pantalla: es lo que mantiene la cápsula pegada a sus ítems.
 */
@Composable
private fun FloatingNavBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: @Composable () -> Unit
) {
    // El contenido va SIEMPRE en blanco (onSurface), activo o no. El activo se
    // marca con dos cosas: el icono relleno (lo pone el llamador) Y un velo
    // concéntrico recortado con la forma de la cápsula. El ancho lo iguala el
    // [EqualWidthRow] padre, así que aquí solo se ciñe a su contenido.
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = FloatingNavItemMargin)
            .clip(FloatingNavBarShape)
            .background(if (selected) chromeActivePillColor() else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            // Misma pareja min-width + padding que el ítem de selección, así los
            // dos tipos de barra flotante miden igual (ver constantes).
            .widthIn(min = FloatingNavItemMinWidth)
            .padding(horizontal = FloatingNavItemContentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                icon()
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * La cápsula que envuelve cualquier barra de acciones de selección. Comparte
 * forma, color, altura y márgenes con [MainNavigationBar] a propósito: la barra
 * de selección *sustituye* a la nav (ver `resolvedBottomBar`), así que entrar en
 * selección se lee como que la cápsula cambia de contenido, no como que aparece
 * otra barra encima.
 *
 * Comparte también la estrategia de ancho: se ciñe a sus ítems, va centrada y
 * los iguala entre sí con el mismo [EqualWidthRow] que la nav. Si en una pantalla
 * estrecha no caben, el row los encoge en proporción en vez de desbordar.
 */
@Composable
private fun FloatingSelectionBar(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .padding(
                start = FloatingNavBarHorizontalMargin,
                end = FloatingNavBarHorizontalMargin,
                bottom = FloatingNavBarBottomMargin
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = FloatingNavBarShape,
            // Mismo patrón de cápsula que la nav: transparente + cristal de fondo.
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 6.dp
        ) {
          Box {
            Box(Modifier.matchParentSize().chromeCapsuleBackdrop())
            EqualWidthRow(
                modifier = Modifier
                    .height(CompactNavBarContentHeight)
                    .padding(horizontal = FloatingNavBarItemsPadding),
                horizontalGap = FloatingNavItemGap,
                content = content
            )
          }
        }
    }
}

/**
 * Un botón de la cápsula de selección. Hermano de [FloatingNavBarItem], pero sin
 * estado seleccionado: estos ítems disparan una acción, no marcan dónde estás.
 *
 * Se mide por su contenido (con un mínimo) y lo iguala el [EqualWidthRow] padre,
 * en vez de repartirse el ancho con `weight`; el ripple queda recortado a la
 * misma forma que la cápsula.
 *
 * [tint] es para las acciones destructivas; si no se pasa, hereda el color de la
 * cápsula. Deshabilitado baja el alpha del conjunto (icono + etiqueta) al 0.38
 * que usa Material para el estado disabled.
 */
@Composable
private fun FloatingSelectionBarItem(
    onClick: () -> Unit,
    enabled: Boolean,
    label: String,
    tint: Color = Color.Unspecified,
    icon: @Composable () -> Unit
) {
    val base = if (tint != Color.Unspecified) tint else LocalContentColor.current
    val contentColor = if (enabled) base else base.copy(alpha = 0.38f)
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = FloatingNavItemMargin)
            .clip(FloatingNavBarShape)
            .clickable(enabled = enabled, onClick = onClick)
            // Misma pareja min-width + padding que el ítem de la nav: antes solo
            // tenía el mínimo y se ceñía al contenido, por eso se veía estrecho.
            .widthIn(min = FloatingNavItemMinWidth)
            .padding(horizontal = FloatingNavItemContentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                icon()
                SelectionLabel(label)
            }
        }
    }
}

/**
 * The one and only "add something here" affordance, shared by every top bar that
 * has one (upload on Fotos/Más, new album, new folder/subfolder, new admin user/
 * library). It used to be three patterns — a Scaffold FAB, a plain [IconButton]
 * and a hand-placed `ExtendedFloatingActionButton` — for the same idea.
 *
 * Tonal rather than a `primary`-filled button: it has to sit on the timeline's
 * translucent pill without turning into a blob, while still reading as something
 * other than the flat icons beside it.
 *
 * Callers put it **first** in their `actions` row. It occupies the same 48.dp as
 * an [IconButton], so it drops into an existing row without shifting anything.
 */
@Composable
fun CreateAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/**
 * Top chrome for the timeline. At the very top it reads as a docked, opaque bar
 * carrying the wordmark; once the grid is scrolled the bar dissolves into a
 * compact translucent pill hugging the top-end corner, so the photos read
 * through it. The caller supplies the [atTop] state and fades the whole thing
 * via a `graphicsLayer { alpha }`.
 *
 * The action icons are rendered **once**, at a position that does not depend on
 * which backdrop is showing — only the backdrop behind them crossfades. Two
 * separate rows (one inside a docked `TopAppBar`, one inside the pill) is what
 * used to make the icons slide sideways on every swap: M3 insets a `TopAppBar`'s
 * actions by its own private 4.dp, while the pill's outer + inner margins added
 * up to 10.dp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineTopBar(
    atTop: Boolean,
    onJumpToDate: () -> Unit,
    currentZoom: com.photonne.app.data.settings.TimelineZoomLevel,
    onZoomSelected: (com.photonne.app.data.settings.TimelineZoomLevel) -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    deviceLoading: Boolean = false,
    /**
     * Fuente de blur de la píldora, pasada explícitamente por el timeline (es la
     * rejilla que scrollea por detrás). Sin ella la píldora cae al gris de
     * reserva. No se hereda por el composition local porque la píldora es
     * descendiente del contenido de `MainScaffold`, no de la rejilla.
     */
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val dockedFraction by animateFloatAsState(
        targetValue = if (atTop) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "timelineTopBarDocked"
    )
    Box(modifier = modifier.fillMaxWidth()) {
        // Docked backdrop: a real TopAppBar, so the wordmark keeps its stock M3
        // placement and the bar its stock 64.dp height. It carries no actions —
        // those are the shared row below. Dropped once fully faded, or its
        // Surface would keep swallowing taps meant for the photos underneath.
        if (dockedFraction > 0.01f) {
            TopAppBar(
                title = {
                    Image(
                        painter = photonneLogoPainter(),
                        contentDescription = stringResource(Res.string.app_name),
                        modifier = Modifier.height(32.dp)
                    )
                },
                modifier = Modifier.graphicsLayer { alpha = dockedFraction }
            )
        }
        // The one and only actions row. `top = 8.dp` lands its centre exactly on
        // the docked bar's own action centre ((64.dp bar - 48.dp icons) / 2), so
        // nothing shifts vertically either when the backdrop swaps.
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp, end = 8.dp),
            shape = RoundedCornerShape(percent = 50),
            // Transparente: la Surface aporta forma + sombra y recorta el cristal.
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 4.dp * (1f - dockedFraction)
        ) {
          Box {
            // El cristal se desvanece a medida que la barra se acopla (arriba del
            // todo el fondo lo pone el TopAppBar acoplado que hay detrás); los
            // iconos de la fila comparten posición pero siguen opacos.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - dockedFraction }
                    .chromeCapsuleBackdrop(hazeState = hazeState)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                // Discreet spinner while the first device-gallery scan runs. Sits
                // among the actions so it never overlaps the wordmark, and reads
                // the same whether or not the Recuerdos strip is present.
                if (deviceLoading) {
                    val scanLabel = stringResource(Res.string.timeline_device_loading)
                    val tooltipState = rememberTooltipState()
                    val scope = rememberCoroutineScope()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(scanLabel) } },
                        state = tooltipState,
                        // Long-press is meaningless for a progress spinner on mobile;
                        // reveal the label on a plain tap instead (see clickable below).
                        enableUserInput = false
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { scope.launch { tooltipState.show() } },
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .size(18.dp)
                                    .semantics { contentDescription = scanLabel }
                            )
                        }
                    }
                }
                if (onOpenSearch != null) {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = stringResource(Res.string.tab_search)
                        )
                    }
                }
                com.photonne.app.ui.timeline.TimelineZoomMenuAction(
                    current = currentZoom,
                    onSelect = onZoomSelected
                )
                IconButton(onClick = onJumpToDate) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        contentDescription = stringResource(Res.string.action_jump_to_date)
                    )
                }
            }
          }
        }
    }
}

/** Slim top bar for the Inicio (Hub) view — just the wordmark. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubTopBar() {
    TopAppBar(
        title = {
            Image(
                painter = photonneLogoPainter(),
                contentDescription = stringResource(Res.string.app_name),
                modifier = Modifier.height(32.dp)
            )
        }
    )
}

/** Whether the unified selection top bar shows "Archive" or "Unarchive" on the archive icon. */
enum class ArchiveMode { Archive, Unarchive }

/**
 * Slim selection top bar: only the close (X) navigation icon and the
 * selection counter. All action buttons live in [AssetSelectionBottomBar]
 * so they stay within easy thumb reach on mobile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    totalCount: Int = 0,
    onSelectAll: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    // "Select all" lives here, next to the count, because it controls the
    // *scope* of the selection rather than acting on it — keeping the bottom
    // bar for actual actions (share / album / download / trash).
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(
                    Res.plurals.selection_count,
                    selectedCount,
                    selectedCount
                ),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            if (onSelectAll != null) {
                IconButton(onClick = onSelectAll, enabled = !isMutating) {
                    Icon(
                        Icons.Outlined.SelectAll,
                        contentDescription = stringResource(
                            if (allSelected) Res.string.selection_action_deselect_all
                            else Res.string.selection_action_select_all
                        ),
                        tint = if (allSelected) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current
                    )
                }
            }
            actions()
        }
    )
}

/**
 * Unified selection bottom bar used across every screen that supports
 * multi-asset selection. Primary slots: Select all, Add to album, Download, Trash.
 * Optional context actions live in the overflow menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSelectionBottomBar(
    isMutating: Boolean,
    archiveMode: ArchiveMode = ArchiveMode.Archive,
    onShare: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDownload: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onMove: (() -> Unit)? = null,
    onUnlink: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null,
    onSetAsCover: (() -> Unit)? = null
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // When any context-specific action is wired (Move/Remove/SetCover/Unlink),
    // Download moves to the overflow so the bar keeps a stable 4-item primary
    // count + More. The context action takes Download's slot.
    val hasContextAction = onMove != null || onRemoveFromAlbum != null ||
        onSetAsCover != null || onUnlink != null
    FloatingSelectionBar {
        FloatingSelectionBarItem(
            onClick = onShare,
            enabled = !isMutating,
            label = stringResource(Res.string.selection_label_share),
            icon = {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = stringResource(Res.string.action_share)
                )
            }
        )
        FloatingSelectionBarItem(
            onClick = onAddToAlbum,
            enabled = !isMutating,
            label = stringResource(Res.string.selection_label_add_to_album),
            icon = {
                Icon(
                    Icons.Outlined.AddToPhotos,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
        )
        if (!hasContextAction) {
            FloatingSelectionBarItem(
                onClick = onDownload,
                enabled = !isMutating,
                label = stringResource(Res.string.selection_label_download),
                icon = {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = stringResource(Res.string.selection_action_download)
                    )
                }
            )
        }
        if (onMove != null) {
            FloatingSelectionBarItem(
                onClick = onMove,
                enabled = !isMutating,
                label = stringResource(Res.string.selection_label_move),
                icon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.DriveFileMove,
                        contentDescription = stringResource(Res.string.folder_selection_move)
                    )
                }
            )
        }
        if (onRemoveFromAlbum != null) {
            FloatingSelectionBarItem(
                onClick = onRemoveFromAlbum,
                enabled = !isMutating,
                label = stringResource(Res.string.selection_label_remove),
                icon = {
                    Icon(
                        Icons.Outlined.RemoveCircleOutline,
                        contentDescription = stringResource(Res.string.selection_action_remove_from_album)
                    )
                }
            )
        }
        if (onSetAsCover != null) {
            FloatingSelectionBarItem(
                onClick = onSetAsCover,
                enabled = !isMutating,
                label = stringResource(Res.string.selection_label_set_cover),
                icon = {
                    Icon(
                        Icons.Outlined.PhotoAlbum,
                        contentDescription = stringResource(Res.string.asset_action_set_cover)
                    )
                }
            )
        }
        if (onUnlink != null) {
            FloatingSelectionBarItem(
                onClick = onUnlink,
                enabled = !isMutating,
                label = stringResource(Res.string.people_action_unlink),
                icon = {
                    Icon(
                        Icons.Outlined.LinkOff,
                        contentDescription = stringResource(Res.string.people_action_unlink)
                    )
                }
            )
        }
        FloatingSelectionBarItem(
            onClick = { menuOpen = true },
            enabled = !isMutating,
            label = stringResource(Res.string.selection_label_more),
            icon = {
                Box {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.selection_action_more)
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (hasContextAction) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.selection_action_download)) },
                                leadingIcon = {
                                    Icon(Icons.Outlined.Download, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onDownload() }
                            )
                        }
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (archiveMode == ArchiveMode.Unarchive)
                                            Res.string.archive_action_unarchive
                                        else Res.string.selection_action_archive
                                    )
                                )
                            },
                            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                            onClick = { menuOpen = false; onArchive() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(Res.string.selection_action_trash),
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
                            onClick = { menuOpen = false; onTrash() }
                        )
                    }
                }
            }
        )
    }
}

/**
 * Label used by every selection bottom-bar item. Uses [MaterialTheme.typography.labelSmall]
 * so longer Spanish labels (e.g. "Deseleccionar") fit on a single line, and clamps to one
 * line with ellipsis as a safety net.
 *
 * El color lo pone [FloatingSelectionBarItem] vía `LocalContentColor`, que es lo
 * que mantiene icono y etiqueta a juego tanto en las acciones destructivas como
 * en el estado deshabilitado.
 */
@Composable
private fun SelectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsListTopBar(
    onOpenFilters: () -> Unit,
    isFilterActive: Boolean = false,
    isSearchActive: Boolean = false,
    onToggleSearch: () -> Unit = {},
    /** Opens the album-type chooser. Null hides the action. */
    onCreateAlbum: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Text(
                stringResource(Res.string.albums_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            if (onCreateAlbum != null) {
                CreateAction(
                    icon = Icons.Outlined.AddBox,
                    contentDescription = stringResource(Res.string.album_action_new),
                    onClick = onCreateAlbum
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = stringResource(Res.string.albums_action_search)
                )
            }
            IconButton(onClick = onOpenFilters) {
                Icon(
                    imageVector = if (isFilterActive) Icons.Filled.Tune else Icons.Outlined.Tune,
                    contentDescription = stringResource(
                        if (isFilterActive) Res.string.filters_action_active
                        else Res.string.albums_action_filters
                    ),
                    tint = if (isFilterActive) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
        }
    )
}

/**
 * Slim top bar shown when a single album card is selected from the list:
 * just Close (X) and the album name. Actions live in [AlbumCardSelectionBottomBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCardSelectionTopBar(
    albumName: String,
    isMutating: Boolean,
    onClose: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(albumName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    )
}

/**
 * Bottom bar for the single-album-card selection. Mirrors the PWA's Albums
 * selection toolbar (Members, Edit, Leave, Delete) gated by permissions.
 * Delete carries the error tint since it's destructive.
 */
@Composable
fun AlbumCardSelectionBottomBar(
    canManageMembers: Boolean,
    canEdit: Boolean,
    canLeave: Boolean,
    canDelete: Boolean,
    isMutating: Boolean,
    onManageMembers: () -> Unit,
    onEdit: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit
) {
    FloatingSelectionBar {
        if (canManageMembers) {
            FloatingSelectionBarItem(
                onClick = onManageMembers,
                enabled = !isMutating,
                label = stringResource(Res.string.selection_label_members),
                icon = {
                    Icon(
                        Icons.Outlined.Group,
                        contentDescription = stringResource(Res.string.action_collaborators)
                    )
                }
            )
        }
        if (canEdit) {
            FloatingSelectionBarItem(
                onClick = onEdit,
                enabled = !isMutating,
                label = stringResource(Res.string.action_edit),
                icon = {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(Res.string.action_edit)
                    )
                }
            )
        }
        if (canLeave) {
            FloatingSelectionBarItem(
                onClick = onLeave,
                enabled = !isMutating,
                label = stringResource(Res.string.selection_label_leave),
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(Res.string.album_card_action_leave)
                    )
                }
            )
        }
        if (canDelete) {
            FloatingSelectionBarItem(
                onClick = onDelete,
                enabled = !isMutating,
                label = stringResource(Res.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
                icon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(Res.string.action_delete)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersListTopBar(
    onOpenFilters: () -> Unit,
    isFilterActive: Boolean = false,
    isSearchActive: Boolean = false,
    onToggleSearch: () -> Unit = {},
    /** Create a root folder. Null hides the action (e.g. while showing Externas). */
    onCreateFolder: (() -> Unit)? = null
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.folders_title), style = MaterialTheme.typography.titleMedium) },
        actions = {
            if (onCreateFolder != null) {
                CreateAction(
                    icon = Icons.Outlined.CreateNewFolder,
                    contentDescription = stringResource(Res.string.folder_action_new),
                    onClick = onCreateFolder
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (isSearchActive) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = stringResource(Res.string.folders_action_search)
                )
            }
            IconButton(onClick = onOpenFilters) {
                Icon(
                    imageVector = if (isFilterActive) Icons.Filled.Tune else Icons.Outlined.Tune,
                    contentDescription = stringResource(
                        if (isFilterActive) Res.string.filters_action_active
                        else Res.string.folders_action_filters
                    ),
                    tint = if (isFilterActive) MaterialTheme.colorScheme.primary
                    else LocalContentColor.current
                )
            }
        }
    )
}

/**
 * Slim top bar for folder card selection: Close (X) + folder name only.
 * Actions live in [FolderCardSelectionBottomBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderCardSelectionTopBar(
    folderName: String,
    isMutating: Boolean,
    onClose: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(folderName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }
    )
}

/**
 * Bottom bar for the single-folder-card selection. Mirrors the PWA's Folders
 * selection toolbar (Members, Rename, Delete) gated by permissions.
 */
@Composable
fun FolderCardSelectionBottomBar(
    canManageMembers: Boolean,
    canRename: Boolean,
    canDelete: Boolean,
    isMutating: Boolean,
    onManageMembers: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    // Per-user timeline opt-out. Only meaningful for shared folders.
    canToggleTimeline: Boolean = false,
    excludedFromDiscovery: Boolean = false,
    onToggleTimeline: () -> Unit = {}
) {
    FloatingSelectionBar {
        if (canToggleTimeline) {
            val label = stringResource(
                if (excludedFromDiscovery) Res.string.folder_discovery_add
                else Res.string.folder_discovery_remove
            )
            FloatingSelectionBarItem(
                onClick = onToggleTimeline,
                enabled = !isMutating,
                label = label,
                icon = {
                    Icon(
                        if (excludedFromDiscovery) Icons.Outlined.Visibility
                        else Icons.Outlined.VisibilityOff,
                        contentDescription = label
                    )
                }
            )
        }
        if (canManageMembers) {
            FloatingSelectionBarItem(
                onClick = onManageMembers,
                enabled = !isMutating,
                label = stringResource(Res.string.selection_label_members),
                icon = {
                    Icon(
                        Icons.Outlined.Group,
                        contentDescription = stringResource(Res.string.action_collaborators)
                    )
                }
            )
        }
        if (canRename) {
            FloatingSelectionBarItem(
                onClick = onRename,
                enabled = !isMutating,
                label = stringResource(Res.string.action_rename),
                icon = {
                    Icon(
                        Icons.Outlined.DriveFileRenameOutline,
                        contentDescription = stringResource(Res.string.action_rename)
                    )
                }
            )
        }
        if (canDelete) {
            FloatingSelectionBarItem(
                onClick = onDelete,
                enabled = !isMutating,
                label = stringResource(Res.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
                icon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(Res.string.action_delete)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailTopBar(
    title: String,
    subtitle: String?,
    canEdit: Boolean,
    canDelete: Boolean,
    canManageMembers: Boolean,
    canMove: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onManageMembers: () -> Unit,
    // Per-user timeline opt-out. Only meaningful for shared folders.
    canToggleTimeline: Boolean = false,
    excludedFromDiscovery: Boolean = false,
    onToggleTimeline: () -> Unit = {},
    /** Create a subfolder of this folder. Null hides the action. */
    onCreateSubfolder: (() -> Unit)? = null
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val hasMenu = canEdit || canDelete || canManageMembers || canMove || canToggleTimeline
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            if (onCreateSubfolder != null) {
                CreateAction(
                    icon = Icons.Outlined.CreateNewFolder,
                    contentDescription = stringResource(Res.string.folder_action_new),
                    onClick = onCreateSubfolder
                )
            }
            if (hasMenu) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.folder_action_actions)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (canToggleTimeline) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (excludedFromDiscovery) Res.string.folder_discovery_add
                                            else Res.string.folder_discovery_remove
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (excludedFromDiscovery) Icons.Outlined.Visibility
                                        else Icons.Outlined.VisibilityOff,
                                        contentDescription = null
                                    )
                                },
                                onClick = { menuOpen = false; onToggleTimeline() }
                            )
                        }
                        if (canEdit) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.action_edit)) },
                                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; onEdit() }
                            )
                        }
                        if (canMove) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.folder_action_move)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null)
                                },
                                onClick = { menuOpen = false; onMove() }
                            )
                        }
                        if (canManageMembers) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.album_action_members)) },
                                leadingIcon = { Icon(Icons.Outlined.People, contentDescription = null) },
                                onClick = { menuOpen = false; onManageMembers() }
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
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onMoveToFolder: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            TextButton(onClick = onMoveToFolder, enabled = !isMutating) {
                Text(stringResource(Res.string.folder_selection_move))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar() {
    TopAppBar(
        title = {
            Text(
                stringResource(Res.string.tab_search),
                style = MaterialTheme.typography.titleMedium
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreTopBar(onOpenUpload: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(stringResource(Res.string.tab_more), style = MaterialTheme.typography.titleMedium) },
        actions = {
            if (onOpenUpload != null) {
                CreateAction(
                    icon = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = stringResource(Res.string.upload_title),
                    onClick = onOpenUpload
                )
            }
        }
    )
}

/** Generic title + optional subtitle + back button used by every settings sub-page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = actions
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsTopBar(
    title: String,
    canMarkAllRead: Boolean,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        actions = {
            IconButton(onClick = onMarkAllRead, enabled = canMarkAllRead) {
                Icon(
                    Icons.Outlined.DoneAll,
                    contentDescription = stringResource(
                        Res.string.notifications_action_mark_all_read
                    )
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTopBar(
    title: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = stringResource(Res.string.action_refresh)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedTopBar(
    title: String,
    subtitle: String?,
    canUnarchiveAll: Boolean,
    onBack: () -> Unit,
    onUnarchiveAll: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            if (canUnarchiveAll) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.action_more)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.archive_action_unarchive_all)) },
                            onClick = { menuOpen = false; onUnarchiveAll() }
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashTopBar(
    title: String,
    subtitle: String?,
    canActOnAll: Boolean,
    onBack: () -> Unit,
    onRestoreAll: () -> Unit,
    onEmptyTrash: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            if (canActOnAll) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(Res.string.action_more)
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.trash_action_restore_all)) },
                            onClick = { menuOpen = false; onRestoreAll() }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(Res.string.trash_action_empty),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { menuOpen = false; onEmptyTrash() }
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleTopBar(
    title: String,
    onBack: () -> Unit,
    onRecluster: () -> Unit,
    showHidden: Boolean,
    onToggleHidden: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.people_action_recluster)) },
                        onClick = { menuOpen = false; onRecluster() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (showHidden) stringResource(Res.string.people_action_hide_hidden)
                                else stringResource(Res.string.people_action_show_hidden)
                            )
                        },
                        onClick = { menuOpen = false; onToggleHidden() }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailTopBar(
    title: String,
    subtitle: String?,
    isHidden: Boolean,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onSuggestions: () -> Unit,
    onMerge: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.people_action_rename)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.people_action_suggestions)) },
                        onClick = { menuOpen = false; onSuggestions() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.people_action_merge)) },
                        onClick = { menuOpen = false; onMerge() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isHidden) stringResource(Res.string.people_action_unhide)
                                else stringResource(Res.string.people_action_hide)
                            )
                        },
                        onClick = { menuOpen = false; onToggleHidden() }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonSuggestionsTopBar(
    title: String,
    subtitle: String?,
    isBulkMutating: Boolean,
    onBack: () -> Unit,
    onAcceptAll: () -> Unit,
    onDismissAll: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(Res.string.action_close)
                )
            }
        },
        title = {
            androidx.compose.foundation.layout.Column {
                Text(
                    stringResource(Res.string.people_suggestions_title, title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = !isBulkMutating) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(Res.string.action_more)
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(Res.string.people_action_suggestions_accept_all))
                        },
                        onClick = { menuOpen = false; onAcceptAll() }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(Res.string.people_action_suggestions_dismiss_all))
                        },
                        onClick = { menuOpen = false; onDismissAll() }
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onUnlink: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            TextButton(onClick = onUnlink, enabled = !isMutating) {
                Text(stringResource(Res.string.people_action_unlink))
            }
            IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.AddToPhotos,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onArchive, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Archive,
                    contentDescription = stringResource(Res.string.selection_action_archive)
                )
            }
            IconButton(onClick = onTrash, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.selection_action_trash),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/**
 * Selection top bar for the Favorites screen — the same bulk vocabulary
 * as the Timeline selection bar (Add to album, Archive, Trash), since
 * "Unfavorite" can be done from the asset viewer per item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.AddToPhotos,
                    contentDescription = stringResource(Res.string.selection_action_add_to_album)
                )
            }
            IconButton(onClick = onArchive, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Archive,
                    contentDescription = stringResource(Res.string.selection_action_archive)
                )
            }
            IconButton(onClick = onTrash, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.selection_action_trash),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/** Selection top bar tailored to the Archived screen — only exposes Unarchive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onUnarchive: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            TextButton(onClick = onUnarchive, enabled = !isMutating) {
                Text(stringResource(Res.string.archive_action_unarchive))
            }
        }
    )
}

/** Selection top bar tailored to the Trash screen — Restore + Delete forever. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashSelectionTopBar(
    selectedCount: Int,
    isMutating: Boolean,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose, enabled = !isMutating) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.selection_action_close)
                )
            }
        },
        title = {
            Text(
                text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        actions = {
            TextButton(onClick = onRestore, enabled = !isMutating) {
                Text(stringResource(Res.string.trash_action_restore))
            }
            IconButton(onClick = onPurge, enabled = !isMutating) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(Res.string.trash_action_delete_forever),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}
