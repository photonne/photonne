package com.photonne.app.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.albums_filters_group_by_year
import com.photonne.app.resources.albums_sort_name
import com.photonne.app.resources.albums_sort_oldest
import com.photonne.app.resources.albums_sort_recent
import com.photonne.app.resources.filters_sort_label
import com.photonne.app.resources.filters_title
import com.photonne.app.resources.filters_view_label
import com.photonne.app.resources.view_mode_grid
import com.photonne.app.resources.view_mode_list
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlbumsFiltersSheet(
    state: AlbumsUiState,
    onDismiss: () -> Unit,
    onSortChange: (AlbumSort) -> Unit,
    onViewModeChange: (AlbumViewMode) -> Unit,
    onGroupByYearChange: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(Res.string.filters_title),
                style = MaterialTheme.typography.titleLarge
            )

            SectionLabel(stringResource(Res.string.filters_sort_label))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlbumSortChip(AlbumSort.Recent, state.sort, onSortChange, Res.string.albums_sort_recent)
                AlbumSortChip(AlbumSort.Oldest, state.sort, onSortChange, Res.string.albums_sort_oldest)
                AlbumSortChip(AlbumSort.Name, state.sort, onSortChange, Res.string.albums_sort_name)
            }

            HorizontalDivider()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(Res.string.albums_filters_group_by_year),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = state.groupByYear,
                    onCheckedChange = onGroupByYearChange
                )
            }

            HorizontalDivider()

            SectionLabel(stringResource(Res.string.filters_view_label))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewModeChip(
                    mode = AlbumViewMode.Grid,
                    selected = state.viewMode,
                    onChange = onViewModeChange,
                    labelRes = Res.string.view_mode_grid,
                    iconGrid = true
                )
                ViewModeChip(
                    mode = AlbumViewMode.List,
                    selected = state.viewMode,
                    onChange = onViewModeChange,
                    labelRes = Res.string.view_mode_list,
                    iconGrid = false
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumSortChip(
    value: AlbumSort,
    selected: AlbumSort,
    onChange: (AlbumSort) -> Unit,
    labelRes: org.jetbrains.compose.resources.StringResource
) {
    FilterChip(
        selected = selected == value,
        onClick = { onChange(value) },
        label = { Text(stringResource(labelRes)) },
        colors = FilterChipDefaults.filterChipColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeChip(
    mode: AlbumViewMode,
    selected: AlbumViewMode,
    onChange: (AlbumViewMode) -> Unit,
    labelRes: org.jetbrains.compose.resources.StringResource,
    iconGrid: Boolean
) {
    FilterChip(
        selected = selected == mode,
        onClick = { onChange(mode) },
        label = { Text(stringResource(labelRes)) },
        leadingIcon = {
            Icon(
                imageVector = if (iconGrid) Icons.Filled.ViewModule
                else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = null
            )
        }
    )
}
