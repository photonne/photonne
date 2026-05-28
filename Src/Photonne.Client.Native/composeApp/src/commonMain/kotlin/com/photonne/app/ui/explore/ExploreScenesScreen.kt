package com.photonne.app.ui.explore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.resources.Res
import com.photonne.app.resources.explore_scenes_empty
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExploreScenesScreen(
    viewModel: ExploreFacetsViewModel,
    onSceneClick: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val tiles = state.scenes.map { ExploreLabelTile(name = it.label, assetCount = it.assetCount) }
    ExploreLabelGridScreen(
        tiles = tiles,
        isLoading = state.isLoading,
        errorMessage = state.error?.userMessage,
        emptyText = stringResource(Res.string.explore_scenes_empty),
        onTileClick = onSceneClick
    )
}
