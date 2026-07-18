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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.data.models.YearCount
import com.photonne.app.ui.organize.YearBreakdownChips
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
    showOrganizeByDate: Boolean = false,
    yearBreakdown: List<YearCount> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (targetFolderId: String?, organizeByYear: Boolean) -> Unit
) {
    // Prune the moved folder's own subtree so it can't be dropped into a descendant.
    val excluded = remember(folders, excludeFolderId) {
        if (excludeFolderId == null) emptySet()
        else computeExclusionSet(folders, excludeFolderId)
    }
    val candidates = remember(folders, excluded) { folders.filterNot { it.id in excluded } }
    val roots = remember(candidates) { buildFolderForest(candidates) }
    val (personalRoots, sharedRoots) = remember(roots) { roots.partition { !it.isShared } }
    val byId = remember(candidates) { candidates.associateBy { it.id } }

    var selectedId by remember(initialSelectionId) { mutableStateOf<String?>(initialSelectionId) }
    var rootSelected by remember(initialSelectionId, includeRoot) {
        mutableStateOf(includeRoot && initialSelectionId == null)
    }
    var query by remember { mutableStateOf("") }
    var organizeByYear by remember { mutableStateOf(false) }
    // Groups open by default (this is a quick single-pick, unlike the album
    // picker) and the ancestors of the preselected destination are pre-expanded
    // so it's visible on open.
    var expanded by remember(candidates, initialSelectionId) {
        mutableStateOf(setOf("grp:personal", "grp:shared") + ancestorIds(byId, initialSelectionId))
    }

    val q = query.trim().lowercase()
    val searching = q.isNotEmpty()
    val personalNodes = if (searching) filterFolderTree(personalRoots, q) else personalRoots
    val sharedNodes = if (searching) filterFolderTree(sharedRoots, q) else sharedRoots
    val effExpanded = if (searching) collectFolderIds(personalNodes) + collectFolderIds(sharedNodes) else expanded

    val canSubmit = !isSubmitting && (rootSelected || selectedId != null)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Single-select: a radio in the trailing slot, no subtree "covered" locking.
    val trailing: @Composable (Boolean, Boolean, () -> Unit) -> Unit = { checked, enabled, onToggle ->
        RadioButton(selected = checked, onClick = onToggle, enabled = enabled)
    }

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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar carpeta…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val empty = personalNodes.isEmpty() && sharedNodes.isEmpty()
                // The root row is hidden while searching, so an empty search must
                // fall through to the "no results" message even when includeRoot.
                if (empty && (!includeRoot || searching)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (searching) "Sin resultados" else stringResource(Res.string.folder_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (includeRoot && !searching) {
                            item(key = "__root__") {
                                RootPickerRow(
                                    label = stringResource(Res.string.folder_picker_root),
                                    selected = rootSelected,
                                    onSelect = {
                                        rootSelected = true
                                        selectedId = null
                                    }
                                )
                            }
                        }
                        folderGroup(
                            groupId = "grp:personal", label = "Personales", nodes = personalNodes,
                            searching = searching, userExpanded = expanded, effExpanded = effExpanded,
                            onToggleGroup = { expanded = expanded.toggleMember("grp:personal") },
                            onToggleExpand = { id -> expanded = expanded.toggleMember(id) },
                            isSelected = { !rootSelected && selectedId == it.id }, isCovered = { false },
                            onToggleSelect = { rootSelected = false; selectedId = it.id },
                            trailing = trailing,
                        )
                        folderGroup(
                            groupId = "grp:shared", label = "Compartidas", nodes = sharedNodes,
                            searching = searching, userExpanded = expanded, effExpanded = effExpanded,
                            onToggleGroup = { expanded = expanded.toggleMember("grp:shared") },
                            onToggleExpand = { id -> expanded = expanded.toggleMember(id) },
                            isSelected = { !rootSelected && selectedId == it.id }, isCovered = { false },
                            onToggleSelect = { rootSelected = false; selectedId = it.id },
                            trailing = trailing,
                        )
                    }
                }
                if (errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
            if (showOrganizeByDate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubmitting) { organizeByYear = !organizeByYear }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = organizeByYear,
                        onCheckedChange = { organizeByYear = it },
                        enabled = !isSubmitting
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Organizar por año", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Se crearán subcarpetas por año (2026, 2025…) dentro del destino.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (organizeByYear && yearBreakdown.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Se repartirán en:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        YearBreakdownChips(yearBreakdown)
                    }
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
                    onClick = {
                        onConfirm(
                            if (rootSelected) null else selectedId,
                            showOrganizeByDate && organizeByYear
                        )
                    }
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
private fun RootPickerRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(top = 2.dp, bottom = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(32.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = onSelect)
    }
}

/** Parent chain of [id] (excluding itself), so the tree can be pre-expanded down
 * to a preselected destination. */
private fun ancestorIds(byId: Map<String, FolderSummary>, id: String?): Set<String> {
    if (id == null) return emptySet()
    val out = mutableSetOf<String>()
    var cur = byId[id]?.parentFolderId
    while (cur != null && out.add(cur)) cur = byId[cur]?.parentFolderId
    return out
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
