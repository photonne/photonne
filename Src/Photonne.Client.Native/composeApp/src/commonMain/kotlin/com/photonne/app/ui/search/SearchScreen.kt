package com.photonne.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.resources.Res
import com.photonne.app.resources.search_clear_all
import com.photonne.app.resources.search_empty_results
import com.photonne.app.resources.search_filters
import com.photonne.app.resources.search_idle_subtitle
import com.photonne.app.resources.search_idle_title
import com.photonne.app.resources.search_input_hint
import com.photonne.app.resources.search_mode_semantic
import com.photonne.app.resources.search_mode_text
import com.photonne.app.ui.grid.AssetGrid
import com.photonne.app.ui.theme.EmptyState
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onOpenFilters: () -> Unit
) {
    val apiBaseUrl = rememberApiBaseUrl()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.ensureFacetsLoaded() }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchInputBar(
            value = state.query,
            onValueChange = viewModel::setQuery,
            onClear = { viewModel.setQuery("") }
        )

        ModeAndFiltersRow(
            mode = state.mode,
            activeFilterCount = state.activeFilterCount,
            onModeChange = viewModel::setMode,
            onOpenFilters = onOpenFilters,
            canClear = state.hasAnyCriteria,
            onClearAll = viewModel::clearAll
        )

        ActiveFiltersRow(state = state)

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.results.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                state.error?.userMessage != null && state.results.isEmpty() ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            state.error?.userMessage ?: "",
                            color = MaterialTheme.colorScheme.error
                        )
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
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                    selectedIds = state.selection,
                    hasMore = state.hasMore,
                    isAppending = state.isAppending,
                    isInitialLoading = state.isLoading,
                    onLoadMore = viewModel::loadMore,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(Res.string.search_input_hint)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                }
            }
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun ModeAndFiltersRow(
    mode: SearchMode,
    activeFilterCount: Int,
    onModeChange: (SearchMode) -> Unit,
    onOpenFilters: () -> Unit,
    canClear: Boolean,
    onClearAll: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        FilterChip(
            selected = mode == SearchMode.Text,
            onClick = { onModeChange(SearchMode.Text) },
            label = { Text(stringResource(Res.string.search_mode_text)) }
        )
        FilterChip(
            selected = mode == SearchMode.Semantic,
            onClick = { onModeChange(SearchMode.Semantic) },
            label = { Text(stringResource(Res.string.search_mode_semantic)) }
        )
        AssistChip(
            onClick = onOpenFilters,
            label = {
                val text = stringResource(Res.string.search_filters) +
                    if (activeFilterCount > 0) " ($activeFilterCount)" else ""
                Text(text)
            },
            leadingIcon = { Icon(Icons.Filled.List, contentDescription = null) },
            enabled = mode == SearchMode.Text,
            colors = AssistChipDefaults.assistChipColors()
        )
        if (canClear) {
            AssistChip(
                onClick = onClearAll,
                label = { Text(stringResource(Res.string.search_clear_all)) }
            )
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
