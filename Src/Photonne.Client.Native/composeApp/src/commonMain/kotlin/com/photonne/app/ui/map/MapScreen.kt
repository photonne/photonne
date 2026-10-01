package com.photonne.app.ui.map

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.map_sheet_show_pill
import com.photonne.app.ui.navigation.PlatformBackHandler
import com.photonne.app.ui.theme.IconSize
import com.photonne.app.ui.theme.MotionDurations
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.photonne.app.data.models.MapPoint
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.map_action_zoom_out
import com.photonne.app.ui.main.ChromePill
import com.photonne.app.ui.main.chromeCapsuleBackdrop
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_refresh
import com.photonne.app.resources.map_action_fit_to_data
import com.photonne.app.resources.map_attribution
import com.photonne.app.resources.map_action_zoom_in
import com.photonne.app.resources.map_empty_subtitle
import com.photonne.app.resources.map_empty_title
import com.photonne.app.resources.map_title
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.theme.ChromeElevation
import com.photonne.app.ui.theme.Spacing

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    /** Marcador suelto: los puntos visibles en el viewport, del más reciente
     *  al más antiguo, y el índice del tocado (Lote N2: antes el visor se
     *  abría con una sola foto y no se podía deslizar, a diferencia del clúster). */
    onPointOpen: (List<MapPoint>, Int) -> Unit,
    /** Miniatura de la hoja: su lista entera (viewport o clúster) es el feed
     *  del visor, empezando en la tocada. */
    onSheetPhotoOpen: (List<MapPoint>, Int) -> Unit,
    onBulkAddToAlbum: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val apiBaseUrl = rememberApiBaseUrl()
    val darkTiles = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Los tiles de OsmMap se pintan con Coil (AsyncImage) → contenido Compose que
    // Haze SÍ puede difuminar. Los toasts son hermanos del mapa, así que lo leen.
    val mapHazeState = remember { HazeState() }
    // Las cápsulas de arriba flotan permanentemente (aquí no hay scroll que las
    // acople ni las esconda), así que todo lo que se alinee arriba tiene que
    // arrancar por debajo de ellas.
    val reservedTop = subscreenChromeReservedTop()
    val navReserved = floatingNavBarReservedHeight()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.ensureLoaded() }
    // Al salir del mapa ni la hoja ni el filtro de clúster (ni su selección)
    // sobreviven: la próxima visita arranca solo con el mapa.
    DisposableEffect(viewModel) { onDispose { viewModel.onLeave() } }

    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }
    // Mientras el mapa se mueve la hoja deja el cristal por un fondo liso: el
    // blur de casi media pantalla, recalculado en cada frame del arrastre,
    // encarecía tanto el frame que el mapa temblaba.
    var mapMoving by remember { mutableStateOf(false) }

    // ── Hoja persistente ────────────────────────────────────────────────
    // Anclajes: oculta del todo (una píldora sobre la nav la recupera), media
    // altura y desplegada bajo el cromo de arriba. Hasta el primer clúster
    // tocado no hay hoja ni píldora; ese toque la sube a media altura, y a
    // partir de ahí la píldora queda a mano el resto de la visita.
    var savedSheetValue by rememberSaveable { mutableStateOf(MapSheetValue.Hidden.name) }
    val sheetState = remember {
        // Un estado guardado por una versión anterior puede nombrar "Peek".
        val initial = MapSheetValue.entries.firstOrNull { it.name == savedSheetValue }
            ?: MapSheetValue.Hidden
        AnchoredDraggableState(initial)
    }
    val sheetGridState = rememberLazyGridState()
    val navReservedPx = with(density) { navReserved.toPx() }
    val reservedTopPx = with(density) { reservedTop.toPx() }
    val expandedTopPx = with(density) { (reservedTop + Spacing.sm).toPx() }
    val mapHeightPx = mapSizePx.height.toFloat()
    val anchorPositions = remember(mapHeightPx, expandedTopPx) {
        if (mapHeightPx <= 0f) emptyMap()
        else mapSheetAnchorPositions(mapHeightPx, expandedTopPx)
    }
    LaunchedEffect(anchorPositions) {
        if (anchorPositions.isEmpty()) return@LaunchedEffect
        val anchors = DraggableAnchors { anchorPositions.forEach { (value, position) -> value at position } }
        val target = sheetState.targetValue.takeIf { it in anchorPositions } ?: MapSheetValue.Hidden
        sheetState.updateAnchors(anchors, target)
    }
    /** Posición abierta más baja: media altura, o desplegada si no cabe. */
    val openSheetValue =
        if (MapSheetValue.Half in anchorPositions) MapSheetValue.Half else MapSheetValue.Expanded
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.settledValue }.collect { savedSheetValue = it.name }
    }
    // Sin fotos con ubicación no hay hoja: el aviso de mapa vacío lo dice todo.
    // Ni hoja ni píldora hasta el primer clúster tocado en la visita.
    val sheetShown = state.sheetEnabled && state.firstLoadComplete &&
        state.points.isNotEmpty() && anchorPositions.isNotEmpty()
    val hiddenTopPx = anchorPositions[MapSheetValue.Hidden] ?: mapHeightPx
    // Donde descansan los controles y la atribución con la hoja oculta: sobre
    // la nav y, si la hoja ya existe en esta visita, también sobre la píldora.
    val restTopPx = mapHeightPx - navReservedPx -
        (if (sheetShown) with(density) { HiddenPillReserve.toPx() } else 0f)
    // Borde superior de la hoja (se lee en layout/dibujo, no en composición).
    val sheetTop: () -> Float = {
        if (!sheetShown) hiddenTopPx
        else sheetState.offset.takeUnless { it.isNaN() } ?: hiddenTopPx
    }
    // Lo que siguen los controles y la atribución: el borde de la hoja, pero
    // nunca por debajo de su sitio de descanso.
    val controlsTop: () -> Float = { minOf(sheetTop(), restTopPx) }
    // Los controles se desvanecen al subir la hoja hacia media altura.
    val fadeEndPx = anchorPositions[openSheetValue] ?: restTopPx
    val controlsAlpha: () -> Float = {
        if (!sheetShown || restTopPx <= fadeEndPx) 1f
        else ((controlsTop() - fadeEndPx) / (restTopPx - fadeEndPx)).coerceIn(0f, 1f)
    }
    val controlsVisible by remember(sheetShown, restTopPx, fadeEndPx) {
        derivedStateOf { controlsAlpha() > 0.05f }
    }

    fun animateSheetTo(value: MapSheetValue) {
        if (value !in anchorPositions) return
        scope.launch { sheetState.animateTo(value) }
    }

    // Tocar un clúster: la hoja pasa a sus fotos, sube a media altura y el mapa
    // se desliza para dejar el clúster en el hueco visible sobre ella.
    var centerJob by remember { mutableStateOf<Job?>(null) }
    val onClusterClick: (List<MapPoint>) -> Unit = { cluster ->
        viewModel.focusCluster(cluster)
        animateSheetTo(openSheetValue)
        val current = viewModel.state.value
        val target = centerToRevealCluster(
            points = cluster,
            zoom = current.zoom,
            mapHeightPx = mapSizePx.height,
            visibleTopPx = reservedTopPx,
            visibleBottomPx = anchorPositions[MapSheetValue.Half] ?: mapHeightPx
        )
        if (target != null) {
            val (fromLat, fromLng) = current.centerLat to current.centerLng
            val (toLat, toLng) = target
            centerJob?.cancel()
            centerJob = scope.launch {
                Animatable(0f).animateTo(1f, tween(MotionDurations.CHROME_MS)) {
                    viewModel.onCenterChanged(
                        fromLat + (toLat - fromLat) * value,
                        fromLng + (toLng - fromLng) * value
                    )
                }
            }
        }
    }

    // Atrás: selección → desplegada → media → oculta → salir del mapa.
    val settledSheet = sheetState.settledValue
    PlatformBackHandler(
        enabled = sheetShown && (state.isSelectionActive || settledSheet != MapSheetValue.Hidden)
    ) {
        when {
            state.isSelectionActive -> viewModel.clearSelection()
            settledSheet == MapSheetValue.Expanded && MapSheetValue.Half in anchorPositions ->
                animateSheetTo(MapSheetValue.Half)
            else -> animateSheetTo(MapSheetValue.Hidden)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged {
            mapSizePx = it
            viewModel.onViewportSizeChanged(it.width, it.height)
        }
    ) {
        OsmMap(
            centerLat = state.centerLat,
            centerLng = state.centerLng,
            zoom = state.zoom,
            points = state.points,
            baseUrl = apiBaseUrl,
            darkTiles = darkTiles,
            onCenterChanged = { lat, lng ->
                // El dedo manda: corta el deslizamiento hacia un clúster.
                centerJob?.cancel()
                viewModel.onCenterChanged(lat, lng)
            },
            onZoomChanged = viewModel::onZoomChanged,
            onClusterClick = onClusterClick,
            onPointClick = { tapped ->
                val visible = pointsInViewport(
                    points = state.points,
                    centerLat = state.centerLat,
                    centerLng = state.centerLng,
                    zoom = state.zoom,
                    widthPx = mapSizePx.width,
                    heightPx = mapSizePx.height
                )
                val ordered = if (visible.any { it.id == tapped.id }) visible
                else (visible + tapped).newestFirst()
                onPointOpen(ordered, ordered.indexOfFirst { it.id == tapped.id }.coerceAtLeast(0))
            },
            tileApiKey = state.tileApiKey,
            onMovingChanged = { mapMoving = it },
            modifier = Modifier.fillMaxSize().hazeSource(mapHazeState)
        )

        when {
            state.isLoading ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = reservedTop + Spacing.sm),
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent
                ) {
                  Box {
                    Box(Modifier.matchParentSize().chromeCapsuleBackdrop(hazeState = mapHazeState))
                    Box(modifier = Modifier.padding(Spacing.md), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(Spacing.xxs)
                        )
                    }
                  }
                }
            state.firstLoadComplete && state.points.isEmpty() && state.error == null ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(Spacing.xl),
                    shape = MaterialTheme.shapes.large,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                  Box {
                    Box(Modifier.matchParentSize().chromeCapsuleBackdrop(hazeState = mapHazeState))
                    Column(
                        modifier = Modifier.padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Text(
                            stringResource(Res.string.map_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(Res.string.map_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                  }
                }
        }

        // Condición de uso de las teselas de OSM y CARTO: la atribución debe
        // estar visible sobre el propio mapa, así que va justo encima de la hoja.
        var attributionHeightPx by remember { mutableIntStateOf(0) }
        MapAttribution(
            text = stringResource(Res.string.map_attribution),
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { attributionHeightPx = it.height }
                .offset {
                    IntOffset(
                        Spacing.lg.roundToPx(),
                        (controlsTop() - attributionHeightPx - Spacing.sm.toPx()).roundToInt()
                    )
                }
        )

        // Controles del mapa: una cápsula vertical de cristal, la misma pieza que
        // el resto del cromo flotante (antes eran tres FAB opacos). El mapa es
        // hazeSource, así que el cristal difumina las teselas de detrás. Sigue
        // al borde de la hoja asomada y se desvanece al subirla.
        if (controlsVisible) {
            var controlsHeightPx by remember { mutableIntStateOf(0) }
            ChromePill(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .onSizeChanged { controlsHeightPx = it.height }
                    .offset {
                        IntOffset(
                            -Spacing.lg.roundToPx(),
                            (controlsTop() - controlsHeightPx - Spacing.lg.toPx()).roundToInt()
                        )
                    }
                    .graphicsLayer { alpha = controlsAlpha() },
                hazeState = mapHazeState,
                elevation = ChromeElevation.bar
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { viewModel.zoomIn() }) {
                        Icon(
                            PhotonneIcons.ZoomIn,
                            contentDescription = stringResource(Res.string.map_action_zoom_in)
                        )
                    }
                    IconButton(onClick = { viewModel.zoomOut() }) {
                        Icon(
                            PhotonneIcons.ZoomOut,
                            contentDescription = stringResource(Res.string.map_action_zoom_out)
                        )
                    }
                    IconButton(onClick = { viewModel.fitToData(mapSizePx.width, mapSizePx.height) }) {
                        Icon(
                            PhotonneIcons.FitToBounds,
                            contentDescription = stringResource(Res.string.map_action_fit_to_data)
                        )
                    }
                }
            }
        }

        // Oculta del todo y sin moverse, la hoja no se compone: su rejilla no
        // pinta nada fuera de la pantalla.
        if (sheetShown && !(settledSheet == MapSheetValue.Hidden && sheetState.targetValue == MapSheetValue.Hidden)) {
            val sheetPoints = state.sheetPoints
            MapPhotoSheet(
                sheetState = sheetState,
                gridState = sheetGridState,
                fallbackTopPx = hiddenTopPx,
                minVisibleTopPx = anchorPositions[openSheetValue] ?: expandedTopPx,
                points = sheetPoints,
                isFocused = state.focusedPoints != null,
                isReady = state.viewportReady || state.focusedPoints != null,
                baseUrl = apiBaseUrl,
                selectedIds = state.selection,
                isMutating = state.isBulkMutating,
                hazeState = mapHazeState,
                solidBackground = mapMoving,
                bottomPadding = navReserved,
                onClearFocus = viewModel::clearFocus,
                onPhotoClick = { index -> onSheetPhotoOpen(sheetPoints, index) },
                onToggleSelection = viewModel::toggleSelection,
                onSelectAll = viewModel::selectAllInSheet,
                onExitSelection = viewModel::clearSelection,
                onAddToAlbum = onBulkAddToAlbum,
                onArchive = viewModel::bulkArchive,
                onTrash = viewModel::bulkTrash
            )
        }

        // Hoja oculta: una píldora sobre la nav la devuelve a media altura.
        if (sheetShown && settledSheet == MapSheetValue.Hidden) {
            ChromePill(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navReserved + Spacing.sm),
                hazeState = mapHazeState,
                elevation = ChromeElevation.pill,
                onClick = { animateSheetTo(openSheetValue) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(
                        PhotonneIcons.ChevronUp,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.chip)
                    )
                    Text(
                        stringResource(Res.string.map_sheet_show_pill, state.sheetPoints.size),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // El banner de error estándar de la app (el mismo que en el resto de
        // pantallas, con "Ver detalles" y "Reintentar"), flotando sobre el mapa.
        if (state.error?.userMessage != null) {
            ErrorBanner(
                error = state.error,
                onRetry = viewModel::refresh,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = Spacing.lg)
                    .padding(top = reservedTop + Spacing.sm)
                    .clip(MaterialTheme.shapes.small)
            )
        }

        // `scroll = null`: el mapa no scrollea, así que el cromo nunca se acopla
        // ni se esconde, y el título viaja dentro de la cápsula de volver (no hay
        // estado acoplado donde enseñarlo).
        SubscreenFloatingChrome(
            title = stringResource(Res.string.map_title),
            onBack = onBack,
            scroll = null,
            hazeState = mapHazeState,
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(
                        PhotonneIcons.Refresh,
                        contentDescription = stringResource(Res.string.action_refresh)
                    )
                }
            }
        )
    }
}

/** Hueco que deja la píldora "Fotos · N" (alto + margen) para los controles. */
private val HiddenPillReserve = 52.dp
