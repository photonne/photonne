package com.photonne.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.ErrorMessages
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.map.MapRepository
import com.photonne.app.data.models.MapPoint
import com.photonne.app.data.models.TimelineItem
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.selection.applying
import com.photonne.app.ui.selection.toggled
import com.photonne.app.ui.selection.toggledAll
import com.photonne.app.ui.selection.withSelection
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val centerLat: Double = 20.0,
    val centerLng: Double = 0.0,
    val zoom: Int = 2,
    val points: List<MapPoint> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiError? = null,
    val firstLoadComplete: Boolean = false,
    /**
     * Clave de API de las teselas, leída del ajuste del servidor
     * (ServerSettings.MapTileApiKey). Null mientras no se ha cargado o si el
     * administrador no ha puesto ninguna.
     */
    val tileApiKey: String? = null,
    /** Tamaño medido del mapa: el viewport que filtra la hoja persistente. */
    val viewportWidthPx: Int = 0,
    val viewportHeightPx: Int = 0,
    /**
     * Fotos del viewport, de la más reciente a la más antigua. Se recalcula
     * [VIEWPORT_DEBOUNCE_MS] después de que la cámara se pare (no en cada frame
     * del arrastre) y se congela mientras hay selección, para que la rejilla no
     * cambie bajo el dedo.
     */
    val viewportPoints: List<MapPoint> = emptyList(),
    /** False hasta el primer cálculo del viewport: evita enseñar "no hay fotos"
     *  antes de haber mirado. */
    val viewportReady: Boolean = false,
    /** Clúster tocado: la hoja enseña solo sus fotos hasta que se quita el filtro. */
    val focusedPoints: List<MapPoint>? = null,
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()

    /** Lo que enseña la hoja: el clúster tocado o, sin él, el viewport. */
    val sheetPoints: List<MapPoint> get() = focusedPoints ?: viewportPoints
}

/** Lo que decide el contenido del viewport; la hoja se recalcula cuando cambia. */
private data class ViewportKey(
    val centerLat: Double,
    val centerLng: Double,
    val zoom: Int,
    val widthPx: Int,
    val heightPx: Int,
    val points: List<MapPoint>,
    val frozen: Boolean
)

