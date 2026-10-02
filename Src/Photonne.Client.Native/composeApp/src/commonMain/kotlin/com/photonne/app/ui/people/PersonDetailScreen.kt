package com.photonne.app.ui.people

import com.photonne.app.resources.person_memories_together
import com.photonne.app.resources.person_memories_through_years
import com.photonne.app.ui.theme.Spacing
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_more
import com.photonne.app.resources.people_action_hide
import com.photonne.app.resources.people_detail_empty_subtitle
import com.photonne.app.resources.people_detail_empty_title
import com.photonne.app.resources.people_action_merge
import com.photonne.app.resources.people_action_rename
import com.photonne.app.resources.people_action_suggestions
import com.photonne.app.resources.people_action_unhide
import com.photonne.app.resources.people_unnamed
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.PhotoGridScrubberOverlay
import com.photonne.app.ui.grid.rememberAssetGridSelectionGestures
import com.photonne.app.ui.selection.SelectionPatch
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.AssetGridSkeleton
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneIcons

@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    title: String,
    isHidden: Boolean,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onSuggestions: () -> Unit,
    onMerge: () -> Unit,
    onToggleHidden: () -> Unit,
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = onRetry,
    onApplySelection: (SelectionPatch) -> Unit = {},
    /** Uno de sus recuerdos ("Martina a lo largo de los años", "Martina y Joan"). */
    onOpenMemory: (com.photonne.app.data.models.Memory) -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val hazeState = remember { HazeState() }
    val gridState = rememberLazyGridState()
    val gestures = rememberAssetGridSelectionGestures(onApplySelection)
    // Con una selección activa el cromo flotante cede el sitio a la cápsula de
    // selección, que ocupa el mismo hueco: la rejilla lo reserva siempre, así
    // que entrar o salir de la selección no la mueve (ni a mitad de arrastre).
    val chromeFloating = !state.isSelectionActive
    val reservedTop = subscreenChromeReservedTop()

    Box(modifier = Modifier.fillMaxSize()) {
        com.photonne.app.ui.theme.PhotonneRefreshableScreen(
            indicatorTopPadding = reservedTop,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh
        ) {
        when {
            state.isInitialLoading ->
                AssetGridSkeleton(
                    contentPadding = PaddingValues(
                        top = reservedTop,
                        bottom = floatingNavBarReservedHeight()
                    )
                )
            state.error != null && state.items.isEmpty() ->
                com.photonne.app.ui.error.FullScreenError(
                    error = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = reservedTop)
                )
            state.items.isEmpty() && state.personId != null ->
                EmptyState(
                    icon = PhotonneIcons.Person,
                    title = stringResource(Res.string.people_detail_empty_title),
                    subtitle = stringResource(Res.string.people_detail_empty_subtitle),
                    modifier = Modifier.padding(top = reservedTop)
                )
            else -> AssetGrid(
                items = state.items,
                baseUrl = apiBaseUrl,
                gridState = gridState,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                selectedIds = state.selection,
                hasMore = state.hasMore,
                isAppending = state.isAppending,
                isInitialLoading = state.isInitialLoading,
                onLoadMore = onLoadMore,
                dragSelect = gestures.dragSelect,
                contentPadding = PaddingValues(
                    top = reservedTop,
                    bottom = floatingNavBarReservedHeight()
                ),
                // Sus recuerdos encima de sus fotos (ver PersonMemoriesHeader).
                header = if (state.memories.isNotEmpty()) {
                    {
                        PersonMemoriesHeader(
                            memories = state.memories,
                            baseUrl = apiBaseUrl,
                            openingId = state.openingMemoryId,
                            onOpen = onOpenMemory
                        )
                    }
                } else null,
                modifier = Modifier.fillMaxWidth().hazeSource(hazeState)
            )
        }
        }

        PhotoGridScrubberOverlay(
            gridState = gridState,
            items = state.items,
            headerCount = if (state.memories.isNotEmpty()) 1 else 0,
            reservedTop = reservedTop,
            reservedBottom = floatingNavBarReservedHeight(),
            selectionActive = state.isSelectionActive,
            hazeState = hazeState,
        )

        if (chromeFloating) {
            SubscreenFloatingChrome(
                title = title.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.people_unnamed),
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
                statusBarScrim = true,
                actions = {
                    PersonDetailOverflowMenu(
                        isHidden = isHidden,
                        onRename = onRename,
                        onSuggestions = onSuggestions,
                        onMerge = onMerge,
                        onToggleHidden = onToggleHidden
                    )
                }
            )
        }
    }
}

