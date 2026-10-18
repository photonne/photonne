package com.photonne.app.ui.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.photonne.app.resources.action_create
import com.photonne.app.resources.folder_action_new
import com.photonne.app.resources.folder_field_name
import com.photonne.app.resources.folder_picker_new_in
import com.photonne.app.resources.folder_picker_new_in_root
import com.photonne.app.resources.folder_picker_new_slash
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import com.photonne.app.resources.folder_picker_recent
import com.photonne.app.data.error.UiError
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.data.models.YearCount
import com.photonne.app.ui.organize.YearBreakdownChips
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_move
import com.photonne.app.resources.folder_picker_empty
import com.photonne.app.resources.folder_picker_root
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.PrimaryActionButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.SheetHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<FolderSummary>,
    isSubmitting: Boolean,
    errorMessage: String? = null,
    excludeFolderId: String? = null,
    /** Como [excludeFolderId], para mover varias carpetas a la vez. */
    excludeFolderIds: Set<String> = emptySet(),
    includeRoot: Boolean = false,
    initialSelectionId: String? = null,
    showOrganizeByDate: Boolean = false,
    yearBreakdown: List<YearCount> = emptyList(),
    /**
     * Últimos destinos usados, la más reciente primero. Se pintan como atajos
     * sobre el árbol: organizar es repetitivo y sin memoria cada movimiento
     * obliga a recorrerlo entero otra vez.
     */
    recentDestinationIds: List<String> = emptyList(),
    /**
     * Etiqueta del botón de confirmar. Por defecto "Mover", pero el selector
     * también sirve para elegir destino sin mover nada todavía (reglas de
     * organización), donde "Mover" mentía.
     */
    confirmLabel: String? = null,
    /** The destination list is still loading (shows a spinner instead of "no folders"). */
    isLoading: Boolean = false,
    /**
     * Why the destination list couldn't load. With nothing to list it replaces
     * the "no folders" message, with [onRetry]: telling a failed load from a
     * real empty list.
     */
    loadError: UiError? = null,
    /** Reloads the destinations; also called on open when they're empty or failed. */
    onRetry: (() -> Unit)? = null,
    /**
     * Crea una carpeta dentro del destino elegido (o en la raíz personal sin
     * ninguno) sin salir del selector, que la deja elegida en cuanto llega a
     * [folders]. Null: el selector no ofrece crear.
     */
    onCreateFolder: ((
        name: String,
        parentFolderId: String?,
        onCreated: (FolderSummary) -> Unit,
        onFailed: (String) -> Unit,
    ) -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: (targetFolderId: String?, organizeByYear: Boolean) -> Unit
) {
    LaunchedEffect(Unit) {
        if (onRetry != null && !isLoading && (folders.isEmpty() || loadError != null)) onRetry()
    }
    // Prune the moved folder's own subtree so it can't be dropped into a descendant.
    val excluded = remember(folders, excludeFolderId, excludeFolderIds) {
        (excludeFolderIds + listOfNotNull(excludeFolderId))
            .flatMapTo(mutableSetOf()) { computeExclusionSet(folders, it) }
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
    // so it's visible on open. Not keyed on the folder list: a folder created
    // here would otherwise fold back everything the user had opened.
    var expanded by remember(initialSelectionId) {
        mutableStateOf(setOf("grp:personal", "grp:shared"))
    }
    // The list may arrive after the sheet opens.
    LaunchedEffect(initialSelectionId, byId.isNotEmpty()) {
        expanded = expanded + ancestorIds(byId, initialSelectionId)
    }

    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var creatingFolder by remember { mutableStateOf(false) }
    var newFolderError by remember { mutableStateOf<String?>(null) }

    val q = query.trim().lowercase()
    val searching = q.isNotEmpty()
    val personalNodes = if (searching) filterFolderTree(personalRoots, q) else personalRoots
    val sharedNodes = if (searching) filterFolderTree(sharedRoots, q) else sharedRoots
    val effExpanded = if (searching) collectFolderIds(personalNodes) + collectFolderIds(sharedNodes) else expanded

    val canSubmit = !isSubmitting && !creatingFolder && (rootSelected || selectedId != null)
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
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SheetHeader(title)

            // Solo fuera de búsqueda: buscando, los atajos compiten con lo que
            // se está buscando en vez de ahorrar trabajo.
            val recents = remember(recentDestinationIds, byId) {
                recentDestinationIds.mapNotNull { byId[it] }
            }
            if (recents.isNotEmpty() && !searching) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(Res.string.folder_picker_recent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        recents.forEach { folder ->
                            FilterChip(
                                selected = !rootSelected && selectedId == folder.id,
                                onClick = { rootSelected = false; selectedId = folder.id },
                                enabled = !isSubmitting,
                                label = { Text(folder.name, maxLines = 1) },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                placeholder = { Text("Buscar carpeta…") },
                leadingIcon = { Icon(PhotonneIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(PhotonneIcons.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
            )
            // El árbol es lo ÚNICO elástico de la hoja: se queda con el espacio que
            // sobre (y scrollea dentro) en vez de reservarse una altura fija. Con una
            // altura fija, todo lo que va detrás —organizar por año, los chips y la
            // fila Cancelar/Mover— se desbordaba por debajo del borde de la hoja, y
            // como aquí no hay scroll exterior el botón quedaba inalcanzable.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                val empty = personalNodes.isEmpty() && sharedNodes.isEmpty()
                if (folders.isEmpty() && isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                } else if (folders.isEmpty() && loadError != null) {
                    ErrorBanner(error = loadError, onRetry = onRetry)
                // The root row is hidden while searching, so an empty search must
                // fall through to the "no results" message even when includeRoot.
                } else if (empty && (!includeRoot || searching)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
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
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
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
            }
            if (onCreateFolder != null && !(folders.isEmpty() && (isLoading || loadError != null))) {
                val parent = if (rootSelected) null else selectedId?.let(byId::get)
                val badName = newFolderName.contains('/') || newFolderName.contains('\\')
                fun submitNewFolder() {
                    val name = newFolderName.trim()
                    if (name.isEmpty() || badName || creatingFolder) return
                    creatingFolder = true
                    newFolderError = null
                    onCreateFolder(
                        name,
                        parent?.id,
                        { created ->
                            creatingFolder = false
                            newFolderName = ""
                            showNewFolder = false
                            query = ""
                            rootSelected = false
                            selectedId = created.id
                            // Su rama abierta, para verla elegida bajo su padre.
                            expanded = expanded + ancestorIds(byId, created.parentFolderId) +
                                listOfNotNull(created.parentFolderId) +
                                if (created.isShared) "grp:shared" else "grp:personal"
                        },
                        { message ->
                            creatingFolder = false
                            newFolderError = message
                        },
                    )
                }
                NewFolderRow(
                    open = showNewFolder,
                    where = if (parent != null) {
                        stringResource(Res.string.folder_picker_new_in, parent.name)
                    } else {
                        stringResource(Res.string.folder_picker_new_in_root)
                    },
                    name = newFolderName,
                    error = if (badName) stringResource(Res.string.folder_picker_new_slash) else newFolderError,
                    canCreate = newFolderName.isNotBlank() && !badName && !creatingFolder && !isSubmitting,
                    creating = creatingFolder,
                    onOpen = { showNewFolder = true },
                    onNameChange = { newFolderName = it; newFolderError = null },
                    onCreate = ::submitNewFolder,
                )
            }
            // Fuera del bloque elástico: un error dentro se recortaba junto al árbol.
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            if (showOrganizeByDate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubmitting) { organizeByYear = !organizeByYear }
                        .padding(vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = organizeByYear,
                        onCheckedChange = { organizeByYear = it },
                        enabled = !isSubmitting
                    )
                    Spacer(Modifier.width(Spacing.xs))
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
                        modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = Spacing.xxs),
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
            PrimaryActionButton(
                label = confirmLabel ?: stringResource(Res.string.action_move),
                onClick = {
                    onConfirm(
                        if (rootSelected) null else selectedId,
                        showOrganizeByDate && organizeByYear
                    )
                },
                enabled = canSubmit,
                // Crear la carpeta puede ocupar al mismo ViewModel que mueve
                // (mover carpetas en bloque): eso no es "moviendo".
                isLoading = isSubmitting && !creatingFolder
            )
        }
    }
}

/**
 * "Nueva carpeta": un botón que se despliega en un campo con el sitio donde
 * se creará (la carpeta elegida en el árbol), para no tener que salir a
 * Carpetas cuando el destino aún no existe.
 */
@Composable
private fun NewFolderRow(
    open: Boolean,
    where: String,
    name: String,
    error: String?,
    canCreate: Boolean,
    creating: Boolean,
    onOpen: () -> Unit,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    if (!open) {
        TextButton(onClick = onOpen) {
            Icon(PhotonneIcons.NewFolder, contentDescription = null)
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(Res.string.folder_action_new))
        }
        return
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            where,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text(stringResource(Res.string.folder_field_name)) },
                leadingIcon = { Icon(PhotonneIcons.NewFolder, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canCreate) onCreate() }),
                isError = error != null,
                enabled = !creating,
                singleLine = true,
            )
            if (creating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onCreate, enabled = canCreate) {
                    Text(stringResource(Res.string.action_create))
                }
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun RootPickerRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(top = Spacing.xxs, bottom = Spacing.xxs, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(Spacing.xxl))
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
