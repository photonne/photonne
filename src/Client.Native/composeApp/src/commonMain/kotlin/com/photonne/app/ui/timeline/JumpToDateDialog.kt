package com.photonne.app.ui.timeline

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_jump
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.CompactPrimaryButton
import com.photonne.app.ui.theme.CompactSecondaryButton

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
            CompactPrimaryButton(
                label = stringResource(Res.string.action_jump),
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(Instant.fromEpochMilliseconds(it)) }
                },
                enabled = canConfirm
            )
        },
        dismissButton = {
            CompactSecondaryButton(
                label = stringResource(Res.string.action_cancel),
                onClick = onDismiss
            )
        }
    ) {
        DatePicker(state = state)
    }
}
