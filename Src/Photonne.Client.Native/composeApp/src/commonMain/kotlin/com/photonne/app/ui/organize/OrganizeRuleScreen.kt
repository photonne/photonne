package com.photonne.app.ui.organize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.FolderSummary
import com.photonne.app.ui.album.smart.RuleConditionsEditor
import com.photonne.app.ui.folder.FolderPickerDialog
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Para organizar → Mover por condiciones": build a condition rule (reusing the
 * smart-album condition builder), preview how many MobileBackup-pending assets
 * match, pick a destination folder, and file them all there in one shot. The
 * move is server-resolved and irreversible (physical file move), so it's gated
 * behind a confirmation.
 *
 * @param destinations writable move-destination folders (from the folders VM).
 * @param onMoved receives the moved count; the caller refreshes the inbox badge
 *   and navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeRuleScreen(
    destinations: List<FolderSummary>,
    onBack: () -> Unit,
    onMoved: (Int) -> Unit,
    viewModel: OrganizeRuleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val pickers by viewModel.pickers.collectAsState()
    val baseUrl = rememberApiBaseUrl()

    var showFolderPicker by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    // The VM instance is reused across navigations; start blank each time.
    LaunchedEffect(Unit) { viewModel.reset() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mover por condiciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
            )
        },
        bottomBar = {
            MoveBar(
                count = state.previewCount,
                path = state.targetFolderPath,
                enabled = state.canMove,
                isMoving = state.isMoving,
                onMove = { showConfirm = true },
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

            Text(
                "Mueve de golpe todo lo que sigue en \"Para organizar\" y cumpla estas condiciones.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RuleConditionsEditor(
                conditions = state.conditions,
                matchAll = state.matchAll,
                pickers = pickers,
                baseUrl = baseUrl,
                previewCount = state.previewCount,
                previewSampleIds = state.previewSampleIds,
                isPreviewing = state.isPreviewing,
                onSetMatchAll = viewModel::setMatchAll,
                onUpsertCondition = viewModel::upsertCondition,
                onRemoveCondition = viewModel::removeCondition,
                onPeopleQuery = viewModel::setPeopleQuery,
                onSceneQuery = viewModel::setSceneQuery,
                onObjectQuery = viewModel::setObjectQuery,
                onEnsureFolders = viewModel::ensureFolders,
            )

            DestinationRow(
                path = state.targetFolderPath,
                onClick = { showFolderPicker = true },
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

    if (showFolderPicker) {
        FolderPickerDialog(
            title = "Mover a",
            folders = destinations,
            isSubmitting = false,
            includeRoot = false,
            initialSelectionId = state.targetFolderId,
            onDismiss = { showFolderPicker = false },
            onConfirm = { targetFolderId ->
                showFolderPicker = false
                targetFolderId?.let { id ->
                    destinations.firstOrNull { it.id == id }?.let { folder ->
                        viewModel.setTarget(folder.id, folder.path)
                    }
                }
            },
        )
    }

    if (showConfirm) {
        val count = state.previewCount ?: 0
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Mover $count fotos") },
            text = {
                Text("Se moverán $count fotos a ${prettyPath(state.targetFolderPath)}. Los archivos se mueven de sitio.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.move(onMoved)
                }) { Text("Mover") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun DestinationRow(path: String?, onClick: () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Mover a", style = MaterialTheme.typography.titleSmall)
                Text(
                    path?.let { prettyPath(it) } ?: "Elige una carpeta destino",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MoveBar(
    count: Int?,
    path: String?,
    enabled: Boolean,
    isMoving: Boolean,
    onMove: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onMove,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isMoving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    val n = count ?: 0
                    Text(
                        when {
                            path == null -> "Elige un destino"
                            n == 0 -> "Sin coincidencias"
                            else -> "Mover $n fotos"
                        }
                    )
                }
            }
        }
    }
}

/** Trims the internal "/assets/users/{username}" prefix so the destination reads
 *  as the user sees their library ("/Familia/2026"), falling back to the raw path. */
private fun prettyPath(path: String?): String {
    if (path == null) return ""
    val marker = "/assets/users/"
    val idx = path.indexOf(marker)
    if (idx < 0) return path
    val afterUser = path.substring(idx + marker.length).substringAfter('/', "")
    return if (afterUser.isBlank()) path else "/$afterUser"
}
