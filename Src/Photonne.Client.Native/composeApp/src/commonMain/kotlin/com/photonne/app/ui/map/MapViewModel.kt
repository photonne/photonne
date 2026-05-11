package com.photonne.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photonne.app.data.map.MapRepository
import com.photonne.app.data.models.MapCluster
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val centerLat: Double = 20.0,
    val centerLng: Double = 0.0,
    val zoom: Int = 2,
    val clusters: List<MapCluster> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val firstLoadComplete: Boolean = false
)

class MapViewModel(
    private val repository: MapRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var pendingFetch: Job? = null

    fun ensureLoaded() {
        if (_state.value.firstLoadComplete || _state.value.isLoading) return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { repository.clusters(zoom = _state.value.zoom) }
                .onSuccess { clusters ->
                    val (centerLat, centerLng, zoom) = pickInitialView(clusters)
                    _state.update {
                        it.copy(
                            clusters = clusters,
                            centerLat = centerLat,
                            centerLng = centerLng,
                            zoom = zoom,
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

    /**
     * Called by the map composable each time the viewport settles (zoom
     * change, drag end). Debounced so a quick pinch-and-pan doesn't issue
     * a request per frame.
     */
    fun onViewportChanged(bounds: MapBounds) {
        pendingFetch?.cancel()
        pendingFetch = viewModelScope.launch {
            delay(VIEWPORT_DEBOUNCE_MS)
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                repository.clusters(
                    zoom = bounds.zoom,
                    minLat = bounds.minLat,
                    minLng = bounds.minLng,
                    maxLat = bounds.maxLat,
                    maxLng = bounds.maxLng
                )
            }
                .onSuccess { clusters ->
                    _state.update { it.copy(clusters = clusters, isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load map"
                        )
                    }
                }
        }
    }

    fun fitToData() {
        val clusters = _state.value.clusters
        if (clusters.isEmpty()) return
        val (lat, lng, zoom) = pickInitialView(clusters)
        _state.update {
            it.copy(centerLat = lat, centerLng = lng, zoom = zoom)
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    /**
     * Pick a reasonable starting viewport from the unbounded first-load
     * response: average the cluster centers and pick a zoom level wide
     * enough to fit them all without going below world view.
     */
    private fun pickInitialView(clusters: List<MapCluster>): Triple<Double, Double, Int> {
        if (clusters.isEmpty()) return Triple(20.0, 0.0, 2)
        val avgLat = clusters.sumOf { it.latitude } / clusters.size
        val avgLng = clusters.sumOf { it.longitude } / clusters.size
        val latSpan = (clusters.maxOf { it.latitude } - clusters.minOf { it.latitude })
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

    companion object {
        private const val VIEWPORT_DEBOUNCE_MS = 350L
    }
}
