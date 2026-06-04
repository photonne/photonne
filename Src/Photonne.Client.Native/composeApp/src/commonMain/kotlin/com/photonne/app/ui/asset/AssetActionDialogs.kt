package com.photonne.app.ui.asset

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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.models.CaptureDateSuggestion
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_delete
import com.photonne.app.resources.action_save
import com.photonne.app.resources.asset_date_dialog_title
import com.photonne.app.resources.asset_date_readonly_note
import com.photonne.app.resources.asset_date_suggestion_button
import com.photonne.app.resources.asset_date_suggestion_exif
import com.photonne.app.resources.asset_date_suggestion_file
import com.photonne.app.resources.asset_date_suggestion_filename
import com.photonne.app.resources.asset_date_suggestion_folder
import com.photonne.app.resources.asset_date_suggestion_loading
import com.photonne.app.resources.asset_date_suggestion_none
import com.photonne.app.resources.asset_date_suggestion_use
import com.photonne.app.resources.asset_date_write_to_file
import com.photonne.app.resources.asset_date_write_to_file_caption
import com.photonne.app.resources.asset_description_dialog_title
import com.photonne.app.resources.asset_description_field
import com.photonne.app.resources.asset_trash_message
import com.photonne.app.resources.asset_trash_title
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditDescriptionDialog(
    initialDescription: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var value by remember(initialDescription) { mutableStateOf(initialDescription.orEmpty()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.asset_description_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(Res.string.asset_description_field)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onConfirm(value.trim().takeIf { it.isNotEmpty() }) }) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        }
    }
}

