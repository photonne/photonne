package com.photonne.app.ui.map

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.MapPoint
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.action_retry
import com.photonne.app.resources.map_action_zoom_out
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
import com.photonne.app.ui.theme.Spacing

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onPointOpen: (MapPoint) -> Unit,
    onClusterPhotoOpen: (List<MapPoint>, Int) -> Unit,
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

    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    var mapSizePx by remember {
        androidx.compose.runtime.mutableStateOf(androidx.compose.ui.unit.IntSize.Zero)
    }
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { mapSizePx = it }) {
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
            tileApiKey = state.tileApiKey,
            modifier = Modifier.fillMaxSize().hazeSource(mapHazeState)
        )

        when {
            state.isLoading ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = reservedTop + Spacing.sm),
                    shape = RoundedCornerShape(16.dp),
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
                    shape = RoundedCornerShape(16.dp),
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

        state.error?.userMessage?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = Spacing.lg)
                    .padding(top = reservedTop + Spacing.sm),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        message,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(vertical = Spacing.sm),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = viewModel::refresh) {
                        Text(
                            stringResource(Res.string.action_retry),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Condición de uso de las teselas de OSM y CARTO: la atribución debe
        // estar visible sobre el propio mapa.
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = floatingNavBarReservedHeight() + 16.dp),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ) {
            Text(
                stringResource(Res.string.map_attribution),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = Spacing.xxs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg)
                // La rejilla del mapa dibuja a sangre bajo la nav flotante; sube los
                // controles de zoom por encima de la cápsula.
                .padding(bottom = floatingNavBarReservedHeight()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = { viewModel.fitToData(mapSizePx.width, mapSizePx.height) },
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
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = stringResource(Res.string.map_action_zoom_out)
                )
            }
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
                        Icons.Outlined.Refresh,
                        contentDescription = stringResource(Res.string.action_refresh)
                    )
                }
            }
        )
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
