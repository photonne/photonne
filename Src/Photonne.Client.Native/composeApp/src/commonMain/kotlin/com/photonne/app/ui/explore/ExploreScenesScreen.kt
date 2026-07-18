package com.photonne.app.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_scenes_empty
import com.photonne.app.resources.explore_section_scenes
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExploreScenesScreen(
    viewModel: ExploreFacetsViewModel,
    onSceneClick: (String) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val baseUrl = rememberApiBaseUrl()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val tiles = state.scenes.map {
        ExploreLabelTile(name = it.label, assetCount = it.assetCount, coverAssetId = it.coverAssetId)
    }
    ExploreLabelGridScreen(
        tiles = tiles,
        isLoading = state.isLoading,
        error = state.error,
        emptyText = stringResource(Res.string.explore_scenes_empty),
        baseUrl = baseUrl,
        title = stringResource(Res.string.explore_section_scenes),
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onTileClick = onSceneClick,
        onChromeVisibleChange = onChromeVisibleChange
    )
}
