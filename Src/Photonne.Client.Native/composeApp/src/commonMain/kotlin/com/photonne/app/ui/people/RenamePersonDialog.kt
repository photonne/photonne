package com.photonne.app.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_save
import com.photonne.app.resources.people_rename_field
import com.photonne.app.resources.people_rename_title
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.PrimaryActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenamePersonDialog(
    initialName: String?,
    isSubmitting: Boolean,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String?) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName.orEmpty()) }
    val nameFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { nameFocus.requestFocus() }
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
            Text(
                stringResource(Res.string.people_rename_title),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.people_rename_field)) },
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (!isSubmitting) {
                        onConfirm(name.trim().takeIf { it.isNotEmpty() })
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
            )
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))
            PrimaryActionButton(
                label = stringResource(Res.string.action_save),
                onClick = { onConfirm(name.trim().takeIf { it.isNotEmpty() }) },
                isLoading = isSubmitting
            )
        }
    }
}
