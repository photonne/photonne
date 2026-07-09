package com.photonne.app.ui.album.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.datetime.toLocalDateTime
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.AlbumSummary
import org.koin.compose.viewmodel.koinViewModel

private enum class EditorSheet { Menu, People, Folders, Scenes, Objects, Dates }

/**
 * "Nuevo álbum inteligente" — the dedicated rule editor
 * (docs/smart-albums/creation-ux.md). A name, a flat Todas/Cualquiera toggle, a
 * list of condition chips (each editable via a bottom sheet), and a live preview
 * strip driven by the dry-run endpoint. Reuses the same resolver the saved album
 * will use, so the preview equals the real content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAlbumEditorScreen(
    onBack: () -> Unit,
    onCreated: (AlbumSummary) -> Unit,
    viewModel: SmartAlbumEditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pickers by viewModel.pickers.collectAsState()
    val baseUrl = rememberApiBaseUrl()
    var sheet by remember { mutableStateOf<EditorSheet?>(null) }

    // The VM is reused across entries (single ViewModelStoreOwner in the hand-rolled
    // nav), so start each "Nuevo álbum" from a blank slate instead of the last edit.
    LaunchedEffect(Unit) { viewModel.reset() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nuevo álbum inteligente") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.create(onCreated) },
                        enabled = state.canSave,
                    ) { Text("Crear") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Nombre del álbum") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Coincidir con: Todas (AND) / Cualquiera (OR)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Coincidir con")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.matchAll,
                        onClick = { viewModel.setMatchAll(true) },
                        label = { Text("Todas") },
                    )
                    FilterChip(
                        selected = !state.matchAll,
                        onClick = { viewModel.setMatchAll(false) },
                        label = { Text("Cualquiera") },
                    )
                }
            }

            // Condiciones
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Condiciones")
                if (state.conditions.isEmpty()) {
                    Text(
                        "Añade al menos una condición para definir el álbum.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.conditions.forEach { condition ->
                        ConditionRow(
                            condition = condition,
                            onEdit = {
                                sheet = when (condition) {
                                    is SmartCondition.People -> EditorSheet.People
                                    is SmartCondition.Folders -> EditorSheet.Folders
                                    is SmartCondition.Scenes -> EditorSheet.Scenes
                                    is SmartCondition.Objects -> EditorSheet.Objects
                                    is SmartCondition.DateRange -> EditorSheet.Dates
                                }
                            },
                            onRemove = { viewModel.removeCondition(condition.key) },
                        )
                    }
                }
                TextButton(onClick = { sheet = EditorSheet.Menu }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Añadir condición")
                }
            }

            // Preview
            SmartPreviewSection(
                count = state.previewCount,
                sampleIds = state.previewSampleIds,
                isPreviewing = state.isPreviewing,
                hasConditions = state.activeConditions.isNotEmpty(),
                baseUrl = baseUrl,
            )

            state.error?.let { err ->
                Text(
                    err.userMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Bottom sheets ────────────────────────────────────────────────────────
    // Load only the source of the picker that just opened (empty query = default
    // top-N); reopening a loaded picker keeps its previous results/query.
    LaunchedEffect(sheet) {
        when (sheet) {
            EditorSheet.People -> if (!pickers.people.loadedOnce) viewModel.setPeopleQuery("")
            EditorSheet.Scenes -> if (!pickers.scenes.loadedOnce) viewModel.setSceneQuery("")
            EditorSheet.Objects -> if (!pickers.objects.loadedOnce) viewModel.setObjectQuery("")
            EditorSheet.Folders -> viewModel.ensureFolders()
            else -> Unit
        }
    }

    when (sheet) {
        EditorSheet.Menu -> AddConditionMenuSheet(
            onDismiss = { sheet = null },
            onPick = { sheet = it },
        )
        EditorSheet.People -> PeopleSelectSheet(
            results = pickers.people,
            baseUrl = baseUrl,
            existing = state.conditions.filterIsInstance<SmartCondition.People>().firstOrNull(),
            onQueryChange = viewModel::setPeopleQuery,
            onDismiss = { sheet = null },
            onConfirm = { viewModel.upsertCondition(it); sheet = null },
        )
        EditorSheet.Folders -> FolderSelectSheet(
            roots = pickers.folderRoots,
            all = pickers.folders,
            isLoading = pickers.foldersLoading,
            existing = state.conditions.filterIsInstance<SmartCondition.Folders>().firstOrNull(),
            onDismiss = { sheet = null },
            onConfirm = { viewModel.upsertCondition(it); sheet = null },
        )
        EditorSheet.Scenes -> LabelSelectSheet(
            title = "Escenas",
            placeholder = "Buscar escena…",
            results = pickers.scenes,
            baseUrl = baseUrl,
            existingLabels = state.conditions.filterIsInstance<SmartCondition.Scenes>().firstOrNull()?.labels ?: emptyList(),
            onQueryChange = viewModel::setSceneQuery,
            onDismiss = { sheet = null },
            onConfirm = { viewModel.upsertCondition(SmartCondition.Scenes(it)); sheet = null },
        )
        EditorSheet.Objects -> LabelSelectSheet(
            title = "Objetos",
            placeholder = "Buscar objeto…",
            results = pickers.objects,
            baseUrl = baseUrl,
            existingLabels = state.conditions.filterIsInstance<SmartCondition.Objects>().firstOrNull()?.labels ?: emptyList(),
            onQueryChange = viewModel::setObjectQuery,
            onDismiss = { sheet = null },
            onConfirm = { viewModel.upsertCondition(SmartCondition.Objects(it)); sheet = null },
        )
        EditorSheet.Dates -> DateRangeSheet(
            existing = state.conditions.filterIsInstance<SmartCondition.DateRange>().firstOrNull(),
            onDismiss = { sheet = null },
            onConfirm = { viewModel.upsertCondition(it); sheet = null },
        )
        null -> Unit
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ConditionRow(
    condition: SmartCondition,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(condition.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(condition.title(), style = MaterialTheme.typography.titleSmall)
                Text(
                    condition.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Quitar")
            }
        }
    }
}

private fun SmartCondition.icon(): ImageVector = when (this) {
    is SmartCondition.People -> Icons.Outlined.Person
    is SmartCondition.Folders -> Icons.Outlined.Folder
    is SmartCondition.DateRange -> Icons.Outlined.DateRange
    is SmartCondition.Scenes -> Icons.Outlined.Image
    is SmartCondition.Objects -> Icons.Outlined.Category
}

private fun SmartCondition.title(): String = when (this) {
    is SmartCondition.People -> "Personas"
    is SmartCondition.Folders -> "Carpetas"
    is SmartCondition.DateRange -> "Fechas"
    is SmartCondition.Scenes -> "Escenas"
    is SmartCondition.Objects -> "Objetos"
}

private fun SmartCondition.summary(): String = when (this) {
    is SmartCondition.People -> {
        val names = people.joinToString(", ") { it.name }
        val mode = if (matchAll) " (todas juntas)" else " (cualquiera)"
        if (people.isEmpty()) "Sin selección" else names + mode
    }
    is SmartCondition.Folders ->
        if (folders.isEmpty()) "Sin selección" else folders.joinToString(", ") { it.name } + " (+ subcarpetas)"
    is SmartCondition.DateRange -> when {
        from != null && to != null -> "$from → $to"
        from != null -> "Desde $from"
        to != null -> "Hasta $to"
        else -> "Sin rango"
    }
    is SmartCondition.Scenes -> if (labels.isEmpty()) "Sin selección" else labels.joinToString(", ")
    is SmartCondition.Objects -> if (labels.isEmpty()) "Sin selección" else labels.joinToString(", ")
}

@Composable
private fun SmartPreviewSection(
    count: Int?,
    sampleIds: List<String>,
    isPreviewing: Boolean,
    hasConditions: Boolean,
    baseUrl: String,
) {
    if (!hasConditions) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    count != null -> "$count fotos coinciden"
                    else -> "Calculando…"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (isPreviewing) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
        if (sampleIds.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sampleIds, key = { it }) { id ->
                    AsyncImage(
                        model = "$baseUrl/api/assets/$id/thumbnail?size=Small",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

// ── Add-condition menu ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConditionMenuSheet(onDismiss: () -> Unit, onPick: (EditorSheet) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Añadir condición",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            MenuRow(Icons.Outlined.Person, "Personas") { onPick(EditorSheet.People) }
            MenuRow(Icons.Outlined.Folder, "Carpetas") { onPick(EditorSheet.Folders) }
            MenuRow(Icons.Outlined.DateRange, "Fechas") { onPick(EditorSheet.Dates) }
            MenuRow(Icons.Outlined.Image, "Escenas") { onPick(EditorSheet.Scenes) }
            MenuRow(Icons.Outlined.Category, "Objetos") { onPick(EditorSheet.Objects) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

// ── People select ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleSelectSheet(
    results: PickerResults<PersonRef>,
    baseUrl: String,
    existing: SmartCondition.People?,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (SmartCondition.People) -> Unit,
) {
    var selected by remember { mutableStateOf(existing?.people?.map { it.id }?.toSet() ?: emptySet()) }
    var matchAll by remember { mutableStateOf(existing?.matchAll ?: false) }
    // Display data for the selected-chips header, independent of the current
    // search results (a selected person may not be in the visible top-N).
    val refs = remember { mutableStateMapOf<String, PersonRef>().apply { existing?.people?.forEach { put(it.id, it) } } }

    SearchSelectScaffold(
        title = "Personas",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(SmartCondition.People(people = selected.mapNotNull { refs[it] }, matchAll = matchAll)) },
        confirmEnabled = selected.isNotEmpty(),
        header = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !matchAll, onClick = { matchAll = false }, label = { Text("Cualquiera") })
                FilterChip(selected = matchAll, onClick = { matchAll = true }, label = { Text("Todas juntas") })
            }
            PickerSearchField(results.query, onQueryChange, "Buscar persona…")
            SelectedChipsHeader(
                items = selected.mapNotNull { id -> refs[id]?.let { id to it.name } },
                onRemove = { selected = selected - it },
            )
        },
        body = {
            PickerListBody(results.isLoading, results.loadedOnce, results.results.isEmpty()) {
                items(results.results, key = { it.id }) { person ->
                    val isSel = person.id in selected
                    PersonSelectRow(person, baseUrl, isSel) {
                        selected = if (isSel) selected - person.id else { refs[person.id] = person; selected + person.id }
                    }
                }
            }
        },
    )
}

// ── Folder select (expandable tree + search fallback) ────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderSelectSheet(
    roots: List<FolderNode>,
    all: List<FolderRef>,
    isLoading: Boolean,
    existing: SmartCondition.Folders?,
    onDismiss: () -> Unit,
    onConfirm: (SmartCondition.Folders) -> Unit,
) {
    var selected by remember { mutableStateOf(existing?.folders?.map { it.id }?.toSet() ?: emptySet()) }
    var query by remember { mutableStateOf("") }
    // Everything starts collapsed — including the Personales / Compartidas groups.
    var expanded by remember(roots) { mutableStateOf(emptySet<String>()) }
    val refById = remember(all, existing) { (all + (existing?.folders ?: emptyList())).associateBy { it.id } }
    val selectedPaths = remember(selected, refById) { selected.mapNotNull { refById[it]?.path } }

    // A folder is "covered" when a strict ancestor is already selected: the
    // backend pulls its whole subtree in, so it's shown checked + locked.
    fun covered(path: String): Boolean = selectedPaths.any { p -> path != p && path.startsWith("$p/") }
    // Selecting a folder drops any already-selected descendants (the parent
    // now covers them) so the chip set and the wire rule stay minimal.
    fun toggle(id: String, path: String) {
        selected = if (id in selected) selected - id
        else (selected.filterNot { refById[it]?.path?.startsWith("$path/") == true } + id).toSet()
    }

    val q = query.trim().lowercase()
    val searching = q.isNotEmpty()
    val (personalRoots, sharedRoots) = roots.partition { !it.isShared }
    // Search stays in tree view: prune to branches that contain a match, keeping
    // the ancestors so you see WHERE the match lives; then force everything open.
    val personalNodes = if (searching) filterFolderTree(personalRoots, q) else personalRoots
    val sharedNodes = if (searching) filterFolderTree(sharedRoots, q) else sharedRoots
    val effExpanded = if (searching) collectFolderIds(personalNodes) + collectFolderIds(sharedNodes) else expanded

    SearchSelectScaffold(
        title = "Carpetas",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(SmartCondition.Folders(selected.mapNotNull { refById[it] })) },
        confirmEnabled = selected.isNotEmpty(),
        header = {
            PickerSearchField(query, { query = it }, "Buscar carpeta…")
            Text(
                "Se incluyen también las subcarpetas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectedChipsHeader(
                items = selected.mapNotNull { id -> refById[id]?.let { id to it.name } },
                onRemove = { selected = selected - it },
            )
        },
        body = {
            when {
                isLoading && all.isEmpty() ->
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                personalNodes.isEmpty() && sharedNodes.isEmpty() ->
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (searching) "Sin resultados" else "No hay carpetas.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    folderGroup(
                        groupId = "grp:personal", label = "Personales", nodes = personalNodes,
                        searching = searching, userExpanded = expanded, effExpanded = effExpanded,
                        onToggleGroup = { expanded = expanded.toggleMember("grp:personal") },
                        onToggleExpand = { id -> expanded = expanded.toggleMember(id) },
                        isSelected = { it.id in selected }, isCovered = { covered(it.path) },
                        onToggleSelect = { toggle(it.id, it.path) },
                    )
                    folderGroup(
                        groupId = "grp:shared", label = "Compartidas", nodes = sharedNodes,
                        searching = searching, userExpanded = expanded, effExpanded = effExpanded,
                        onToggleGroup = { expanded = expanded.toggleMember("grp:shared") },
                        onToggleExpand = { id -> expanded = expanded.toggleMember(id) },
                        isSelected = { it.id in selected }, isCovered = { covered(it.path) },
                        onToggleSelect = { toggle(it.id, it.path) },
                    )
                }
            }
        },
    )
}

/** Renders a collapsible "Personales" / "Compartidas" group header plus, when
 * open, its subtree. During search the group is forced open. */
