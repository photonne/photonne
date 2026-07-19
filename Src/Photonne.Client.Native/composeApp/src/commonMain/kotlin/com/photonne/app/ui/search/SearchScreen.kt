package com.photonne.app.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.SolidColor
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.action_close
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.photonne.app.resources.Res
import com.photonne.app.resources.search_clear_all
import com.photonne.app.resources.search_empty_results
import com.photonne.app.resources.search_filters
import com.photonne.app.resources.search_idle_subtitle
import com.photonne.app.resources.search_idle_title
import com.photonne.app.resources.search_input_hint
import com.photonne.app.resources.search_mode_semantic
import com.photonne.app.resources.search_mode_text
import androidx.compose.foundation.layout.PaddingValues
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.grid.PhotoGridScrubberOverlay
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.PhotonneRefreshableScreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onOpenFilters: () -> Unit,
    onBack: () -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.ensureFacetsLoaded() }

    val gridState = rememberLazyGridState()
    val hazeState = remember { HazeState() }
    // Con una selección activa manda la cápsula de selección acoplada del Scaffold:
    // el buscador no dibuja su cromo flotante ni reserva su hueco (como el resto).
    val selecting = state.isSelectionActive
    val reservedTop = if (selecting) 0.dp else subscreenChromeReservedTop()

    Box(modifier = Modifier.fillMaxSize()) {
        PhotonneRefreshableScreen(
            isRefreshing = state.isLoading && state.results.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.results.isEmpty() ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    state.error?.userMessage != null && state.results.isEmpty() ->
                        Box(
                            modifier = Modifier.fillMaxSize().padding(top = reservedTop).padding(24.dp)
                        ) {
                            com.photonne.app.ui.error.ErrorBanner(error = state.error)
                        }
                    !state.hasAnyCriteria ->
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = stringResource(Res.string.search_idle_title),
                            subtitle = stringResource(Res.string.search_idle_subtitle)
                        )
                    state.results.isEmpty() ->
                        EmptyState(
                            icon = Icons.Outlined.Search,
                            title = stringResource(Res.string.search_empty_results)
                        )
                    else -> AssetGrid(
                        items = state.results,
                        baseUrl = apiBaseUrl,
                        gridState = gridState,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        selectedIds = state.selection,
                        hasMore = state.hasMore,
                        isAppending = state.isAppending,
                        isInitialLoading = state.isLoading,
                        onLoadMore = viewModel::loadMore,
                        contentPadding = PaddingValues(
                            top = reservedTop,
                            bottom = floatingNavBarReservedHeight()
                        ),
                        // Los filtros activos viajan como cabecera de la rejilla:
                        // se desplazan con los resultados en vez de flotar.
                        header = { ActiveFiltersRow(state = state) },
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState)
                    )
                }
            }
        }

        // La fecha central + mango salen siempre (el mes de cada foto es un dato
        // real aunque el orden sea por relevancia); el carril de años solo si el
        // resultado resulta ir en orden de fecha (lo decide buildAssetYearMarkers).
        // El filtro activo es la cabecera de la rejilla → headerCount=1.
        PhotoGridScrubberOverlay(
            gridState = gridState,
            items = state.results,
            headerCount = 1,
            reservedTop = reservedTop,
            reservedBottom = floatingNavBarReservedHeight(),
            selectionActive = selecting,
            hazeState = hazeState,
        )

        if (!selecting) {
            SubscreenFloatingChrome(
                title = "",
                onBack = onBack,
                titleContent = {
                    SearchFieldPill(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        onClear = { viewModel.setQuery("") },
                        modifier = Modifier.weight(1f)
                    )
                },
                scroll = SubscreenScroll(
                    firstVisibleItemIndex = { gridState.firstVisibleItemIndex },
                    firstVisibleItemScrollOffset = { gridState.firstVisibleItemScrollOffset },
                    isScrollInProgress = { gridState.isScrollInProgress },
                    scrollToTopMinIndex = 8,
                    onScrollToTop = { gridState.animateScrollToItem(0) }
                ),
                hazeState = hazeState,
                onChromeVisibleChange = onChromeVisibleChange,
                actions = {
                    IconButton(
                        onClick = onOpenFilters,
                        enabled = state.mode == SearchMode.Text
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = stringResource(Res.string.search_filters),
                            tint = if (state.activeFilterCount > 0)
                                MaterialTheme.colorScheme.primary
                            else LocalContentColor.current
                        )
                    }
                    SearchModeMenu(
                        mode = state.mode,
                        onModeChange = viewModel::setMode,
                        canClear = state.hasAnyCriteria,
                        onClearAll = viewModel::clearAll
                    )
                }
            )
        }
    }
}

// Campo de búsqueda sin borde, pensado para vivir dentro de la cápsula flotante
// estándar (icono + texto + limpiar), no un OutlinedTextField con su propia caja.
@Composable
private fun SearchFieldPill(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        stringResource(Res.string.search_input_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                inner()
            }
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.search_clear_all),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// Modo de búsqueda (Texto/Semántica) y limpiar, como botón de menú dentro de la
// cápsula de acciones en vez de una fila de chips que ocupa una línea entera.
@Composable
private fun SearchModeMenu(
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
    canClear: Boolean,
    onClearAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.search_mode_text)) },
                onClick = { onModeChange(SearchMode.Text); expanded = false },
                trailingIcon = {
                    if (mode == SearchMode.Text) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                    }
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.search_mode_semantic)) },
                onClick = { onModeChange(SearchMode.Semantic); expanded = false },
                trailingIcon = {
                    if (mode == SearchMode.Semantic) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                    }
                }
            )
            if (canClear) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.search_clear_all)) },
                    onClick = { onClearAll(); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ActiveFiltersRow(state: SearchUiState) {
    val chips = remember(state) {
        buildList {
            if (state.from != null || state.to != null) {
                add(state.from?.toString().orEmpty() + " — " + state.to?.toString().orEmpty())
            }
            if (state.ocrText.isNotBlank()) add("OCR: ${state.ocrText}")
            if (state.selectedPersonIds.isNotEmpty()) {
                val names = state.selectedPersonIds
                    .mapNotNull { id -> state.people.firstOrNull { it.id == id }?.name }
                    .take(2)
                val rest = state.selectedPersonIds.size - names.size
                add((names.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") ?: "")
                    .let { if (rest > 0) "$it +$rest" else it }
                    .ifBlank { "${state.selectedPersonIds.size}" })
            }
            for (label in state.selectedObjectLabels) add(label)
            for (label in state.selectedSceneLabels) add(label)
        }
    }
    if (chips.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        items(chips) { chip ->
            AssistChip(onClick = {}, label = { Text(chip) })
        }
    }
}
