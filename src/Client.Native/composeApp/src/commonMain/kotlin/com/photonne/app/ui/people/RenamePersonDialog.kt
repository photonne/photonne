package com.photonne.app.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_save
import com.photonne.app.data.models.Person
import com.photonne.app.resources.people_rename_field
import com.photonne.app.resources.people_rename_keep_apart
import com.photonne.app.resources.people_rename_merge
import com.photonne.app.resources.people_rename_namesake
import com.photonne.app.resources.people_rename_title
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.CompactPrimaryButton
import com.photonne.app.ui.theme.CompactSecondaryButton
import com.photonne.app.ui.theme.SheetActions
import com.photonne.app.resources.action_cancel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenamePersonDialog(
    initialName: String?,
    isSubmitting: Boolean,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String?) -> Unit,
    /**
     * Someone else already called that, if anyone. When there is, saving asks
     * first whether it's the same person and offers [onMerge] instead.
     */
    findNamesake: (suspend (name: String) -> Person?)? = null,
    /** Joins this person into [Person], who already has the name. */
    onMerge: (Person) -> Unit = {}
) {
    var name by remember(initialName) { mutableStateOf(initialName.orEmpty()) }
    var existing by remember { mutableStateOf<Person?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busy = isSubmitting || checking

    fun save() {
        if (busy) return
        val wanted = name.trim()
        val lookup = findNamesake
        if (lookup == null || wanted.isEmpty() || wanted == initialName.orEmpty().trim()) {
            onConfirm(wanted.takeIf { it.isNotEmpty() })
            return
        }
        checking = true
        scope.launch {
            // A failed lookup just renames: the check is a courtesy.
            val match = runCatching { lookup(wanted) }.getOrNull()
            checking = false
            if (match != null) existing = match else onConfirm(wanted)
        }
    }
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
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            SheetHeader(stringResource(Res.string.people_rename_title))
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    // Another name asks again.
                    existing = null
                },
                label = { Text(stringResource(Res.string.people_rename_field)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
            )
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
            val namesake = existing
            if (namesake != null) {
                Text(
                    stringResource(Res.string.people_rename_namesake, namesake.name.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            SheetActions {
                if (namesake != null) {
                    CompactSecondaryButton(
                        label = stringResource(Res.string.people_rename_keep_apart),
                        onClick = { onConfirm(name.trim().takeIf { it.isNotEmpty() }) },
                        enabled = !busy
                    )
                    CompactPrimaryButton(
                        label = stringResource(Res.string.people_rename_merge),
                        onClick = { onMerge(namesake) },
                        isLoading = busy
                    )
                } else {
                    CompactSecondaryButton(
                        label = stringResource(Res.string.action_cancel),
                        onClick = onDismiss,
                        enabled = !busy
                    )
                    CompactPrimaryButton(
                        label = stringResource(Res.string.action_save),
                        onClick = { save() },
                        isLoading = busy
                    )
                }
            }
        }
    }
}