private fun LazyListScope.folderGroup(
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
) {
    if (nodes.isEmpty()) return
    val open = if (searching) true else groupId in userExpanded
    item(key = groupId) {
        FolderGroupHeaderRow(label = label, expanded = open, enabled = !searching, onToggle = onToggleGroup)
    }
    if (open) {
        renderFolderTree(nodes, 1, effExpanded, onToggleExpand, isSelected, isCovered, onToggleSelect)
    }
}

/** Prunes the tree to branches containing a name match; keeps ancestors of
 * matches so the hierarchy/location stays visible. */
private fun filterFolderTree(nodes: List<FolderNode>, q: String): List<FolderNode> =
    nodes.mapNotNull { node ->
        val kids = filterFolderTree(node.children, q)
        if (node.name.lowercase().contains(q) || kids.isNotEmpty()) node.copy(children = kids) else null
    }

private fun collectFolderIds(nodes: List<FolderNode>): Set<String> =
    nodes.flatMapTo(mutableSetOf()) { listOf(it.id) + collectFolderIds(it.children) }

private fun Set<String>.toggleMember(id: String): Set<String> = if (id in this) this - id else this + id

private fun LazyListScope.renderFolderTree(
    nodes: List<FolderNode>,
    depth: Int,
    expanded: Set<String>,
    onToggleExpand: (String) -> Unit,
    isSelected: (FolderNode) -> Boolean,
    isCovered: (FolderNode) -> Boolean,
    onToggleSelect: (FolderNode) -> Unit,
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
            )
        }
        if (node.id in expanded && node.children.isNotEmpty()) {
            renderFolderTree(node.children, depth + 1, expanded, onToggleExpand, isSelected, isCovered, onToggleSelect)
        }
    }
}

