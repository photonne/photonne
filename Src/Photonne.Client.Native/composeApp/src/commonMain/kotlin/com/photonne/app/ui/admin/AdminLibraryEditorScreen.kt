package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_create
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_libraries_action_delete
import com.photonne.app.resources.admin_libraries_cron_hint
import com.photonne.app.resources.admin_libraries_delete_message
import com.photonne.app.resources.admin_libraries_delete_title
import com.photonne.app.resources.admin_libraries_field_cron
import com.photonne.app.resources.admin_libraries_field_import_subfolders
import com.photonne.app.resources.admin_libraries_field_name
import com.photonne.app.resources.admin_libraries_field_path
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import org.jetbrains.compose.resources.stringResource

/**
 * Full-page editor used to create or edit an external library, replacing
 * the AlertDialog version. Delete confirmation remains an AlertDialog
 * since it is a one-shot destructive confirmation.
 */
@Composable
fun AdminLibraryEditorScreen(
    viewModel: AdminLibrariesViewModel,
    libraryId: String?,
    onDone: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureLoaded() }

    val existing = libraryId?.let { id -> state.libraries.firstOrNull { it.id == id } }
    val isEdit = libraryId != null

    if (isEdit && existing == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var path by remember(existing?.id) { mutableStateOf(existing?.path.orEmpty()) }
    var importSubfolders by remember(existing?.id) {
        mutableStateOf(existing?.importSubfolders ?: true)
    }
    var cron by remember(existing?.id) { mutableStateOf(existing?.cronSchedule.orEmpty()) }
    var showDelete by remember(existing?.id) { mutableStateOf(false) }

    val isSubmitting = state.isMutating
    val canSubmit = !isSubmitting && name.isNotBlank() && path.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp + floatingNavBarReservedHeight()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.admin_libraries_field_name)) },
            singleLine = true,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text(stringResource(Res.string.admin_libraries_field_path)) },
            singleLine = true,
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.admin_libraries_field_import_subfolders),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = importSubfolders,
                onCheckedChange = { importSubfolders = it },
                enabled = !isSubmitting
            )
        }
        OutlinedTextField(
            value = cron,
            onValueChange = { cron = it },
            label = { Text(stringResource(Res.string.admin_libraries_field_cron)) },
            singleLine = true,
            enabled = !isSubmitting,
            supportingText = {
                Text(stringResource(Res.string.admin_libraries_cron_hint))
            },
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.userMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(4.dp))

        Button(
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
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
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(
                stringResource(
                    if (isEdit) Res.string.action_save else Res.string.action_create
                )
            )
        }

        if (isEdit) {
            OutlinedButton(
                onClick = { showDelete = true },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(Res.string.admin_libraries_action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showDelete = false },
            title = { Text(stringResource(Res.string.admin_libraries_delete_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.admin_libraries_delete_message,
                        existing.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(existing.id) {
                            showDelete = false
                            onDone()
                        }
                    },
                    enabled = !isSubmitting
                ) {
                    Text(
                        stringResource(Res.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDelete = false },
                    enabled = !isSubmitting
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}
