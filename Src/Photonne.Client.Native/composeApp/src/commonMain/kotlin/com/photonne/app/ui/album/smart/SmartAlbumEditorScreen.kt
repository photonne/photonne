package com.photonne.app.ui.album.smart

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.photonne.app.data.api.rememberApiBaseUrl
import com.photonne.app.data.models.AlbumSummary
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_create
import com.photonne.app.resources.admin_settings_discard_confirm
import com.photonne.app.resources.admin_settings_discard_message
import com.photonne.app.resources.admin_settings_discard_title
import com.photonne.app.resources.smart_album_editor_title
import com.photonne.app.resources.smart_album_name_label
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.navigation.PlatformBackHandler
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.photonne.app.ui.theme.contentWidth
import com.photonne.app.ui.theme.Spacing

/**
 * "Nuevo álbum inteligente" — the dedicated rule editor
 * (docs/smart-albums/creation-ux.md). A name plus the shared
 * [RuleConditionsEditor] (Todas/Cualquiera toggle, condition chips, live preview
 * strip). Reuses the same resolver the saved album will use, so the preview
 * equals the real content.
 */
@Composable
fun SmartAlbumEditorScreen(
    onBack: () -> Unit,
    onCreated: (AlbumSummary) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    viewModel: SmartAlbumEditorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickers by viewModel.pickers.collectAsStateWithLifecycle()
    val baseUrl = rememberApiBaseUrl()
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val reservedTop = subscreenChromeReservedTop()

    // The VM is reused across entries (single ViewModelStoreOwner in the hand-rolled
    // nav), so start each "Nuevo álbum" from a blank slate instead of the last edit.
    LaunchedEffect(Unit) { viewModel.reset() }

    // Salir descartaba el borrador sin avisar: con nombre o condiciones puestos
    // hay trabajo que perder, así que atrás pasa por una confirmación.
    val isDirty = state.name.isNotBlank() || state.activeConditions.isNotEmpty()
    var confirmDiscard by remember { mutableStateOf(false) }
    val guardedBack = { if (isDirty) confirmDiscard = true else onBack() }
    PlatformBackHandler(enabled = isDirty) { confirmDiscard = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .hazeSource(hazeState)
                .contentWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = reservedTop,
                    bottom = floatingNavBarReservedHeight(),
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(Spacing.xs))

            // El error vivía al final, debajo del editor de condiciones: con la
            // lista crecida quedaba fuera de pantalla justo cuando importaba.
            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
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
                    imeAction = ImeAction.Done,
                ),
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

            Spacer(Modifier.height(Spacing.xl))
        }

        // Cromo flotante como el resto de subpantallas (la barra acoplada de
        // Material se comía el alto y no casaba con el andamio de la app).
        SubscreenFloatingChrome(
            title = stringResource(Res.string.smart_album_editor_title),
            onBack = guardedBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { if (scrollState.value > 0) 1 else 0 },
                firstVisibleItemScrollOffset = { scrollState.value },
                isScrollInProgress = { scrollState.isScrollInProgress },
                scrollToTopMinIndex = 1,
                onScrollToTop = { scrollState.animateScrollTo(0) },
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange,
            actions = {
                TextButton(
                    onClick = { viewModel.create(onCreated) },
                    enabled = state.canSave,
                ) {
                    // Crear resuelve reglas en el servidor y puede tardar: sin
                    // indicador el botón parecía no haber hecho nada.
                    if (state.isCreating) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(Res.string.action_create))
                }
            },
        )
    }

    if (confirmDiscard) {
        ConfirmActionDialog(
            title = stringResource(Res.string.admin_settings_discard_title),
            message = stringResource(Res.string.admin_settings_discard_message),
            confirmLabel = stringResource(Res.string.admin_settings_discard_confirm),
            isDestructive = true,
            isSubmitting = false,
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
        )
    }
}
