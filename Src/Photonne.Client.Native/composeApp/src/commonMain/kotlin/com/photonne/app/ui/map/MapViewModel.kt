package com.photonne.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.album.AlbumsRepository
import com.photonne.app.data.asset.AssetDetailRepository
import com.photonne.app.data.error.UiError
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.data.map.MapRepository
import com.photonne.app.data.models.MapPoint
import com.photonne.app.data.models.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // Cluster bottom-sheet state. `sheetPoints` is null when the sheet
    // is closed; the view-model owns it so selection survives a
    // configuration change and so the bulk actions can drop affected
    // assets out of the point list when they succeed.
    val sheetPoints: List<MapPoint>? = null,
    val selection: Set<String> = emptySet(),
    val isBulkMutating: Boolean = false
) {
    val isSelectionActive: Boolean get() = selection.isNotEmpty()
}

class MapViewModel(
    private val repository: MapRepository,
    private val assetRepository: AssetDetailRepository,
    private val albumsRepository: AlbumsRepository,
    private val errorFactory: UiErrorFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.firstLoadComplete || _state.value.isLoading) return
        refresh()
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
                    _state.update {
                        it.copy(
                            isLoading = false,
                            firstLoadComplete = true,
                            error = errorFactory.from(error, "Failed to load map")
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

    fun fitToData() {
        val points = _state.value.points
        if (points.isEmpty()) return
        val (lat, lng, zoom) = pickInitialView(points)
        _state.update { it.copy(centerLat = lat, centerLng = lng, zoom = zoom) }
    }

    fun openClusterSheet(points: List<MapPoint>) {
        _state.update {
            it.copy(
                sheetPoints = points.sortedByDescending { p -> p.date },
                selection = emptySet()
            )
        }
    }

    fun closeClusterSheet() {
        _state.update { it.copy(sheetPoints = null, selection = emptySet()) }
    }

    fun toggleSelection(assetId: String) {
        _state.update {
            val next = it.selection.toMutableSet()
            if (!next.add(assetId)) next.remove(assetId)
            it.copy(selection = next)
        }
    }

    fun selectAllInSheet() {
        _state.update {
            val ids = it.sheetPoints?.map { p -> p.id }?.toSet() ?: return@update it
            if (it.selection == ids) it.copy(selection = emptySet())
            else it.copy(selection = ids)
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
        errorFallback = "Failed to archive"
    )

    fun bulkTrash() = runBulk(
        action = { assetRepository.trash(it) },
        errorFallback = "Failed to delete"
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
                    val sheet = _state.value.sheetPoints.orEmpty()
                    val asTimeline = sheet
                        .filter { it.id in ids }
                        .map { it.toSyntheticItem() }
                    _state.update { it.copy(isBulkMutating = false, selection = emptySet()) }
                    onSuccess(asTimeline)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isBulkMutating = false,
                            error = errorFactory.from(error, "Failed to add to album")
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
                        val newSheet = previous.sheetPoints?.filterNot { p -> p.id in idSet }
                        previous.copy(
                            isBulkMutating = false,
                            selection = emptySet(),
                            points = newPoints,
                            sheetPoints = newSheet?.takeIf { it.isNotEmpty() }
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
        // first open; "Home" FAB (fitToData) still pans+zooms to the
        // full extent when the user wants the global view.
        val anchor = points.maxByOrNull { it.date } ?: points.first()
        return Triple(anchor.latitude, anchor.longitude, 12)
    }
}

private fun MapPoint.toSyntheticItem(): TimelineItem = TimelineItem(
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
