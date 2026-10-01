package com.photonne.app.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.MapPoint
import com.photonne.app.resources.Res
import com.photonne.app.resources.asset_trash_title
import com.photonne.app.resources.map_cluster_sheet_title
import com.photonne.app.resources.map_sheet_empty_subtitle
import com.photonne.app.resources.map_sheet_empty_title
import com.photonne.app.resources.map_sheet_focus_chip
import com.photonne.app.resources.map_sheet_focus_clear
import com.photonne.app.resources.map_sheet_viewport_title
import com.photonne.app.resources.selection_action_add_to_album
import com.photonne.app.resources.selection_action_archive
import com.photonne.app.resources.selection_action_close
import com.photonne.app.resources.selection_action_deselect_all
import com.photonne.app.resources.selection_action_select_all
import com.photonne.app.resources.selection_action_trash
import com.photonne.app.resources.selection_archive_confirm_message
import com.photonne.app.resources.selection_archive_confirm_title
import com.photonne.app.resources.selection_archive_done
import com.photonne.app.resources.selection_count
import com.photonne.app.resources.selection_deleted_permanently_done
import com.photonne.app.resources.selection_trash_confirm_message
import com.photonne.app.resources.selection_trash_done
import com.photonne.app.resources.trash_disabled_delete_confirm
import com.photonne.app.resources.trash_disabled_delete_message
import com.photonne.app.resources.trash_disabled_delete_title
import com.photonne.app.ui.grid.AssetGridCell
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.main.chromeCapsuleBackdrop
import com.photonne.app.ui.main.chromeSolidColor
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.ChromeElevation
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.Spacing
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hoja persistente del mapa (patrón de Apple/Google Fotos): no modal, vive
 * sobre el mapa con tres anclajes ([MapSheetValue]) y enseña las fotos del
 * viewport o, con un clúster tocado, solo las de ese clúster. Sustituye a la
 * antigua `MapClusterSheet` modal y conserva su cuerpo: la celda común de las
 * rejillas, la cabecera de selección y las mismas confirmaciones.
 *
 * El arrastre vive SOLO en la hoja (asa, cabecera y rejilla vía nested
 * scroll), así que nunca compite con el paneo del mapa. La hoja ocupa desde su
 * borde superior hasta el fondo de la pantalla; la nav flotante va por encima
 * y la rejilla reserva su alto con [bottomPadding].
 *
 * @param fallbackTopPx dónde pintar la hoja mientras los anclajes aún no existen.
 * @param onPeekMeasured alto de asa + título: lo que asoma en reposo.
 */
