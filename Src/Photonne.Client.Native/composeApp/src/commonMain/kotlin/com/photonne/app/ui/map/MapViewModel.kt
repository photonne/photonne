package com.photonne.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.map.MapRepository
import com.photonne.app.data.models.MapPoint
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
    val errorMessage: String? = null,
    val firstLoadComplete: Boolean = false
)

class MapViewModel(
    private val repository: MapRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    fun ensureLoaded() {
        if (_state.value.firstLoadComplete || _state.value.isLoading) return
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
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
                            errorMessage = error.message ?: "Failed to load map"
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

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * Pick a starting viewport that fits the user's full set of GPS
     * points: average the centroid and pick the smallest zoom whose
     * tile size still covers the latitudinal span.
     */
    private fun pickInitialView(points: List<MapPoint>): Triple<Double, Double, Int> {
        if (points.isEmpty()) return Triple(20.0, 0.0, 2)
        val avgLat = points.sumOf { it.latitude } / points.size
        val avgLng = points.sumOf { it.longitude } / points.size
        val latSpan = (points.maxOf { it.latitude } - points.minOf { it.latitude })
            .coerceAtLeast(0.01)
        val zoom = when {
            latSpan > 60 -> 2
            latSpan > 20 -> 3
            latSpan > 5 -> 5
            latSpan > 1 -> 7
            latSpan > 0.1 -> 10
            else -> 12
        }
        return Triple(avgLat, avgLng, zoom)
    }
}