/** Collapsible parent row for the "Personales" / "Compartidas" groups. Disabled
 * (always open) while searching, so the filtered tree stays visible. */
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
        Checkbox(checked = checked, onCheckedChange = { onToggleSelect() }, enabled = enabled)
    }
}

// ── Label select (scenes / objects) ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelSelectSheet(
    title: String,
    placeholder: String,
    results: PickerResults<LabelRef>,
    baseUrl: String,
    existingLabels: List<String>,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var picked by remember { mutableStateOf(existingLabels.toSet()) }

    SearchSelectScaffold(
        title = title,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(picked.toList()) },
        confirmEnabled = picked.isNotEmpty(),
        header = {
            PickerSearchField(results.query, onQueryChange, placeholder)
            SelectedChipsHeader(items = picked.map { it to it }, onRemove = { picked = picked - it })
        },
        body = {
            PickerListBody(results.isLoading, results.loadedOnce, results.results.isEmpty()) {
                items(results.results, key = { it.label }) { label ->
                    val isSel = label.label in picked
                    LabelRow(label, baseUrl, isSel) {
                        picked = if (isSel) picked - label.label else picked + label.label
                    }
                }
            }
        },
    )
}

// ── Searchable picker building blocks ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSelectScaffold(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    header: @Composable ColumnScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            header()
            body()
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, enabled = confirmEnabled) { Text("Aplicar") }
            }
        }
    }
}