@Composable
internal fun MapPhotoSheet(
    sheetState: AnchoredDraggableState<MapSheetValue>,
    gridState: LazyGridState,
    fallbackTopPx: Float,
    points: List<MapPoint>,
    isFocused: Boolean,
    isReady: Boolean,
    baseUrl: String,
    selectedIds: Set<String>,
    isMutating: Boolean,
    hazeState: HazeState,
    /** Fondo liso en vez de cristal (mientras el mapa se mueve). */
    solidBackground: Boolean,
    bottomPadding: Dp,
    onPeekMeasured: (Int) -> Unit,
    onPeekClick: () -> Unit,
    onClearFocus: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onExitSelection: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelectionActive = selectedIds.isNotEmpty()
    val items = remember(points) { points.map { it.toSyntheticItem() } }

    MapSheetConfirmations(
        selectedCount = selectedIds.size,
        isMutating = isMutating,
        onArchive = onArchive,
        onTrash = onTrash
    ) { askArchive, askTrash ->
        val density = LocalDensity.current
        val flingThresholdPx = with(density) { SheetFlingThreshold.toPx() }
        val nestedScroll = remember(sheetState, flingThresholdPx) {
            sheetNestedScrollConnection(sheetState, flingThresholdPx)
        }
        // La rueda del ratón (y cualquier scroll sin fling) puede dejar la hoja
        // entre dos anclajes: al terminar, que encaje en el más cercano.
        LaunchedEffect(sheetState, gridState) {
            snapshotFlow { gridState.isScrollInProgress }.collect { scrolling ->
                if (!scrolling) sheetState.settleToClosest()
            }
        }
        // Otro viewport u otro clúster: la rejilla vuelve arriba (lo más reciente).
        LaunchedEffect(points.firstOrNull()?.id, isFocused) {
            if (gridState.firstVisibleItemIndex > 0) gridState.scrollToItem(0)
        }

        val shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp)
        )
        Surface(
            modifier = modifier
                .fillMaxSize()
                // La hoja va del anclaje actual al fondo: se mide en la fase de
                // layout leyendo el offset, así que arrastrar no recompone.
                .layout { measurable, constraints ->
                    val top = sheetState.offset.takeUnless { it.isNaN() } ?: fallbackTopPx
                    val topPx = top.roundToInt().coerceIn(0, constraints.maxHeight)
                    val height = constraints.maxHeight - topPx
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = height, maxHeight = height)
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(0, topPx)
                    }
                }
                .nestedScroll(nestedScroll)
                .anchoredDraggable(sheetState, Orientation.Vertical),
            shape = shape,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = ChromeElevation.bar
        ) {
            Box {
                // El mismo cristal que el cromo flotante: difumina el mapa de detrás.
                Box(
                    Modifier.matchParentSize().then(
                        if (solidBackground) Modifier.background(chromeSolidColor())
                        else Modifier.chromeCapsuleBackdrop(hazeState = hazeState)
                    )
                )
                Column(Modifier.fillMaxSize()) {
                    // Lo que asoma en reposo: asa + título (o la cabecera de
                    // selección). Tocarlo en reposo abre la hoja a media altura.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { onPeekMeasured(it.height) }
                            .clickable(
                                enabled = sheetState.settledValue == MapSheetValue.Peek,
                                onClick = onPeekClick
                            )
                    ) {
                        SheetDragHandle()
                        if (isSelectionActive) {
                            SelectionHeader(
                                selectedCount = selectedIds.size,
                                totalCount = points.size,
                                isMutating = isMutating,
                                onExit = onExitSelection,
                                onSelectAll = onSelectAll,
                                onAddToAlbum = onAddToAlbum,
                                onArchive = askArchive,
                                onTrash = askTrash
                            )
                        } else {
                            SheetHeader(
                                title = pluralStringResource(
                                    if (isFocused) Res.plurals.map_cluster_sheet_title
                                    else Res.plurals.map_sheet_viewport_title,
                                    points.size,
                                    points.size
                                ),
                                modifier = Modifier
                                    .padding(horizontal = Spacing.lg)
                                    .padding(bottom = Spacing.md)
                            )
                        }
                    }
                    if (isFocused && !isSelectionActive) {
                        FocusChip(onClearFocus = onClearFocus)
                    }
                    val gridPadding = PaddingValues(
                        start = Spacing.sm,
                        end = Spacing.sm,
                        top = Spacing.xs,
                        bottom = bottomPadding + Spacing.sm
                    )
                    when {
                        !isReady -> AssetGridSkeleton(
                            modifier = Modifier.weight(1f),
                            contentPadding = gridPadding
                        )
                        items.isEmpty() -> EmptyState(
                            icon = PhotonneIcons.Location,
                            title = stringResource(Res.string.map_sheet_empty_title),
                            subtitle = stringResource(Res.string.map_sheet_empty_subtitle),
                            modifier = Modifier.weight(1f).padding(bottom = bottomPadding)
                        )
                        // La misma celda que el resto de rejillas (AssetGridCell):
                        // check de selección, halo y encogido animado, clic derecho
                        // y Ctrl/Cmd+clic en escritorio.
                        else -> LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 110.dp),
                            contentPadding = gridPadding,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                                AssetGridCell(
                                    modifier = Modifier.animateItem(),
                                    asset = item,
                                    baseUrl = baseUrl,
                                    onClick = {
                                        if (isSelectionActive) onToggleSelection(item.id)
                                        else onPhotoClick(index)
                                    },
                                    onLongClick = { onToggleSelection(item.id) },
                                    onToggleClick = { onToggleSelection(item.id) },
                                    isSelected = item.id in selectedIds
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Velocidad a partir de la cual un lanzamiento salta al anclaje siguiente. */
private val SheetFlingThreshold = 125.dp

/**
 * Enlaza la rejilla con la hoja, como `BottomSheetScaffold`: arrastrar hacia
 * arriba primero sube la hoja y solo después desplaza la rejilla; hacia abajo,
 * la rejilla vuelve a su principio y entonces baja la hoja.
 */
private fun sheetNestedScrollConnection(
    state: AnchoredDraggableState<MapSheetValue>,
    flingThresholdPx: Float
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0 && source == NestedScrollSource.UserInput && !state.offset.isNaN()) {
            Offset(0f, state.dispatchRawDelta(delta))
        } else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && !state.offset.isNaN()) {
            Offset(0f, state.dispatchRawDelta(available.y))
        } else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        val offset = state.offset
        if (offset.isNaN()) return Velocity.Zero
        // Con la hoja a medio camino el lanzamiento es suyo; desplegada, de la rejilla.
        return if (offset > state.anchors.minPosition() + 0.5f) {
            state.settleWithVelocity(available.y, flingThresholdPx)
            available
        } else Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (state.offset.isNaN()) return Velocity.Zero
        state.settleWithVelocity(available.y, flingThresholdPx)
        return available
    }
}

