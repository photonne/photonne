package com.photonne.app.ui.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.FolderSummary

/**
 * A node of the folder tree shown in the collapsible pickers. Built client-side
 * from the flat folder list via [FolderSummary.parentFolderId]. Shared by the
 * smart-album folder condition picker (multi-select) and the move-destination
 * picker (single-select).
 */
data class FolderNode(
    val id: String,
    val name: String,
    val path: String,
    val isShared: Boolean,
    val children: List<FolderNode>,
)

/**
 * Builds a folder forest from a flat list: roots are the nodes whose parent is
 * null or absent from the set (orphans, e.g. when the parent was filtered out);
 * children are attached recursively by [FolderSummary.parentFolderId] and sorted
 * by name. Order-agnostic to the input list.
 */
fun buildFolderForest(all: List<FolderSummary>): List<FolderNode> {
    val childrenByParent = all.groupBy { it.parentFolderId }
    val ids = all.mapTo(HashSet()) { it.id }
    fun node(f: FolderSummary): FolderNode = FolderNode(
        id = f.id,
        name = f.name,
        path = f.path,
        isShared = f.isShared,
        children = (childrenByParent[f.id] ?: emptyList())
            .sortedBy { it.name.lowercase() }
            .map(::node),
    )
    return all.filter { it.parentFolderId == null || it.parentFolderId !in ids }
        .sortedBy { it.name.lowercase() }
        .map(::node)
}

/** Prunes the tree to branches containing a name match; keeps ancestors of
 * matches so the hierarchy/location stays visible. */
fun filterFolderTree(nodes: List<FolderNode>, q: String): List<FolderNode> =
    nodes.mapNotNull { node ->
        val kids = filterFolderTree(node.children, q)
        if (node.name.lowercase().contains(q) || kids.isNotEmpty()) node.copy(children = kids) else null
    }

fun collectFolderIds(nodes: List<FolderNode>): Set<String> =
    nodes.flatMapTo(mutableSetOf()) { listOf(it.id) + collectFolderIds(it.children) }

fun Set<String>.toggleMember(id: String): Set<String> = if (id in this) this - id else this + id

/** Renders a collapsible group header (e.g. "Personales" / "Compartidas") plus,
 * when open, its subtree. During search the group is forced open. The trailing
 * selection control is supplied by the caller (Checkbox for multi-select, radio
 * for single-select). */
fun LazyListScope.folderGroup(
    groupId: String,
    label: String,
    nodes: List<FolderNode>,
    searching: Boolean,
    userExpanded: Set<String>,
    effExpanded: Set<String>,
    onToggleGroup: () -> Unit,
    onToggleExpand: (String) -> Unit,
    isSelected: (FolderNode) -> Boolean,
    isCovered: (FolderNode) -> Boolean,
    onToggleSelect: (FolderNode) -> Unit,
    trailing: @Composable (checked: Boolean, enabled: Boolean, onToggle: () -> Unit) -> Unit,
) {
    if (nodes.isEmpty()) return
    val open = if (searching) true else groupId in userExpanded
    item(key = groupId) {
        FolderGroupHeaderRow(label = label, expanded = open, enabled = !searching, onToggle = onToggleGroup)
    }
    if (open) {
        renderFolderTree(nodes, 1, effExpanded, onToggleExpand, isSelected, isCovered, onToggleSelect, trailing)
    }
}

fun LazyListScope.renderFolderTree(
    nodes: List<FolderNode>,
    depth: Int,
    expanded: Set<String>,
    onToggleExpand: (String) -> Unit,
    isSelected: (FolderNode) -> Boolean,
    isCovered: (FolderNode) -> Boolean,
    onToggleSelect: (FolderNode) -> Unit,
    trailing: @Composable (checked: Boolean, enabled: Boolean, onToggle: () -> Unit) -> Unit,
) {
    nodes.forEach { node ->
        item(key = node.id) {
            val cov = isCovered(node)
            FolderTreeRow(
                node = node,
                depth = depth,
                expanded = node.id in expanded,
                checked = isSelected(node) || cov,
                enabled = !cov,
                onToggleExpand = { onToggleExpand(node.id) },
                onToggleSelect = { onToggleSelect(node) },
                trailing = trailing,
            )
        }
        if (node.id in expanded && node.children.isNotEmpty()) {
            renderFolderTree(node.children, depth + 1, expanded, onToggleExpand, isSelected, isCovered, onToggleSelect, trailing)
        }
    }
}

/** Collapsible parent row for the group headers. Disabled (always open) while
 * searching, so the filtered tree stays visible. */
@Composable
private fun FolderGroupHeaderRow(label: String, expanded: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onToggle).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "Colapsar" else "Expandir",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FolderTreeRow(
    node: FolderNode,
    depth: Int,
    expanded: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onToggleExpand: () -> Unit,
    onToggleSelect: () -> Unit,
    trailing: @Composable (checked: Boolean, enabled: Boolean, onToggle: () -> Unit) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggleSelect)
            .padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.children.isNotEmpty()) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                )
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }
        Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!enabled) {
                Text("incluida", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing(checked, enabled, onToggleSelect)
    }
}
