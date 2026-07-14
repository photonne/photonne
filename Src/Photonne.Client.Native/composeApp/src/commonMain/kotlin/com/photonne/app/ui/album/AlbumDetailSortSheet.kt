package com.photonne.app.ui.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.album_sort_album_order
import com.photonne.app.resources.albums_sort_date
import com.photonne.app.resources.filters_direction_label
import com.photonne.app.resources.filters_sort_ascending
import com.photonne.app.resources.filters_sort_descending
import com.photonne.app.resources.filters_sort_label
import com.photonne.app.ui.util.SegmentOption
import com.photonne.app.ui.util.SegmentedChoiceRow
import com.photonne.app.ui.util.SortDirection
import org.jetbrains.compose.resources.stringResource

/**
 * Minimal sort control for an album's photos — sort criterion (album order /
 * date) plus direction. No filters, no view mode; a trimmed sibling of the
 * albums-list [AlbumsFiltersSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailSortSheet(
    sort: AlbumDetailSort,
    direction: SortDirection,
    onDismiss: () -> Unit,
    onSortChange: (AlbumDetailSort) -> Unit,
    onDirectionChange: (SortDirection) -> Unit
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
            SectionLabel(stringResource(Res.string.filters_sort_label))
            SegmentedChoiceRow(
                options = listOf(
                    SegmentOption(
                        AlbumDetailSort.Album,
                        stringResource(Res.string.album_sort_album_order)
                    ),
                    SegmentOption(
                        AlbumDetailSort.Date,
                        stringResource(Res.string.albums_sort_date)
                    )
                ),
                selected = sort,
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
                selected = direction,
                onSelect = onDirectionChange
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