/** Encaja la hoja según la velocidad: rápido hacia un lado → anclaje siguiente
 *  en esa dirección; lento → el más cercano. */
private suspend fun AnchoredDraggableState<MapSheetValue>.settleWithVelocity(
    velocity: Float,
    thresholdPx: Float
) {
    val offset = offset.takeUnless { it.isNaN() } ?: return
    val target = when {
        // Hacia arriba el offset decrece: el anclaje por debajo de la posición.
        velocity < -thresholdPx -> anchors.closestAnchor(offset, searchUpwards = false)
        velocity > thresholdPx -> anchors.closestAnchor(offset, searchUpwards = true)
        else -> anchors.closestAnchor(offset)
    } ?: return
    animateTo(target)
}

private suspend fun AnchoredDraggableState<MapSheetValue>.settleToClosest() {
    val offset = offset.takeUnless { it.isNaN() } ?: return
    if (isAnimationRunning) return
    val target = anchors.closestAnchor(offset) ?: return
    if (abs(anchors.positionOf(target) - offset) > 0.5f) animateTo(target)
}

/** Asa de la hoja: la misma medida que la de las hojas de Material. */
@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md, bottom = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(50)
                )
        )
    }
}

/** "Zona seleccionada · ✕": la hoja filtra por el clúster tocado; quitarlo
 *  vuelve a todo el viewport. */
@Composable
private fun FocusChip(onClearFocus: () -> Unit) {
    Row(modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.xs)) {
        InputChip(
            selected = true,
            onClick = onClearFocus,
            label = { Text(stringResource(Res.string.map_sheet_focus_chip)) },
            trailingIcon = {
                Icon(
                    PhotonneIcons.Close,
                    contentDescription = stringResource(Res.string.map_sheet_focus_clear),
                    modifier = Modifier.size(InputChipDefaults.IconSize)
                )
            },
            leadingIcon = {
                Icon(
                    PhotonneIcons.Location,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.chip)
                )
            }
        )
    }
}

/**
 * Confirmaciones de archivar y papelera en bloque. El mapa tiene su propio
 * camino de selección (no pasa por AssetSelectionBottomBar), así que confirma
 * aquí igual que el resto de pantallas; archivar también, porque aquí no hay
 * barra con Deshacer. [content] recibe las dos acciones que abren los diálogos.
 */
