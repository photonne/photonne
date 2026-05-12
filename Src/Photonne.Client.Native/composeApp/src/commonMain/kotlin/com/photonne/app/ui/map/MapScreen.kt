package com.photonne.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.MapPoint
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.map_action_fit_to_data
import com.photonne.app.resources.map_action_zoom_in
import com.photonne.app.resources.map_action_zoom_out
import com.photonne.app.resources.map_empty_subtitle
import com.photonne.app.resources.map_empty_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onPointOpen: (MapPoint) -> Unit,
    onClusterPhotoOpen: (List<MapPoint>, Int) -> Unit,
    onBulkAddToAlbum: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val apiBaseUrl = rememberApiBaseUrl()
    val darkTiles = MaterialTheme.colorScheme.background.luminance() < 0.5f

    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    Box(modifier = Modifier.fillMaxSize()) {
        OsmMap(
            centerLat = state.centerLat,
            centerLng = state.centerLng,
            zoom = state.zoom,
            points = state.points,
            baseUrl = apiBaseUrl,
            darkTiles = darkTiles,
            onCenterChanged = viewModel::onCenterChanged,
            onZoomChanged = viewModel::onZoomChanged,
            onClusterClick = viewModel::openClusterSheet,
            onPointClick = onPointOpen,
            modifier = Modifier.fillMaxSize()
        )

        when {
            !state.firstLoadComplete && state.isLoading ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Box(modifier = Modifier.padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }
            state.firstLoadComplete && state.points.isEmpty() ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = { viewModel.fitToData() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = stringResource(Res.string.map_action_fit_to_data)
                )
            }
            FloatingActionButton(
                onClick = { viewModel.zoomIn() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.map_action_zoom_in)
                )
            }
            FloatingActionButton(
                onClick = { viewModel.zoomOut() },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "−",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    val sheetPoints = state.sheetPoints
    if (sheetPoints != null) {
        MapClusterSheet(
            points = sheetPoints,
            baseUrl = apiBaseUrl,
            selectedIds = state.selection,
            isMutating = state.isBulkMutating,
            onDismiss = viewModel::closeClusterSheet,
            onPhotoClick = { index -> onClusterPhotoOpen(sheetPoints, index) },
            onToggleSelection = viewModel::toggleSelection,
            onSelectAll = viewModel::selectAllInSheet,
            onExitSelection = viewModel::clearSelection,
            onAddToAlbum = onBulkAddToAlbum,
            onArchive = viewModel::bulkArchive,
            onTrash = viewModel::bulkTrash
        )
    }
}