/**
 * Lets the user override an asset's capture date. The picked date+time is
 * interpreted as UTC (consistent with the rest of the date pickers in the app)
 * and sent as an absolute instant; the server stores UTC and, when
 * [writeToFile] is on, writes it back into the file's EXIF.
 *
 * [writeToFile] is forced off and the toggle disabled for read-only assets
 * (external libraries), where the file can't be modified.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCaptureDateDialog(
    initialDate: Instant,
    isReadOnly: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Instant, Boolean) -> Unit,
    onRequestSuggestion: (suspend () -> CaptureDateSuggestion?)? = null
) {
    val initialLdt = remember(initialDate) { initialDate.toLocalDateTime(TimeZone.UTC) }
    // The selected day lives in its own state so the sheet stays compact: the
    // full DatePicker only appears on demand inside a DatePickerDialog —
    // inlined it ate the whole sheet height and pushed Save off-screen.
    var selectedDate by remember(initialDate) { mutableStateOf(initialLdt.date) }
    var showDatePicker by remember { mutableStateOf(false) }
    // Time defaults to noon — when fixing a wrong date, the stored time is
    // meaningless anyway (it matches the bulk-inference convention). The user
    // can still tap the compact time chip to set an exact one; "use
    // suggestion" overwrites it with the candidate's time.
    var timeSeed by remember(initialDate) { mutableStateOf(12 to 0) }
    var showTimePicker by remember { mutableStateOf(false) }
    var writeToFile by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var suggestionLoading by remember { mutableStateOf(false) }
    var suggestionRequested by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<CaptureDateSuggestion?>(null) }

    fun applyCandidate(instant: Instant) {
        val ldt = instant.toLocalDateTime(TimeZone.UTC)
        selectedDate = ldt.date
        timeSeed = ldt.hour to ldt.minute
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(Res.string.asset_date_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )

            // Date + time on one row, each opening its picker in a dialog.
            val dateLabel = selectedDate.dayOfMonth.toString().padStart(2, '0') + "/" +
                selectedDate.monthNumber.toString().padStart(2, '0') + "/" +
                selectedDate.year
            val timeLabel = timeSeed.first.toString().padStart(2, '0') + ":" +
                timeSeed.second.toString().padStart(2, '0')
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(dateLabel, style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text(timeLabel, style = MaterialTheme.typography.bodyLarge)
                }
            }

            // "What would the server recover?" — per-asset preview of the EXIF
            // re-read and the name/folder inference; tapping a candidate fills
            // the pickers, the user still confirms with Save.
            if (onRequestSuggestion != null) {
                TextButton(
                    enabled = !suggestionLoading,
                    onClick = {
                        scope.launch {
                            suggestionLoading = true
                            suggestion = onRequestSuggestion()
                            suggestionRequested = true
                            suggestionLoading = false
                        }
                    }
                ) {
                    Text(
                        if (suggestionLoading) stringResource(Res.string.asset_date_suggestion_loading)
                        else stringResource(Res.string.asset_date_suggestion_button)
                    )
                }

                suggestion?.let { s ->
                    s.exifDate?.let { exif ->
                        SuggestionRow(
                            label = stringResource(Res.string.asset_date_suggestion_exif),
                            instant = exif,
                            onUse = { applyCandidate(exif) }
                        )
                    }
                    s.fileDate?.let { fileDate ->
                        SuggestionRow(
                            label = stringResource(Res.string.asset_date_suggestion_file),
                            instant = fileDate,
                            onUse = { applyCandidate(fileDate) }
                        )
                    }
                    s.inferredDate?.let { inferred ->
                        SuggestionRow(
                            label = if (s.inferredOrigin == "FileName")
                                stringResource(Res.string.asset_date_suggestion_filename)
                            else stringResource(Res.string.asset_date_suggestion_folder),
                            instant = inferred,
                            onUse = { applyCandidate(inferred) }
                        )
                    }
                }
                if (suggestionRequested && !suggestionLoading &&
                    suggestion?.exifDate == null && suggestion?.inferredDate == null &&
                    suggestion?.fileDate == null
                ) {
                    Text(
                        stringResource(Res.string.asset_date_suggestion_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.asset_date_write_to_file),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(Res.string.asset_date_write_to_file_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = writeToFile && !isReadOnly,
                    enabled = !isReadOnly,
                    onCheckedChange = { writeToFile = it }
                )
            }
            if (isReadOnly) {
                Text(
                    stringResource(Res.string.asset_date_readonly_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val combined = LocalDateTime(
                        selectedDate.year, selectedDate.monthNumber, selectedDate.dayOfMonth,
                        timeSeed.first, timeSeed.second, 0
                    ).toInstant(TimeZone.UTC)
                    onConfirm(combined, writeToFile && !isReadOnly)
                }) {
                    Text(stringResource(Res.string.action_save))
                }
            }
        }
    }

    if (showDatePicker) {
        // Anchor the picker at noon UTC of the current selection so the
        // epoch→date round-trip can't drift a day across timezones.
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDateTime(
                selectedDate.year, selectedDate.monthNumber, selectedDate.dayOfMonth, 12, 0, 0
            ).toInstant(TimeZone.UTC).toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.UTC).date
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(Res.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = dateState, title = null, showModeToggle = false)
        }
    }

    if (showTimePicker) {
        val dialogTimeState = remember {
            TimePickerState(initialHour = timeSeed.first, initialMinute = timeSeed.second, is24Hour = true)
        }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimeInput(state = dialogTimeState) },
            confirmButton = {
                TextButton(onClick = {
                    timeSeed = dialogTimeState.hour to dialogTimeState.minute
                    showTimePicker = false
                }) {
                    Text(stringResource(Res.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

/** One recoverable-date candidate: source label + formatted date + "use". */
@Composable
private fun SuggestionRow(label: String, instant: Instant, onUse: () -> Unit) {
    val ldt = instant.toLocalDateTime(TimeZone.UTC)
    val formatted = ldt.dayOfMonth.toString().padStart(2, '0') + "/" +
        ldt.monthNumber.toString().padStart(2, '0') + "/" + ldt.year + " " +
        ldt.hour.toString().padStart(2, '0') + ":" + ldt.minute.toString().padStart(2, '0')
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(formatted, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onUse) {
            Text(stringResource(Res.string.asset_date_suggestion_use))
        }
    }
}

@Composable
fun TrashAssetDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.asset_trash_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.asset_trash_message, fileName))
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(Res.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        }
    )
}