@Composable
private fun MapSheetConfirmations(
    selectedCount: Int,
    isMutating: Boolean,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    content: @Composable (askArchive: () -> Unit, askTrash: () -> Unit) -> Unit
) {
    var showTrashConfirm by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbarController.current
    val trashDoneMessage = pluralStringResource(Res.plurals.selection_trash_done, selectedCount, selectedCount)
    val archiveDoneMessage = pluralStringResource(Res.plurals.selection_archive_done, selectedCount, selectedCount)
    // Papelera apagada en el servidor ⇒ el borrado es definitivo: dilo.
    val trashEnabled = com.photonne.app.ui.actions.rememberServerTrashEnabled()
    val deletedDoneMessage = pluralStringResource(
        Res.plurals.selection_deleted_permanently_done,
        selectedCount,
        selectedCount
    )
    if (showTrashConfirm) {
        ConfirmActionDialog(
            title = stringResource(
                if (trashEnabled) Res.string.asset_trash_title
                else Res.string.trash_disabled_delete_title
            ),
            message = pluralStringResource(
                if (trashEnabled) Res.plurals.selection_trash_confirm_message
                else Res.plurals.trash_disabled_delete_message,
                selectedCount,
                selectedCount
            ),
            confirmLabel = stringResource(
                if (trashEnabled) Res.string.selection_action_trash
                else Res.string.trash_disabled_delete_confirm
            ),
            isDestructive = true,
            isSubmitting = isMutating,
            onDismiss = { showTrashConfirm = false },
            onConfirm = {
                showTrashConfirm = false
                onTrash()
                snackbar?.show(if (trashEnabled) trashDoneMessage else deletedDoneMessage)
            }
        )
    }
    if (showArchiveConfirm) {
        ConfirmActionDialog(
            title = stringResource(Res.string.selection_archive_confirm_title),
            message = pluralStringResource(
                Res.plurals.selection_archive_confirm_message,
                selectedCount,
                selectedCount
            ),
            confirmLabel = stringResource(Res.string.selection_action_archive),
            isDestructive = false,
            isSubmitting = isMutating,
            onDismiss = { showArchiveConfirm = false },
            onConfirm = {
                showArchiveConfirm = false
                onArchive()
                snackbar?.show(archiveDoneMessage)
            }
        )
    }
    content({ showArchiveConfirm = true }, { showTrashConfirm = true })
}

/**
 * Cabecera de selección dentro de la hoja. No puede ser la barra flotante de
 * [com.photonne.app.ui.main.AssetSelectionTopBar] (la hoja tapa la pantalla),
 * pero copia su forma: cerrar, recuento en `titleMedium` y el mismo botón de
 * seleccionar/deseleccionar todo; las acciones van a continuación.
 */
@Composable
private fun SelectionHeader(
    selectedCount: Int,
    totalCount: Int,
    isMutating: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToAlbum: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit
) {
    val allSelected = totalCount > 0 && selectedCount >= totalCount
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onExit, enabled = !isMutating) {
            Icon(
                PhotonneIcons.Close,
                contentDescription = stringResource(Res.string.selection_action_close)
            )
        }
        Text(
            text = pluralStringResource(Res.plurals.selection_count, selectedCount, selectedCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = Spacing.xs)
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll, enabled = !isMutating) {
            Icon(
                PhotonneIcons.SelectAll,
                contentDescription = stringResource(
                    if (allSelected) Res.string.selection_action_deselect_all
                    else Res.string.selection_action_select_all
                ),
                tint = if (allSelected) MaterialTheme.colorScheme.primary
                else LocalContentColor.current
            )
        }
        IconButton(onClick = onAddToAlbum, enabled = !isMutating) {
            Icon(
                PhotonneIcons.AddToAlbum,
                contentDescription = stringResource(Res.string.selection_action_add_to_album)
            )
        }
        IconButton(onClick = onArchive, enabled = !isMutating) {
            Icon(
                PhotonneIcons.Archive,
                contentDescription = stringResource(Res.string.selection_action_archive)
            )
        }
        IconButton(onClick = onTrash, enabled = !isMutating) {
            Icon(
                PhotonneIcons.Delete,
                contentDescription = stringResource(Res.string.selection_action_trash),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
