package com.photonne.app.ui.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.ui.util.sortedByNatural
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_move
import com.photonne.app.resources.folder_picker_empty
import com.photonne.app.resources.folder_picker_root
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<FolderSummary>,
    isSubmitting: Boolean,
    errorMessage: String? = null,
    excludeFolderId: String? = null,
    includeRoot: Boolean = false,
    initialSelectionId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (targetFolderId: String?) -> Unit
) {
    val excluded = remember(folders, excludeFolderId) {
        if (excludeFolderId == null) emptySet()
        else computeExclusionSet(folders, excludeFolderId)
    }
    val candidates = remember(folders, excluded) {
        folders.filterNot { it.id in excluded }
            .sortedByNatural { it.path }
    }
    var selectedId by remember(initialSelectionId) {
        mutableStateOf<String?>(initialSelectionId)
    }
    var rootSelected by remember(initialSelectionId, includeRoot) {
        mutableStateOf(includeRoot && initialSelectionId == null)
    }
    val canSubmit = !isSubmitting && (rootSelected || selectedId != null)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (candidates.isEmpty() && !includeRoot) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(Res.string.folder_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (includeRoot) {
                            item(key = "__root__") {
                                FolderPickerRow(
                                    label = stringResource(Res.string.folder_picker_root),
                                    pathHint = null,
                                    depth = 0,
                                    selected = rootSelected,
                                    onClick = {
                                        rootSelected = true
                                        selectedId = null
                                    }
                                )
                            }
                        }
                        items(candidates, key = { it.id }) { folder ->
                            val display = folder.name.ifBlank { folder.path }
                            FolderPickerRow(
                                label = display,
                                pathHint = folder.path.takeIf { it != display },
                                depth = folder.path.count { it == '/' }.coerceAtLeast(0),
                                selected = !rootSelected && selectedId == folder.id,
                                onClick = {
                                    rootSelected = false
                                    selectedId = folder.id
                                }
                            )
                        }
                    }
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = canSubmit,
                    onClick = { onConfirm(if (rootSelected) null else selectedId) }
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(Res.string.action_move))
                }
            }
        }
    }
}

@Composable
private fun FolderPickerRow(
    label: String,
    pathHint: String?,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = (8 + (depth.coerceAtMost(4) * 12)).dp,
                end = 8.dp,
                top = 6.dp,
                bottom = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            if (pathHint != null) {
                Text(
                    pathHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

private fun computeExclusionSet(
    folders: List<FolderSummary>,
    excludeFolderId: String
): Set<String> {
    val byParent = folders.groupBy { it.parentFolderId }
    val result = mutableSetOf(excludeFolderId)
    val queue = ArrayDeque<String>()
    queue.addLast(excludeFolderId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val children = byParent[current].orEmpty()
        for (child in children) {
            if (result.add(child.id)) queue.addLast(child.id)
        }
    }
    return result
}
