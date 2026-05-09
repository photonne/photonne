package com.photonne.app.ui.timeline

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_jump
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpToDateDialog(
    onDismiss: () -> Unit,
    onConfirm: (Instant) -> Unit
) {
    val state = rememberDatePickerState()
    val canConfirm by androidx.compose.runtime.derivedStateOf { state.selectedDateMillis != null }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(Instant.fromEpochMilliseconds(it)) }
                },
                enabled = canConfirm
            ) { Text(stringResource(Res.string.action_jump)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        }
    ) {
        DatePicker(state = state)
    }
}
