package com.photonne.app.ui.folder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.folder_create_shared_hint
import com.photonne.app.resources.folder_create_shared_label
import com.photonne.app.resources.folder_delete_message
import com.photonne.app.resources.folder_delete_message_items
import com.photonne.app.resources.folder_delete_title
import com.photonne.app.resources.folder_field_name
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    isSubmitting: Boolean,
    errorMessage: String? = null,
    showSharedSpaceOption: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, isSharedSpace: Boolean) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var isSharedSpace by remember { mutableStateOf(false) }
    val canSubmit = name.trim().isNotEmpty() && !isSubmitting
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.folder_field_name)) },
                singleLine = true,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            )
            if (showSharedSpaceOption) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubmitting) { isSharedSpace = !isSharedSpace },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSharedSpace,
                        onCheckedChange = { isSharedSpace = it },
                        enabled = !isSubmitting
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(Res.string.folder_create_shared_label),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(Res.string.folder_create_shared_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(name.trim(), showSharedSpaceOption && isSharedSpace) },
                    enabled = canSubmit
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@Composable
fun DeleteFolderDialog(
    folderName: String,
    isSubmitting: Boolean,
    itemCount: Int = 0,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(Res.string.folder_delete_title)) },
        text = {
            Column {
                val message = if (itemCount > 0) {
                    pluralStringResource(Res.plurals.folder_delete_message_items, itemCount, folderName, itemCount)
                } else {
                    stringResource(Res.string.folder_delete_message, folderName)
                }
                Text(message)
                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSubmitting) {
                Text(
                    stringResource(Res.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
