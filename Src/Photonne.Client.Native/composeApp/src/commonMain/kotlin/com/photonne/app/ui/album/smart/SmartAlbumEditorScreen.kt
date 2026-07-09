package com.photonne.app.ui.album.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
                TextButton(onClick = {
                    viewModel.ensurePickerData()
                    sheet = EditorSheet.Menu
                }) {
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
    when (sheet) {
        EditorSheet.Menu -> AddConditionMenuSheet(
            onDismiss = { sheet = null },
            onPick = { sheet = it },
        )
        EditorSheet.People -> PeopleSelectSheet(
            all = pickers.people,
            baseUrl = baseUrl,
            existing = state.conditions.filterIsInstance<SmartCondition.People>().firstOrNull(),
            isLoading = pickers.isLoading,
            onDismiss = { sheet = null },
            onConfirm = { viewModel.upsertCondition(it); sheet = null },
        )
        EditorSheet.Folders -> FolderSelectSheet(
            all = pickers.folders,
            existing = state.conditions.filterIsInstance<SmartCondition.Folders>().firstOrNull(),
            isLoading = pickers.isLoading,
            onDismiss = { sheet = null },
            onConfirm = { viewModel.upsertCondition(it); sheet = null },
        )
        EditorSheet.Scenes -> LabelSelectSheet(
            title = "Escenas",
            all = pickers.sceneLabels,
            selected = state.conditions.filterIsInstance<SmartCondition.Scenes>().firstOrNull()?.labels ?: emptyList(),
            isLoading = pickers.isLoading,
            onDismiss = { sheet = null },
            onConfirm = { viewModel.upsertCondition(SmartCondition.Scenes(it)); sheet = null },
        )
        EditorSheet.Objects -> LabelSelectSheet(
            title = "Objetos",
            all = pickers.objectLabels,
            selected = state.conditions.filterIsInstance<SmartCondition.Objects>().firstOrNull()?.labels ?: emptyList(),
            isLoading = pickers.isLoading,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PeopleSelectSheet(
    all: List<PersonRef>,
    baseUrl: String,
    existing: SmartCondition.People?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (SmartCondition.People) -> Unit,
) {
    var selected by remember { mutableStateOf(existing?.people?.map { it.id }?.toSet() ?: emptySet()) }
    var matchAll by remember { mutableStateOf(existing?.matchAll ?: false) }

    SelectSheetScaffold(
        title = "Personas",
        isLoading = isLoading,
        isEmpty = all.isEmpty(),
        emptyText = "No hay personas con nombre todavía.",
        onDismiss = onDismiss,
        onConfirm = {
            val picked = all.filter { it.id in selected }
            onConfirm(SmartCondition.People(people = picked, matchAll = matchAll))
        },
        confirmEnabled = selected.isNotEmpty(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            FilterChip(selected = !matchAll, onClick = { matchAll = false }, label = { Text("Cualquiera") })
            FilterChip(selected = matchAll, onClick = { matchAll = true }, label = { Text("Todas juntas") })
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { person ->
                val isSel = person.id in selected
                FilterChip(
                    selected = isSel,
                    onClick = { selected = if (isSel) selected - person.id else selected + person.id },
                    label = { Text(person.name) },
                    leadingIcon = {
                        person.coverFaceId?.let { faceId ->
                            AsyncImage(
                                model = "$baseUrl/api/faces/$faceId/thumbnail",
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(24.dp).clip(CircleShape),
                            )
                        }
                    },
                )
            }
        }
    }
}

// ── Folder select ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FolderSelectSheet(
    all: List<FolderRef>,
    existing: SmartCondition.Folders?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (SmartCondition.Folders) -> Unit,
) {
    var selected by remember { mutableStateOf(existing?.folders?.map { it.id }?.toSet() ?: emptySet()) }

    SelectSheetScaffold(
        title = "Carpetas",
        isLoading = isLoading,
        isEmpty = all.isEmpty(),
        emptyText = "No hay carpetas disponibles.",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(SmartCondition.Folders(all.filter { it.id in selected })) },
        confirmEnabled = selected.isNotEmpty(),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { folder ->
                val isSel = folder.id in selected
                FilterChip(
                    selected = isSel,
                    onClick = { selected = if (isSel) selected - folder.id else selected + folder.id },
                    label = { Text(if (folder.isShared) "${folder.name} · compartida" else folder.name) },
                )
            }
        }
    }
}

// ── Label select (scenes / objects) ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LabelSelectSheet(
    title: String,
    all: List<String>,
    selected: List<String>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var picked by remember { mutableStateOf(selected.toSet()) }
    SelectSheetScaffold(
        title = title,
        isLoading = isLoading,
        isEmpty = all.isEmpty(),
        emptyText = "No hay etiquetas disponibles.",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(picked.toList()) },
        confirmEnabled = picked.isNotEmpty(),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { label ->
                val isSel = label in picked
                FilterChip(
                    selected = isSel,
                    onClick = { picked = if (isSel) picked - label else picked + label },
                    label = { Text(label) },
                )
            }
        }
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
