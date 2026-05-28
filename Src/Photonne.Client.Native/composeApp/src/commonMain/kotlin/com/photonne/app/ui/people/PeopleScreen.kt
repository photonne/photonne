package com.photonne.app.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.Person
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.people_empty_subtitle
import com.photonne.app.resources.people_empty_title
import com.photonne.app.resources.people_unnamed
import com.photonne.app.ui.theme.EmptyState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

@Composable
fun PeopleScreen(
    state: PeopleUiState,
    onPersonClick: (Person) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit
) {
    val apiBaseUrl = rememberApiBaseUrl()

    LaunchedEffect(Unit) { onRefresh() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isInitialLoading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            state.error != null && state.people.isEmpty() ->
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    com.photonne.app.ui.error.ErrorBanner(error = state.error)
                }
            state.isEmpty ->
                EmptyState(
                    icon = Icons.Outlined.People,
                    title = stringResource(Res.string.people_empty_title),
                    subtitle = stringResource(Res.string.people_empty_subtitle)
                )
            else -> {
                val gridState = rememberLazyGridState()
                val shouldLoadMore by remember(
                    state.hasMore,
                    state.isAppending,
                    state.isInitialLoading
                ) {
                    derivedStateOf {
                        val total = gridState.layoutInfo.totalItemsCount
                        val lastVisible = gridState.layoutInfo
                            .visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 6 &&
                            state.hasMore && !state.isAppending && !state.isInitialLoading
                    }
                }
                LaunchedEffect(gridState) {
                    snapshotFlow { shouldLoadMore }
                        .distinctUntilChanged()
                        .filter { it }
                        .collect { onLoadMore() }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 88.dp),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.people, key = { it.id }) { person ->
                        PersonCard(
                            person = person,
                            baseUrl = apiBaseUrl,
                            onClick = { onPersonClick(person) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: Person,
    baseUrl: String,
    onClick: () -> Unit
) {
    val displayName = person.name?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.people_unnamed)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            person.coverFaceId?.let { faceId ->
                AsyncImage(
                    model = "$baseUrl/api/faces/$faceId/thumbnail",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = displayName,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (person.faceCount > 0) {
            Text(
                text = "${person.faceCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