@Composable
private fun PersonDetailOverflowMenu(
    isHidden: Boolean,
    onRename: () -> Unit,
    onSuggestions: () -> Unit,
    onMerge: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(PhotonneIcons.More, contentDescription = stringResource(Res.string.action_more))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_rename)) },
                leadingIcon = { Icon(PhotonneIcons.Rename, contentDescription = null) },
                onClick = { menuOpen = false; onRename() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_suggestions)) },
                leadingIcon = { Icon(PhotonneIcons.FaceSuggestions, contentDescription = null) },
                onClick = { menuOpen = false; onSuggestions() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_action_merge)) },
                leadingIcon = { Icon(PhotonneIcons.Merge, contentDescription = null) },
                onClick = { menuOpen = false; onMerge() }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (isHidden) stringResource(Res.string.people_action_unhide)
                        else stringResource(Res.string.people_action_hide)
                    )
                },
                leadingIcon = {
                    Icon(
                        if (isHidden) PhotonneIcons.Show else PhotonneIcons.Hide,
                        contentDescription = null
                    )
                },
                onClick = { menuOpen = false; onToggleHidden() }
            )
        }
    }
}

private const val SCROLL_TO_TOP_MIN_CELL = 12
private const val SCROLL_TO_TOP_SNAP_CELL = 48

/** Tarjeta de pareja: cuadrada, con la otra persona y cuántas fotos tienen juntas. */
private val PairCardSize = 120.dp

/**
 * Lo de esta persona encima de sus fotos: "A lo largo de los años" como tarjeta
 * destacada (solo hay una por persona) y "Personas con más fotos juntas" como
 * fila, cada tarjeta con la OTRA persona ("Joan · 42 fotos"), no con la de la
 * ficha. Con margen debajo para que no se pegue a la rejilla.
 */
@Composable
private fun PersonMemoriesHeader(
    memories: List<com.photonne.app.data.models.Memory>,
    baseUrl: String,
    openingId: String?,
    onOpen: (com.photonne.app.data.models.Memory) -> Unit,
) {
    val throughYears = memories.firstOrNull {
        com.photonne.app.data.models.MemoryKind.from(it.kind) ==
            com.photonne.app.data.models.MemoryKind.PersonThroughYears
    }
    val pairs = memories.filter {
        com.photonne.app.data.models.MemoryKind.from(it.kind) ==
            com.photonne.app.data.models.MemoryKind.PeopleTogether
    }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        if (throughYears != null) {
            PersonHeaderTitle(stringResource(Res.string.person_memories_through_years))
            com.photonne.app.ui.memories.BigMemoryCard(
                memory = throughYears,
                // La ficha ya dice de quién es: basta el periodo como subtítulo.
                title = stringResource(Res.string.person_memories_through_years),
                baseUrl = baseUrl,
                isOpening = openingId == throughYears.id,
                onClick = { onOpen(throughYears) },
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
            )
        }
        if (pairs.isNotEmpty()) {
            PersonHeaderTitle(stringResource(Res.string.person_memories_together))
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(pairs, key = { "pair:${it.id}" }) { pair ->
                    com.photonne.app.ui.memories.MemoryCardFace(
                        coverUrl = pair.coverAssetId?.let { "$baseUrl/api/assets/$it/thumbnail?size=Medium" },
                        contentDescription = pair.title,
                        title = pair.companionName ?: pair.title,
                        subtitle = pair.subtitle,
                        compact = true,
                        modifier = Modifier
                            .size(PairCardSize)
                            .clickable(enabled = openingId != pair.id) { onOpen(pair) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonHeaderTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm)
            .semantics { heading() }
    )
}