/** Loading / empty / list body that fills the remaining sheet height. */
@Composable
private fun ColumnScope.PickerListBody(
    isLoading: Boolean,
    loadedOnce: Boolean,
    isEmpty: Boolean,
    content: LazyListScope.() -> Unit,
) {
    when {
        isLoading && isEmpty ->
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        loadedOnce && isEmpty ->
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Sin resultados", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun PickerSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Limpiar") }
            }
        },
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SelectedChipsHeader(items: List<Pair<String, String>>, onRemove: (String) -> Unit) {
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (id, text) ->
            InputChip(
                selected = true,
                onClick = { onRemove(id) },
                label = { Text(text) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Quitar", modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun PersonSelectRow(person: PersonRef, baseUrl: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            person.coverFaceId?.let {
                AsyncImage(
                    model = "$baseUrl/api/faces/$it/thumbnail",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(person.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun LabelRow(label: LabelRef, baseUrl: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            label.coverAssetId?.let {
                AsyncImage(
                    model = "$baseUrl/api/assets/$it/thumbnail?size=Small",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(label.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

// ── Date range ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeSheet(
    existing: SmartCondition.DateRange?,
    onDismiss: () -> Unit,
    onConfirm: (SmartCondition.DateRange) -> Unit,
) {
    var from by remember { mutableStateOf(existing?.from) }
    var to by remember { mutableStateOf(existing?.to) }
    var picking by remember { mutableStateOf<Boolean?>(null) } // true = from, false = to

    SelectSheetScaffold(
        title = "Fechas",
        isLoading = false,
        isEmpty = false,
        emptyText = "",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(SmartCondition.DateRange(from, to)) },
        confirmEnabled = from != null || to != null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                Text(from ?: "Desde")
            }
            androidx.compose.material3.OutlinedButton(onClick = { picking = false }, modifier = Modifier.weight(1f)) {
                Text(to ?: "Hasta")
            }
        }
    }

    picking?.let { isFrom ->
        DatePickerSheet(
            initialIso = if (isFrom) from else to,
            onPick = { iso ->
                if (isFrom) from = iso else to = iso
                picking = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(initialIso: String?, onPick: (String?) -> Unit) {
    val initialMillis = initialIso?.let {
        runCatching {
            val date = kotlinx.datetime.LocalDate.parse(it)
            date.toEpochDays().toLong() * 86_400_000L
        }.getOrNull()
    }
    val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = { onPick(initialIso) },
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                val iso = millis?.let {
                    kotlinx.datetime.Instant.fromEpochMilliseconds(it)
                        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date.toString()
                }
                onPick(iso)
            }) { Text("Aceptar") }
        },
        dismissButton = {
            TextButton(onClick = { onPick(initialIso) }) { Text("Cancelar") }
        },
    ) {
        androidx.compose.material3.DatePicker(state = pickerState)
    }
}

// ── Shared sheet scaffold ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectSheetScaffold(
    title: String,
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            when {
                isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                isEmpty -> Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> content()
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(onClick = onConfirm, enabled = confirmEnabled) {
                    Text("Aplicar")
                }
            }
        }
    }
}
