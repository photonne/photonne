package com.photonne.app.ui.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.filters_direction_label
import com.photonne.app.resources.filters_sort_ascending
import com.photonne.app.resources.filters_sort_descending
import com.photonne.app.resources.filters_sort_label
import com.photonne.app.resources.filters_title
import com.photonne.app.resources.filters_view_label
import com.photonne.app.resources.folders_sort_asset_count
import com.photonne.app.resources.folders_sort_name
import com.photonne.app.resources.view_mode_grid
import com.photonne.app.resources.view_mode_list
import com.photonne.app.ui.util.SegmentOption
import com.photonne.app.ui.util.SegmentedChoiceRow
import com.photonne.app.ui.util.SortDirection
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersFiltersSheet(
    state: FoldersUiState,
    onDismiss: () -> Unit,
    onSortChange: (FolderSort) -> Unit,
    onDirectionChange: (SortDirection) -> Unit,
    onViewModeChange: (FolderViewMode) -> Unit
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
            SegmentedChoiceRow(
                options = listOf(
                    SegmentOption(FolderSort.Name, stringResource(Res.string.folders_sort_name)),
                    SegmentOption(
                        FolderSort.AssetCount,
                        stringResource(Res.string.folders_sort_asset_count)
                    )
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

            SectionLabel(stringResource(Res.string.filters_view_label))
            SegmentedChoiceRow(
                options = listOf(
                    SegmentOption(
                        FolderViewMode.Grid,
                        stringResource(Res.string.view_mode_grid),
                        Icons.Filled.ViewModule
                    ),
                    SegmentOption(
                        FolderViewMode.List,
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
