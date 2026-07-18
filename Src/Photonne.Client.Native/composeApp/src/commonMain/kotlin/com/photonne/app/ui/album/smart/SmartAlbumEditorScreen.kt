package com.photonne.app.ui.album.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_close
import com.photonne.app.resources.action_create
import com.photonne.app.resources.smart_album_editor_title
import com.photonne.app.resources.smart_album_name_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Nuevo álbum inteligente" — the dedicated rule editor
 * (docs/smart-albums/creation-ux.md). A name plus the shared
 * [RuleConditionsEditor] (Todas/Cualquiera toggle, condition chips, live preview
 * strip). Reuses the same resolver the saved album will use, so the preview
 * equals the real content.
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

    // The VM is reused across entries (single ViewModelStoreOwner in the hand-rolled
    // nav), so start each "Nuevo álbum" from a blank slate instead of the last edit.
    LaunchedEffect(Unit) { viewModel.reset() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.smart_album_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.action_close)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.create(onCreated) },
                        enabled = state.canSave,
                    ) { Text(stringResource(Res.string.action_create)) }
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
                label = { Text(stringResource(Res.string.smart_album_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
}
