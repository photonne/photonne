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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.People
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.photonne.app.data.models.Person
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_more
import com.photonne.app.resources.people_action_hide_hidden
import com.photonne.app.resources.people_action_recluster
import com.photonne.app.resources.people_action_show_hidden
import com.photonne.app.resources.people_empty_subtitle
import com.photonne.app.resources.people_empty_title
import com.photonne.app.resources.people_title
import com.photonne.app.resources.people_unnamed
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

@Composable
fun PeopleScreen(
    state: PeopleUiState,
    onPersonClick: (Person) -> Unit,
    onLoadMore: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onRecluster: () -> Unit,
    onToggleHidden: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    // Fuente de blur del cromo: la rejilla que scrollea por detrás, de la que
    // las cápsulas son HERMANAS — la regla de Haze.
    val hazeState = remember { HazeState() }
    // Hoisted out of the `else` branch: the chrome follows this scroll, and the
    // empty / loading / error branches need the same bar (and its back button).
    val gridState = rememberLazyGridState()
    val reservedTop = subscreenChromeReservedTop()

    LaunchedEffect(Unit) { onLoad() }

    PhotonneRefreshableScreen(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    ) {
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
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            // Reserva el cromo flotante: la rejilla pasa por
                            // debajo al scrollear, pero en reposo la primera
                            // fila no queda escondida detrás de la barra.
                            top = 12.dp + reservedTop,
                            end = 12.dp,
                            bottom = 12.dp + floatingNavBarReservedHeight()
                        ),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState)
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

            SubscreenFloatingChrome(
                title = stringResource(Res.string.people_title),
                onBack = onBack,
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                    firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { gridState.isScrollInProgress },
                    scrollToTopMinIndex = SCROLL_TO_TOP_MIN_CELL,
                    onScrollToTop = {
                        if (gridState.firstVisibleItemIndex > SCROLL_TO_TOP_SNAP_CELL) {
                            gridState.scrollToItem(SCROLL_TO_TOP_SNAP_CELL)
                        }
                        gridState.animateScrollToItem(0)
                    }
                ),
                hazeState = hazeState,
                onChromeVisibleChange = onChromeVisibleChange,
                actions = { PeopleOverflowMenu(state.showHidden, onRecluster, onToggleHidden) }
            )
        }
    }
}

/** Recluster / show-hidden, moved off the Scaffold's docked bar into the
 * floating chrome's trailing capsule. */
@Composable
private fun PeopleOverflowMenu(
    showHidden: Boolean,
    onRecluster: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(Res.string.action_more)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_recluster)) },
                onClick = { menuOpen = false; onRecluster() }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (showHidden) stringResource(Res.string.people_action_hide_hidden)
                        else stringResource(Res.string.people_action_show_hidden)
                    )
                },
                onClick = { menuOpen = false; onToggleHidden() }
            )
        }
    }
}

/** Cells scrolled past before the back-to-top pill appears (~3 rows of faces). */
private const val SCROLL_TO_TOP_MIN_CELL = 12

/** Where the tap teleports to before animating the rest of the way up. */
private const val SCROLL_TO_TOP_SNAP_CELL = 48

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
