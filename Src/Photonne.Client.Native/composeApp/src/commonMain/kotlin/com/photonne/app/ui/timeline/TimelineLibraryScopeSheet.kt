package com.photonne.app.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.devicelibrary.DeviceBucket
import com.photonne.app.data.devicelibrary.DeviceLibraryScope
import com.photonne.app.resources.Res
import com.photonne.app.resources.backup_bucket_item_count
import com.photonne.app.resources.timeline_scope_all
import com.photonne.app.resources.timeline_scope_all_hint
import com.photonne.app.resources.timeline_scope_backed_up
import com.photonne.app.resources.timeline_scope_camera
import com.photonne.app.resources.timeline_scope_camera_hint
import com.photonne.app.resources.timeline_scope_custom
import com.photonne.app.resources.timeline_scope_custom_hint
import com.photonne.app.resources.timeline_scope_sheet_hint
import com.photonne.app.resources.timeline_scope_sheet_title
import com.photonne.app.resources.timeline_scope_synced
import com.photonne.app.resources.timeline_scope_synced_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet choosing which slice of the device library the timeline
 * shows — a visibility preference, deliberately separate from the
 * backup-folder list (see [DeviceLibraryScope]). Buckets already being
 * backed up carry a small "backed up" tag so both worlds stay legible
 * without being coupled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineLibraryScopeSheet(
    scope: DeviceLibraryScope,
    /** Null while the bucket enumeration is still running. */
    buckets: List<DeviceBucket>?,
    /** Folder-ref uris currently in the backup list, for the tag. */
    backedUpUris: Set<String>,
    onSelect: (DeviceLibraryScope) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item("header") {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        stringResource(Res.string.timeline_scope_sheet_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(Res.string.timeline_scope_sheet_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            item("mode-camera") {
                ScopeModeRow(
                    title = stringResource(Res.string.timeline_scope_camera),
                    hint = stringResource(Res.string.timeline_scope_camera_hint),
                    selected = scope == DeviceLibraryScope.CameraOnly,
                    onClick = { onSelect(DeviceLibraryScope.CameraOnly) }
                )
            }
            item("mode-all") {
                ScopeModeRow(
                    title = stringResource(Res.string.timeline_scope_all),
                    hint = stringResource(Res.string.timeline_scope_all_hint),
                    selected = scope == DeviceLibraryScope.All,
                    onClick = { onSelect(DeviceLibraryScope.All) }
                )
            }
            item("mode-synced") {
                ScopeModeRow(
                    title = stringResource(Res.string.timeline_scope_synced),
                    hint = stringResource(Res.string.timeline_scope_synced_hint),
                    selected = scope == DeviceLibraryScope.SyncedOnly,
                    onClick = { onSelect(DeviceLibraryScope.SyncedOnly) }
                )
            }
            item("mode-custom") {
                ScopeModeRow(
                    title = stringResource(Res.string.timeline_scope_custom),
                    hint = stringResource(Res.string.timeline_scope_custom_hint),
                    selected = scope is DeviceLibraryScope.Buckets,
                    onClick = {
                        if (scope !is DeviceLibraryScope.Buckets) {
                            // Start from everything checked — the user came to
                            // UNcheck the noise, not to rebuild the list.
                            onSelect(
                                DeviceLibraryScope.Buckets(
                                    buckets.orEmpty().map { it.id }.toSet()
                                )
                            )
                        }
                    }
                )
            }
            if (scope is DeviceLibraryScope.Buckets) {
                if (buckets == null) {
                    item("buckets-loading") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    items(buckets, key = { "bucket-${it.id}" }) { bucket ->
                        val checked = bucket.id in scope.bucketIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val ids = if (checked) scope.bucketIds - bucket.id
                                    else scope.bucketIds + bucket.id
                                    onSelect(DeviceLibraryScope.Buckets(ids))
                                }
                                .padding(start = 32.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Spacer(Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    bucket.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    stringResource(
                                        Res.string.backup_bucket_item_count, bucket.itemCount
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (bucket.toFolderRef().uri in backedUpUris) {
                                Text(
                                    stringResource(Res.string.timeline_scope_backed_up),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeModeRow(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
