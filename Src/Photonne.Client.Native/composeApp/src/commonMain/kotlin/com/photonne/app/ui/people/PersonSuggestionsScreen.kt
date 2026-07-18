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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_more
import com.photonne.app.resources.people_action_suggestions_accept_all
import com.photonne.app.resources.people_action_suggestions_dismiss_all
import com.photonne.app.resources.people_suggestions_accept
import com.photonne.app.resources.people_suggestions_dismiss
import com.photonne.app.resources.people_suggestions_empty
import com.photonne.app.resources.people_suggestions_title
import com.photonne.app.resources.people_unnamed
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

@Composable
fun PersonSuggestionsScreen(
    state: PersonSuggestionsUiState,
    title: String,
    isBulkMutating: Boolean,
    onAccept: (faceId: String) -> Unit,
    onDismissFace: (faceId: String) -> Unit,
    onLoadMore: () -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit,
    onAcceptAll: () -> Unit,
    onDismissAll: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val reservedTop = subscreenChromeReservedTop()

    LaunchedEffect(Unit) { onOpen() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isInitialLoading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null && state.items.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(top = reservedTop).padding(24.dp)) {
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
                        top = 12.dp + reservedTop,
                        end = 12.dp,
                        bottom = 12.dp + floatingNavBarReservedHeight()
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().hazeSource(hazeState)
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

        SubscreenFloatingChrome(
            title = stringResource(
                Res.string.people_suggestions_title,
                title.takeIf { it.isNotBlank() } ?: stringResource(Res.string.people_unnamed)
            ),
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                isScrollInProgress = { gridState.isScrollInProgress },
                scrollToTopMinIndex = 6,
                onScrollToTop = {
                    if (gridState.firstVisibleItemIndex > 24) gridState.scrollToItem(24)
                    gridState.animateScrollToItem(0)
                }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange,
            actions = {
                SuggestionsOverflowMenu(
                    enabled = !isBulkMutating,
                    onAcceptAll = onAcceptAll,
                    onDismissAll = onDismissAll
                )
            }
        )
    }
}

@Composable
private fun SuggestionsOverflowMenu(
    enabled: Boolean,
    onAcceptAll: () -> Unit,
    onDismissAll: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }, enabled = enabled) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.action_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_suggestions_accept_all)) },
                onClick = { menuOpen = false; onAcceptAll() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_suggestions_dismiss_all)) },
                onClick = { menuOpen = false; onDismissAll() }
            )
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
