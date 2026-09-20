package com.photonne.app.ui.grid

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.LocalSyncBadge
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.resources.Res
import com.photonne.app.resources.asset_a11y_favorite
import com.photonne.app.resources.asset_a11y_live_photo
import com.photonne.app.resources.asset_a11y_video
import com.photonne.app.resources.sync_badge_failed
import com.photonne.app.resources.sync_badge_pending
import com.photonne.app.resources.sync_badge_uploading
import com.photonne.app.ui.grid.dragselect.AssetCellContentType
import com.photonne.app.ui.grid.dragselect.DragSelectConfig
import com.photonne.app.ui.grid.dragselect.DragSelectState
import com.photonne.app.ui.grid.dragselect.dragSelectable
import com.photonne.app.ui.grid.dragselect.rememberLazyGridDragSelectAdapter
import com.photonne.app.ui.haptics.rememberPhotonneHaptics
import com.photonne.app.ui.image.AssetThumbnailImage
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.rangeSelectionIds
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.LocalCurrentDetailAssetId
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.LocalSharedTransitionScope
import com.photonne.app.ui.util.onSecondaryClick
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private const val PREFETCH_THRESHOLD = 12

/**
 * Reusable square thumbnail grid shared by the Timeline and Album
 * detail. Owns the prefetch trigger that calls [onLoadMore] when the
 * user scrolls near the end of [items].
 */
