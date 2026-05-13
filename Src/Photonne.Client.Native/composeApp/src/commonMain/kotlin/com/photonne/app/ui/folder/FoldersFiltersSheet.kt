package com.photonne.app.ui.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.filters_sort_label
import com.photonne.app.resources.filters_title
import com.photonne.app.resources.filters_view_label
import com.photonne.app.resources.folders_sort_asset_count
import com.photonne.app.resources.folders_sort_name
import com.photonne.app.resources.view_mode_grid
import com.photonne.app.resources.view_mode_list
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoldersFiltersSheet(
    state: FoldersUiState,
    onDismiss: () -> Unit,
    onSortChange: (FolderSort) -> Unit,
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FolderSortChip(FolderSort.Name, state.sort, onSortChange, Res.string.folders_sort_name)
                FolderSortChip(
                    FolderSort.AssetCount,
                    state.sort,
                    onSortChange,
                    Res.string.folders_sort_asset_count
                )
            }

            HorizontalDivider()

            SectionLabel(stringResource(Res.string.filters_view_label))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ViewModeChip(
                    mode = FolderViewMode.Grid,
                    selected = state.viewMode,
                    onChange = onViewModeChange,
                    labelRes = Res.string.view_mode_grid,
                    iconGrid = true
                )
                ViewModeChip(
                    mode = FolderViewMode.List,
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
private fun FolderSortChip(
    value: FolderSort,
    selected: FolderSort,
    onChange: (FolderSort) -> Unit,
    labelRes: org.jetbrains.compose.resources.StringResource
) {
    FilterChip(
        selected = selected == value,
        onClick = { onChange(value) },
        label = { Text(stringResource(labelRes)) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeChip(
    mode: FolderViewMode,
    selected: FolderViewMode,
    onChange: (FolderViewMode) -> Unit,
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
