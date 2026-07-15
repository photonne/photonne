package com.photonne.app.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.photonne.app.resources.albums_scope_all
import com.photonne.app.resources.albums_sort_date
import com.photonne.app.resources.albums_sort_name
import com.photonne.app.resources.albums_scope_mine
import com.photonne.app.resources.albums_scope_shared
import com.photonne.app.resources.filters_direction_label
import com.photonne.app.resources.filters_scope_label
import com.photonne.app.resources.filters_sort_ascending
import com.photonne.app.resources.filters_sort_descending
import com.photonne.app.resources.filters_sort_label
import com.photonne.app.resources.filters_title
import com.photonne.app.resources.filters_view_label
import com.photonne.app.resources.view_mode_grid
import com.photonne.app.resources.view_mode_list
import com.photonne.app.ui.util.SegmentOption
import com.photonne.app.ui.util.SegmentedChoiceRow
import com.photonne.app.ui.util.SortDirection
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsFiltersSheet(
    state: AlbumsUiState,
    onDismiss: () -> Unit,
    onScopeChange: (AlbumsScope) -> Unit,
    onSortChange: (AlbumSort) -> Unit,
    onDirectionChange: (SortDirection) -> Unit,
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

            SectionLabel(stringResource(Res.string.filters_scope_label))
            SegmentedChoiceRow(
                options = listOf(
                    SegmentOption(AlbumsScope.All, stringResource(Res.string.albums_scope_all)),
                    SegmentOption(AlbumsScope.Mine, stringResource(Res.string.albums_scope_mine)),
                    SegmentOption(
                        AlbumsScope.Shared,
                        stringResource(Res.string.albums_scope_shared)
                    )
                ),
                selected = state.scope,
                onSelect = onScopeChange
            )

            HorizontalDivider()

            SectionLabel(stringResource(Res.string.filters_sort_label))
            SegmentedChoiceRow(
                options = listOf(
                    SegmentOption(AlbumSort.Date, stringResource(Res.string.albums_sort_date)),
                    SegmentOption(AlbumSort.Name, stringResource(Res.string.albums_sort_name))
                ),
                selected = state.sort,
                onSelect = onSortChange
            )

            SectionLabel(stringResource(Res.string.filters_direction_label))
            SegmentedChoiceRow(
                options = listOf(
                    SegmentOption(
                        SortDirection.Ascending,
                        stringResource(Res.string.filters_sort_ascending)
                    ),
                    SegmentOption(
                        SortDirection.Descending,
                        stringResource(Res.string.filters_sort_descending)
                    )
                ),
                selected = state.direction,
                onSelect = onDirectionChange
            )

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
            SegmentedChoiceRow(
                options = listOf(
                    SegmentOption(
                        AlbumViewMode.Grid,
                        stringResource(Res.string.view_mode_grid),
                        Icons.Filled.ViewModule
                    ),
                    SegmentOption(
                        AlbumViewMode.List,
                        stringResource(Res.string.view_mode_list),
                        Icons.AutoMirrored.Filled.ViewList
                    )
                ),
                selected = state.viewMode,
                onSelect = onViewModeChange
            )
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
