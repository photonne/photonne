package com.photonne.app.ui.admin

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.ui.theme.PrimaryActionButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_libraries_action_delete
import com.photonne.app.resources.admin_libraries_demo_locked
import com.photonne.app.resources.admin_libraries_cron_hint
import com.photonne.app.resources.admin_libraries_delete_message
import com.photonne.app.resources.admin_libraries_delete_title
import com.photonne.app.resources.admin_libraries_field_cron
import com.photonne.app.resources.admin_libraries_field_import_subfolders
import com.photonne.app.resources.admin_libraries_field_name
import com.photonne.app.resources.admin_libraries_field_path
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.resources.admin_libraries_not_found
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.photonne.app.ui.theme.DestructiveActionButton

/**
 * Full-page editor used to create or edit an external library, replacing
 * the AlertDialog version. Delete confirmation remains an AlertDialog
 * since it is a one-shot destructive confirmation.
 */
@Composable
fun AdminLibraryEditorScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminLibrariesViewModel,
    libraryId: String?,
    onDone: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val existing = libraryId?.let { id -> state.libraries.firstOrNull { it.id == id } }
    val isEdit = libraryId != null

    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var path by remember(existing?.id) { mutableStateOf(existing?.path.orEmpty()) }
    var importSubfolders by remember(existing?.id) {
        mutableStateOf(existing?.importSubfolders ?: true)
    }
    var cron by remember(existing?.id) { mutableStateOf(existing?.cronSchedule.orEmpty()) }
    var showDelete by remember(existing?.id) { mutableStateOf(false) }

    val isSubmitting = state.isMutating
    // Read-only in the demo: the server refuses to create, save or delete one.
    val isDemo = com.photonne.app.ui.demo.LocalDemoMode.current
    val canSubmit = !isSubmitting && !isDemo && name.isNotBlank() && path.isNotBlank()
    // Salir con cambios a medias pide confirmación, como el resto de formularios.
    val hasUnsavedChanges = !isDemo && if (existing != null) {
        name != existing.name ||
            path != existing.path ||
            importSubfolders != existing.importSubfolders ||
            cron != existing.cronSchedule.orEmpty()
    } else {
        !isEdit && listOf(name, path, cron).any { it.isNotBlank() }
    }

    AdminEditorScaffold(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        isResolving = isEdit && existing == null && state.isLoading,
        notFound = isEdit && existing == null && !state.isLoading,
        notFoundMessage = state.error?.userMessage ?: stringResource(Res.string.admin_libraries_not_found),
        onRetry = viewModel::refresh,
        resultMessage = state.statusMessage,
        onResultShown = viewModel::consumeStatus,
        hasUnsavedChanges = hasUnsavedChanges,
    ) {
        if (isDemo) AdminDemoNotice(stringResource(Res.string.admin_libraries_demo_locked))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.admin_libraries_field_name)) },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = com.photonne.app.ui.demo.demoRedacted(path),
            onValueChange = { path = it },
            label = { Text(stringResource(Res.string.admin_libraries_field_path)) },
            singleLine = true,
            // En la demo se ve enmascarada: editar la máscara no tiene sentido.
            enabled = !isSubmitting && !isDemo,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )
        SettingSwitch(
            label = stringResource(Res.string.admin_libraries_field_import_subfolders),
            checked = importSubfolders,
            enabled = !isSubmitting,
            onChange = { importSubfolders = it }
        )
        OutlinedTextField(
            value = cron,
            onValueChange = { cron = it },
            label = { Text(stringResource(Res.string.admin_libraries_field_cron)) },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            supportingText = {
                Text(stringResource(Res.string.admin_libraries_cron_hint))
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (!showDelete) ErrorBanner(error = state.error)

        PrimaryActionButton(
            label = stringResource(if (isEdit) Res.string.action_save else Res.string.action_create),
            enabled = canSubmit,
            isLoading = isSubmitting,
            onClick = {
                val cronValue = cron.takeIf { it.isNotBlank() }
                if (isEdit) {
                    viewModel.update(
                        id = existing!!.id,
                        name = name,
                        path = path,
                        importSubfolders = importSubfolders,
                        cronSchedule = cronValue,
                        onDone = onDone
                    )
                } else {
                    viewModel.create(
                        name = name,
                        path = path,
                        importSubfolders = importSubfolders,
                        cronSchedule = cronValue,
                        onDone = onDone
                    )
                }
            }
        )

        if (isEdit) {
            DestructiveActionButton(
                label = stringResource(Res.string.admin_libraries_action_delete),
                onClick = {
                    viewModel.clearMessages()
                    showDelete = true
                },
                enabled = !isSubmitting && !isDemo
            )
        }
    }

    if (showDelete && existing != null) {
        ConfirmActionDialog(
            title = stringResource(Res.string.admin_libraries_delete_title),
            message = stringResource(Res.string.admin_libraries_delete_message, existing.name),
            confirmLabel = stringResource(Res.string.action_delete),
            isDestructive = true,
            isSubmitting = isSubmitting,
            errorMessage = state.error?.userMessage,
            onDismiss = {
                showDelete = false
                viewModel.clearMessages()
            },
            onConfirm = {
                viewModel.delete(existing.id) {
                    showDelete = false
                    onDone()
                }
            }
        )
    }
}
