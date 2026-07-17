package com.photonne.app.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_objects_empty
import com.photonne.app.resources.explore_section_objects
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExploreObjectsScreen(
    viewModel: ExploreFacetsViewModel,
    onObjectClick: (String) -> Unit,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val baseUrl = rememberApiBaseUrl()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val tiles = state.objects.map {
        ExploreLabelTile(name = it.label, assetCount = it.assetCount, coverAssetId = it.coverAssetId)
    }
    ExploreLabelGridScreen(
        tiles = tiles,
        isLoading = state.isLoading,
        errorMessage = state.error?.userMessage,
        emptyText = stringResource(Res.string.explore_objects_empty),
        baseUrl = baseUrl,
        title = stringResource(Res.string.explore_section_objects),
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onTileClick = onObjectClick,
        onChromeVisibleChange = onChromeVisibleChange
    )
}
