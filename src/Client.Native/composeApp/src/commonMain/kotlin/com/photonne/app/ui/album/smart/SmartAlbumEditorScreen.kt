package com.photonne.app.ui.album.smart

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_save
import com.photonne.app.resources.smart_album_description_label
import com.photonne.app.resources.smart_album_edit_title
import com.photonne.app.resources.smart_album_editor_title
import com.photonne.app.resources.smart_album_preserved_note
import com.photonne.app.resources.smart_album_unknown_ref
import com.photonne.app.resources.smart_album_name_label
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.main.FormPageScaffold
import com.photonne.app.ui.theme.PrimaryActionButton

/**
 * "Nuevo álbum inteligente" / "Editar álbum inteligente" — the dedicated rule
 * editor (docs/smart-albums/creation-ux.md). With [editAlbum] it opens on that
 * album's stored rule and saves over it. A name plus the shared
 * [RuleConditionsEditor] (Todas/Cualquiera toggle, condition chips, live preview
 * strip). Reuses the same resolver the saved album will use, so the preview
 * equals the real content.
 */
@Composable
fun SmartAlbumEditorScreen(
    onBack: () -> Unit,
    onSaved: (AlbumSummary) -> Unit,
    editAlbum: AlbumSummary? = null,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    viewModel: SmartAlbumEditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickers by viewModel.pickers.collectAsStateWithLifecycle()
    val baseUrl = rememberApiBaseUrl()

    // The VM is reused across entries (single ViewModelStoreOwner in the hand-rolled
    // nav), so start each "Nuevo álbum" from a blank slate instead of the last edit,
    // and each edit from the album's stored rule.
    val unknownName = stringResource(Res.string.smart_album_unknown_ref)
    LaunchedEffect(editAlbum?.id) {
        if (editAlbum != null) viewModel.loadForEdit(editAlbum, unknownName) else viewModel.reset()
    }

    // Salir descartaba el borrador sin avisar: con nombre o condiciones puestos
    // (o, al editar, cambios sobre lo cargado) hay trabajo que perder, así que
    // atrás pasa por la confirmación del andamio.
    val isDirty = viewModel.isDirty(state)

    // El andamio de todos los formularios: antes el editor pegaba el suyo, con
    // "Crear/Guardar" como texto en el cromo (el único de la app; el resto de
    // pantallas guarda con el botón principal al final).
    FormPageScaffold(
        title = stringResource(
            if (editAlbum != null) Res.string.smart_album_edit_title else Res.string.smart_album_editor_title
        ),
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        hasUnsavedChanges = isDirty,
    ) { page ->
        page {
            // El error vivía al final, debajo del editor de condiciones: con la
            // lista crecida quedaba fuera de pantalla justo cuando importaba.
            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        err.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(Res.string.smart_album_name_label)) },
                singleLine = true,
                enabled = !state.isCreating,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text(stringResource(Res.string.smart_album_description_label)) },
                minLines = 2,
                enabled = !state.isCreating,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isLoadingRule) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (!state.ruleLoadFailed) {
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

                if (state.preserved.isNotEmpty()) {
                    Text(
                        pluralStringResource(
                            Res.plurals.smart_album_preserved_note,
                            state.preserved.size,
                            state.preserved.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xs))
            // Crear resuelve reglas en el servidor y puede tardar: el spinner
            // ocupa el sitio de la etiqueta mientras tanto. Sin cambios sobre lo
            // cargado no hay nada que guardar.
            PrimaryActionButton(
                label = stringResource(if (editAlbum != null) Res.string.action_save else Res.string.action_create),
                onClick = { viewModel.save(onSaved) },
                enabled = state.canSave && isDirty,
                isLoading = state.isCreating,
            )
        }
    }
}