@Composable
fun AssetGrid(
    items: List<TimelineItem>,
    baseUrl: String,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    /** Padding around the grid content — e.g. to reserve the immersive nav's
     * height at the scroll end so the last row clears the overlaid bar. */
    contentPadding: PaddingValues = PaddingValues(0.dp),
    hasMore: Boolean = false,
    isAppending: Boolean = false,
    isInitialLoading: Boolean = false,
    onLoadMore: () -> Unit = {},
    onItemLongClick: ((Int) -> Unit)? = null,
    selectedIds: Set<String> = emptySet(),
    /**
     * Arrastre en banda. Cuando llega, el long-press deja de gestionarlo la
     * celda y pasa a gestionarlo la rejilla: si ambos lo escuchan se cancelan
     * mutuamente y no entra ninguno.
     */
    dragSelect: AssetGridDragSelect? = null,
    header: (@Composable () -> Unit)? = null
) {
    val shouldLoadMore by remember(hasMore, isAppending, isInitialLoading) {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - PREFETCH_THRESHOLD &&
                hasMore && !isAppending && !isInitialLoading
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    val headerCount = if (header != null) 1 else 0
    // Ancla del Shift+clic: la última celda tocada (toggle, long-press, clic
    // derecho o clic en selección). Por id y no por índice: la paginación
    // añade elementos y el índice del ancla se movería bajo el usuario.
    var rangeAnchorId by remember { mutableStateOf<String?>(null) }
    val haptics = rememberPhotonneHaptics()
    val dragSelectAdapter = rememberLazyGridDragSelectAdapter(
        gridState = gridState,
        headerCount = { headerCount },
        idAt = { ordinal -> items.getOrNull(ordinal)?.id }
    )
    val gridModifier = if (dragSelect != null) {
        modifier.fillMaxSize().dragSelectable(
            state = dragSelect.state,
            adapter = dragSelectAdapter,
            scrollableState = gridState,
            enabled = dragSelect.enabled,
            selectionActive = selectedIds.isNotEmpty(),
            isSelected = { it in selectedIds },
            onPatch = dragSelect.onPatch,
            haptics = haptics,
            // La franja de auto-scroll arranca donde acaba el cromo que se
            // superpone al contenido, que es exactamente el contentPadding.
            config = dragSelect.config.copy(
                autoScrollTopInset = contentPadding.calculateTopPadding(),
                autoScrollBottomInset = contentPadding.calculateBottomPadding()
            )
        )
    } else {
        modifier.fillMaxSize()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        state = gridState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        modifier = gridModifier
    ) {
        if (header != null) {
            item(
                key = "asset-grid-header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "asset-grid-header"
            ) {
                header()
            }
        }
        itemsIndexed(
            items,
            key = { index, item -> assetCellKey(item, index) },
            contentType = { _, _ -> AssetCellContentType }
        ) { index, asset ->
            AssetGridCell(
                // Punto 51: archivar, borrar o reordenar mueve las celdas en
                // vez de hacerlas saltar de sitio.
                modifier = Modifier.animateItem(),
                asset = asset,
                baseUrl = baseUrl,
                onClick = {
                    // Un clic con selección activa alterna la celda (lo hace el
                    // caller), así que también mueve el ancla del rango.
                    if (selectedIds.isNotEmpty()) rangeAnchorId = asset.id
                    onItemClick(index)
                },
                // Con arrastre en banda el long-press lo posee la rejilla.
                onLongClick = if (dragSelect != null) null
                else onItemLongClick?.let {
                    {
                        rangeAnchorId = asset.id
                        it(index)
                    }
                },
                // El clic derecho sigue viniendo de la celda: escritorio no
                // tiene long-press y es su única entrada a la selección.
                onSecondaryClick = onItemLongClick?.let {
                    {
                        rangeAnchorId = asset.id
                        it(index)
                    }
                },
                // Ctrl/Cmd+clic = mismo efecto que el long-press, sin esperar.
                onToggleClick = onItemLongClick?.let {
                    {
                        rangeAnchorId = asset.id
                        it(index)
                    }
                },
                onRangeClick = if (dragSelect == null || onItemLongClick == null) null else {
                    {
                        val anchorIndex = rangeAnchorId
                            ?.let { anchor -> items.indexOfFirst { it.id == anchor } }
                            ?: -1
                        if (anchorIndex >= 0) {
                            val ids = rangeSelectionIds(anchorIndex, index) { ordinal ->
                                items.getOrNull(ordinal)?.id
                            }
                            dragSelect.onPatch(SelectionPatch(select = ids))
                        } else {
                            // Sin ancla el Shift+clic degrada a toggle, y esta
                            // celda pasa a ser el ancla del siguiente rango.
                            rangeAnchorId = asset.id
                            onItemLongClick(index)
                        }
                    }
                },
                isSelected = asset.id in selectedIds
            )
        }
    }
}

/**
 * Configuración del arrastre en banda para [AssetGrid]. Se agrupa en un objeto
 * en vez de en seis parámetros sueltos porque o vienen todos o no viene
 * ninguno.
 */
@Immutable
data class AssetGridDragSelect(
    val state: DragSelectState,
    val onPatch: (SelectionPatch) -> Unit,
    val enabled: Boolean = true,
    val config: DragSelectConfig = DragSelectConfig.Default
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AssetGridCell(
    asset: TimelineItem,
    baseUrl: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /**
     * Clic derecho. Sigue a [onLongClick] salvo que se indique otra cosa: la
     * rejilla con arrastre en banda le quita el long-press a la celda pero le
     * deja el secundario, que en escritorio es la única entrada a selección.
     */
    onSecondaryClick: (() -> Unit)? = onLongClick,
    /**
     * Shift+clic (escritorio): selección de rango desde el ancla de la rejilla.
     * Cuando falta, el clic con Shift se comporta como un clic normal. En
     * táctil los modificadores nunca están pulsados, así que no cambia nada.
     */
    onRangeClick: (() -> Unit)? = null,
    /** Ctrl/Cmd+clic (escritorio): alterna la selección sin long-press. */
    onToggleClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    /**
     * When `true` (default) the cell forces a 1:1 square shape — the
     * legacy uniform-square grid behavior used by Trash, Favorites, etc.
     * Pass `false` from layouts that already set size externally (e.g.
     * the justified timeline grid where width/height come from the row's
     * height and the cell's weight).
     */
    forceSquare: Boolean = true,
    /**
     * When false the cell renders only its dominant-colour backdrop and
     * badges — no thumbnail request. The timeline flips this while the
     * scrubber is dragged so viewport teleports stay cheap.
     */
    loadThumbnail: Boolean = true,
    /**
     * Atenúa la celda. El timeline lo usa con selección activa sobre las
     * fotos solo-dispositivo, que no se pueden seleccionar: sin la
     * atenuación nada indicaba por qué no respondían.
     */
    dimmed: Boolean = false
) {
    val placeholder = remember(asset.dominantColor) { parseHexColor(asset.dominantColor) }
    val sharedScope = LocalSharedTransitionScope.current
    val currentDetailId = LocalCurrentDetailAssetId.current
    // Only register the cell as a shared-element source while the asset
    // viewer is open (or animating closed). Registering on every cell at
    // all times made LazyGrid scrolling churn the SharedTransitionScope's
    // internal bookkeeping on every recycle — visibly laggier scroll.
    val thumbnailSharedMod: Modifier = if (sharedScope != null && currentDetailId != null) {
        val sharedKey = remember(asset.id) { "asset-${asset.id}" }
        with(sharedScope) {
            Modifier.sharedElementWithCallerManagedVisibility(
                sharedContentState = rememberSharedContentState(key = sharedKey),
                visible = currentDetailId != asset.id,
                boundsTransform = { _, _ ->
                    androidx.compose.animation.core.tween(durationMillis = com.photonne.app.ui.theme.MotionDurations.OVERLAY_MS)
                }
            )
        }
    } else {
        Modifier
    }
    val selectionPadding by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        label = "selectionPadding"
    )
    val secondaryClick = onSecondaryClick
    // Los modificadores de teclado no llegan al combinedClickable, así que se
    // capturan en el Press (pase Initial, sin consumir nada — el ripple y el
    // arrastre en banda ni se enteran) y el onClick decide con ellos. Solo se
    // instala el observador si alguien escucha clics modificados.
    val wantsModifiedClicks = onRangeClick != null || onToggleClick != null
    // Hover de escritorio: revela el checkbox de selección sobre la celda. En
    // táctil el hover no existe y nada de esto llega a montarse.
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val pressModifiers = remember { PressModifiersHolder() }
    val clickAction: () -> Unit = if (!wantsModifiedClicks) onClick else {
        {
            val mods = pressModifiers.value
            when {
                mods != null && mods.isShiftPressed && onRangeClick != null -> onRangeClick()
                mods != null && (mods.isCtrlPressed || mods.isMetaPressed) &&
                    onToggleClick != null -> onToggleClick()
                else -> onClick()
            }
        }
    }
    // Una miniatura sin describir es, para un lector de pantalla, una rejilla
    // de nada. Y con selección activa lo que importa es el ESTADO: se anuncia
    // como seleccionable para que diga "seleccionado" al recorrerla.
    val videoLabel = stringResource(Res.string.asset_a11y_video)
    val livePhotoLabel = stringResource(Res.string.asset_a11y_live_photo)
    val favoriteLabel = stringResource(Res.string.asset_a11y_favorite)
    val cellDescription = remember(
        asset.fileName, asset.isVideo, asset.isLivePhoto, asset.isFavorite,
        videoLabel, livePhotoLabel, favoriteLabel
    ) {
        buildList {
            add(asset.fileName)
            if (asset.isVideo) add(videoLabel)
            if (asset.isLivePhoto) add(livePhotoLabel)
            if (asset.isFavorite) add(favoriteLabel)
        }.joinToString(", ")
    }
    Box(
        // OJO con el orden: el padding animado va DESPUÉS de fijar el tamaño,
        // así que encoge el contenido pero no el nodo. El hit-test del
        // arrastre en banda usa la caja del LazyGrid, y si alguien mueve ese
        // padding delante del aspectRatio, seleccionar una celda la encogería
        // bajo el dedo y la banda empezaría a fallar.
        modifier = modifier
            .let { base -> if (forceSquare) base.fillMaxWidth().aspectRatio(1f) else base }
            .graphicsLayer { alpha = if (dimmed) 0.35f else 1f }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.18f else 0f))
            .padding(selectionPadding)
            .background(placeholder ?: MaterialTheme.colorScheme.surfaceVariant)
            .let { base ->
                if (!wantsModifiedClicks) base
                else base.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                pressModifiers.value = event.keyboardModifiers
                            }
                        }
                    }
                }
            }
            .combinedClickable(onClick = clickAction, onLongClick = onLongClick)
            .hoverable(hoverInteraction)
            .pointerHoverIcon(PointerIcon.Hand)
            .let { base -> if (secondaryClick != null) base.onSecondaryClick(secondaryClick) else base }
            .semantics {
                contentDescription = cellDescription
                selected = isSelected
            }
    ) {
        AssetThumbnailImage(
            item = asset,
            baseUrl = baseUrl,
            size = "Small",
            modifier = Modifier.fillMaxSize().then(thumbnailSharedMod),
            enabled = loadThumbnail
        )
        asset.localSyncBadge?.let { badge ->
            // BottomStart so we don't collide with the video glyph
            // (TopEnd) or the favorite heart (BottomEnd).
            LocalSyncBadge(
                badge = badge,
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.xs)
            )
        }
        if (asset.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else if (asset.isLivePhoto) {
            // Same TopEnd slot as the video glyph (they're mutually exclusive):
            // a Live Photo badge mirroring the iOS Photos affordance.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MotionPhotosOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (asset.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = PhotonneColors.favorite,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.xs)
                    .size(IconSize.sm)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.xs)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        // Hover de ratón: scrim superior sutil + checkbox hueco para entrar en
        // selección con un clic, como en Google Photos web. El toggle reutiliza
        // el mismo camino que Ctrl+clic (o el clic derecho como reserva).
        val hoverToggle = onToggleClick ?: secondaryClick
        if (isHovered && !isSelected && hoverToggle != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    // Zona táctil de 32 dp; el aro visible sigue siendo de 20.
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = hoverToggle)
                    .padding(6.dp)
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
private fun LocalSyncBadge(badge: LocalSyncBadge, modifier: Modifier = Modifier) {
    // Pending: a translucent gray circle so the cloud icon reads as
    // "queued / not yet uploaded" without competing with favorite or
    // selection accents. Uploading / Failed keep their semantic
    // colours because they signal active work or an error.
    val (bg, icon) = when (badge) {
        LocalSyncBadge.Pending ->
            Color(0xFF424242).copy(alpha = 0.7f) to Icons.Filled.CloudUpload
        LocalSyncBadge.Uploading ->
            MaterialTheme.colorScheme.tertiary to Icons.Filled.CloudUpload
        LocalSyncBadge.Failed ->
            MaterialTheme.colorScheme.error to Icons.Filled.Refresh
    }
    val description = stringResource(
        when (badge) {
            LocalSyncBadge.Pending -> Res.string.sync_badge_pending
            LocalSyncBadge.Uploading -> Res.string.sync_badge_uploading
            LocalSyncBadge.Failed -> Res.string.sync_badge_failed
        }
    )
    Box(
        modifier = modifier
            .size(24.dp)
            .background(bg, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Últimos modificadores de teclado vistos en un Press. Var plano a propósito:
 * solo lo lee el onClick del mismo gesto, no debe recomponer nada.
 */
internal class PressModifiersHolder {
    var value: PointerKeyboardModifiers? = null
}

internal fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}