class MapViewModel(
    private val repository: MapRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val settingsRepository: com.photonne.app.data.admin.AdminRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    init {
        observeViewport()
    }

    /**
     * La hoja persistente sigue al mapa: cada vez que el centro, el zoom, el
     * tamaño o los puntos cambian se recalcula qué fotos caen dentro, con
     * debounce para no filtrar en cada frame del arrastre. Todo en cliente: los
     * puntos ya están cargados enteros, no hace falta pedir nada por bbox. El
     * primer cálculo va sin espera para que la hoja no pase por "vacío".
     */
    @OptIn(FlowPreview::class)
    private fun observeViewport() {
        viewModelScope.launch {
            _state
                .map {
                    ViewportKey(
                        centerLat = it.centerLat,
                        centerLng = it.centerLng,
                        zoom = it.zoom,
                        widthPx = it.viewportWidthPx,
                        heightPx = it.viewportHeightPx,
                        points = it.points,
                        frozen = it.isSelectionActive || it.isBulkMutating
                    )
                }
                .distinctUntilChanged()
                .debounce { if (_state.value.viewportReady) VIEWPORT_DEBOUNCE_MS else 0L }
                .collect { key ->
                    if (key.frozen || !_state.value.firstLoadComplete) return@collect
                    if (key.widthPx <= 0 || key.heightPx <= 0) return@collect
                    val visible = pointsInViewport(
                        points = key.points,
                        centerLat = key.centerLat,
                        centerLng = key.centerLng,
                        zoom = key.zoom,
                        widthPx = key.widthPx,
                        heightPx = key.heightPx
                    )
                    _state.update { it.copy(viewportPoints = visible, viewportReady = true) }
                }
        }
    }

    fun onViewportSizeChanged(widthPx: Int, heightPx: Int) {
        _state.update { it.copy(viewportWidthPx = widthPx, viewportHeightPx = heightPx) }
    }

    fun ensureLoaded() {
        loadTileApiKey()
        if (_state.value.firstLoadComplete || _state.value.isLoading) return
        refresh()
    }

    /**
     * La clave de teselas vive en el servidor para que cada instalación use la
     * suya. Se lee una sola vez por sesión del ViewModel y su fallo es mudo: sin
     * clave el mapa sigue pintándose (con la marca de agua de CARTO), así que no
     * merece un banner de error encima del mapa.
     */
    private fun loadTileApiKey() {
        if (_state.value.tileApiKey != null) return
        viewModelScope.launch {
            runCatching { settingsRepository.getSettingString(MAP_TILE_API_KEY_SETTING) }
                .onSuccess { key ->
                    _state.update { it.copy(tileApiKey = key.orEmpty()) }
                }
        }
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.points() }
                .onSuccess { points ->
                    val (lat, lng, zoom) = pickInitialView(points)
                    _state.update {
                        it.copy(
                            points = points,
                            centerLat = if (it.firstLoadComplete) it.centerLat else lat,
                            centerLng = if (it.firstLoadComplete) it.centerLng else lng,
                            zoom = if (it.firstLoadComplete) it.zoom else zoom,
                            isLoading = false,
                            firstLoadComplete = true
                        )
                    }
                }
                .onFailure { error ->
                    // firstLoadComplete se queda como esté: marcarlo en el
                    // fallo hacía que "no hay fotos con ubicación" saliera
                    // junto al aviso de error.
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = errorFactory.from(error, "No se pudo cargar el mapa")
                        )
                    }
                }
        }
    }

    fun onCenterChanged(lat: Double, lng: Double) {
        _state.update { it.copy(centerLat = lat, centerLng = lng) }
    }

    fun onZoomChanged(zoom: Int) {
        _state.update { it.copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) }
    }

    fun zoomIn() = onZoomChanged(_state.value.zoom + 1)
    fun zoomOut() = onZoomChanged(_state.value.zoom - 1)

    /**
     * "Encajar" de verdad: centra y elige el mayor zoom en el que el bbox de
     * TODOS los puntos cabe en el viewport (antes saltaba a la foto más
     * reciente a zoom 12, igual que la primera apertura). [viewportWidthPx] y
     * [viewportHeightPx] los pasa la pantalla; sin ellos cae al plano mundial.
     */
    fun fitToData(viewportWidthPx: Int = 0, viewportHeightPx: Int = 0) {
        val points = _state.value.points
        if (points.isEmpty()) return
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }
        val centerLat = (minLat + maxLat) / 2.0
        val centerLng = (minLng + maxLng) / 2.0
        val zoom = if (viewportWidthPx > 0 && viewportHeightPx > 0) {
            // El mayor zoom cuyo bbox (con 10 % de aire por lado) cabe.
            (MAX_ZOOM downTo MIN_ZOOM).firstOrNull { z ->
                val spanX = kotlin.math.abs(lonToWorldX(maxLng, z) - lonToWorldX(minLng, z))
                val spanY = kotlin.math.abs(latToWorldY(minLat, z) - latToWorldY(maxLat, z))
                spanX <= viewportWidthPx * 0.8 && spanY <= viewportHeightPx * 0.8
            } ?: MIN_ZOOM
        } else 2
        _state.update {
            it.copy(
                centerLat = centerLat,
                centerLng = centerLng,
                // Un único punto (o un cluster muy prieto) cabría a MAX_ZOOM;
                // 16 deja contexto de calles alrededor.
                zoom = zoom.coerceAtMost(16)
            )
        }
    }

    /** Clúster tocado: la hoja pasa a enseñar solo sus fotos. */
    fun focusCluster(points: List<MapPoint>) {
        _state.update {
            it.copy(focusedPoints = points.newestFirst(), selection = emptySet())
        }
    }

    /** Quita el filtro de clúster: la hoja vuelve a todo el viewport. */
    fun clearFocus() {
        _state.update { it.copy(focusedPoints = null, selection = emptySet()) }
    }

    fun toggleSelection(assetId: String) {
        _state.update { it.copy(selection = it.selection.toggled(assetId)) }
    }

    /** Un frame de arrastre en banda, en una sola mutación — ver [SelectionPatch]. */
    fun applySelection(patch: SelectionPatch) {
        if (patch.isEmpty) return
        _state.update { it.copy(selection = it.selection.applying(patch)) }
    }

    /** Marca o desmarca [ids] en bloque, sin alternar (carril de filas). */
    fun setSelected(ids: Collection<String>, selected: Boolean) {
        _state.update { it.copy(selection = it.selection.withSelection(ids, selected)) }
    }

    fun selectAllInSheet() {
        _state.update {
            val ids = it.sheetPoints.map { p -> p.id }
            if (ids.isEmpty()) return@update it
            it.copy(selection = it.selection.toggledAll(ids))
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet()) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun bulkArchive() = runBulk(
        action = { assetRepository.archive(it) },
        errorFallback = ErrorMessages.ARCHIVE_FAILED
    )

    fun bulkTrash() = runBulk(
        action = { assetRepository.trash(it) },
        errorFallback = ErrorMessages.TRASH_FAILED
    )

    /**
     * Add the selected cluster photos to [albumId]. The caller is
     * expected to refresh the album list afterwards. We do not drop the
     * affected assets from the map — they're still on disk and still
     * GPS-tagged.
     */
    fun bulkAddToAlbum(albumId: String, onSuccess: (List<TimelineItem>) -> Unit = {}) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { albumsRepository.addAssetsBatch(albumId, ids) }
                .onSuccess {
                    val asTimeline = _state.value.points
                        .filter { it.id in ids }
                        .map { it.toSyntheticItem() }
                    _state.update { it.copy(isBulkMutating = false, selection = emptySet()) }
                    onSuccess(asTimeline)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "No se pudo añadir al álbum")
                        )
                    }
                }
        }
    }

    private fun runBulk(
        action: suspend (List<String>) -> Unit,
        errorFallback: String
    ) {
        val ids = _state.value.selection.toList()
        if (ids.isEmpty() || _state.value.isBulkMutating) return
        _state.update { it.copy(isBulkMutating = true, error = null) }
        viewModelScope.launch {
            runCatching { action(ids) }
                .onSuccess { _ ->
                    val idSet = ids.toHashSet()
                    _state.update { previous ->
                        val newPoints = previous.points.filterNot { p -> p.id in idSet }
                        val newFocus = previous.focusedPoints?.filterNot { p -> p.id in idSet }
                        previous.copy(
                            isBulkMutating = false,
                            selection = emptySet(),
                            points = newPoints,
                            viewportPoints = previous.viewportPoints.filterNot { p -> p.id in idSet },
                            // Clúster vaciado: vuelve a todo el viewport.
                            focusedPoints = newFocus?.takeIf { it.isNotEmpty() }
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, errorFallback)
                        )
                    }
                }
        }
    }

    private fun pickInitialView(points: List<MapPoint>): Triple<Double, Double, Int> {
        if (points.isEmpty()) return Triple(20.0, 0.0, 2)
        // Anchor on the most recent photo's location — better proxy
        // for "where the user is right now" than the global centroid,
        // which for someone with intercontinental travel history can
        // land in the middle of an ocean with everything visually
        // tiny. Zoom 12 (city + suburbs) gives a useful viewport on
        // first open; the "fit" control (fitToData) still pans+zooms to the
        // full extent when the user wants the global view.
        val anchor = points.maxByOrNull { it.date } ?: points.first()
        return Triple(anchor.latitude, anchor.longitude, 12)
    }
}

/** Espera tras parar la cámara antes de refiltrar la hoja. */
private const val VIEWPORT_DEBOUNCE_MS = 300L

/** Misma clave que edita Ajustes del servidor (AdminServerSettingsViewModel). */
private const val MAP_TILE_API_KEY_SETTING = "ServerSettings.MapTileApiKey"

/** TimelineItem mínimo para una foto del mapa: sirve a la celda común de la
 *  rejilla ([com.photonne.app.ui.grid.AssetGridCell]) y a "Añadir a álbum". */
internal fun MapPoint.toSyntheticItem(): TimelineItem = TimelineItem(
    id = id,
    fileName = "",
    fullPath = "",
    fileSize = 0L,
    fileCreatedAt = date,
    fileModifiedAt = date,
    extension = "",
    scannedAt = date,
    type = "Image",
    hasThumbnails = hasThumbnail
)
