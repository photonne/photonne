package com.photonne.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.PersonSuggestion
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.resources.Res
import com.photonne.app.resources.people_suggestions_accept
import com.photonne.app.resources.people_suggestions_dismiss
import com.photonne.app.resources.people_suggestions_empty
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

@Composable
fun PersonSuggestionsScreen(
    state: PersonSuggestionsUiState,
    onAccept: (faceId: String) -> Unit,
    onDismissFace: (faceId: String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: () -> Unit
) {
    val apiBaseUrl = rememberApiBaseUrl()

    LaunchedEffect(Unit) { onOpen() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isInitialLoading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null && state.items.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    com.photonne.app.ui.error.ErrorBanner(error = state.error)
                }
            state.isEmpty ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(Res.string.people_suggestions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            else -> {
                val gridState = rememberLazyGridState()
                val shouldLoadMore by remember(state.hasMore, state.isAppending) {
                    derivedStateOf {
                        val total = gridState.layoutInfo.totalItemsCount
                        val lastVisible = gridState.layoutInfo
                            .visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 6 &&
                            state.hasMore && !state.isAppending
                    }
                }
                LaunchedEffect(gridState) {
                    snapshotFlow { shouldLoadMore }
                        .distinctUntilChanged()
                        .filter { it }
                        .collect { onLoadMore() }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 12.dp,
                        end = 12.dp,
                        bottom = 12.dp + floatingNavBarReservedHeight()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { it.id }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            baseUrl = apiBaseUrl,
                            isPending = suggestion.id in state.pendingFaceIds,
                            onAccept = { onAccept(suggestion.id) },
                            onDismiss = { onDismissFace(suggestion.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: PersonSuggestion,
    baseUrl: String,
    isPending: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = "$baseUrl/api/faces/${suggestion.id}/thumbnail",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isPending) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onDismiss, enabled = !isPending) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.people_suggestions_dismiss),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onAccept, enabled = !isPending) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(Res.string.people_suggestions_accept),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
