package com.photonne.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_accept
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_clear
import com.photonne.app.resources.people_unnamed
import com.photonne.app.resources.search_clear_all
import com.photonne.app.resources.search_date_from
import com.photonne.app.resources.search_date_range
import com.photonne.app.resources.search_date_to
import com.photonne.app.resources.search_filters
import com.photonne.app.resources.search_filters_loading
import com.photonne.app.resources.search_objects_count
import com.photonne.app.resources.search_ocr_hint
import com.photonne.app.resources.search_ocr_title
import com.photonne.app.resources.search_people_count
import com.photonne.app.resources.search_scenes_count
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.photonne.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFiltersSheet(
    state: SearchUiState,
    onDismiss: () -> Unit,
    onDateRangeChange: (LocalDate?, LocalDate?) -> Unit,
    onOcrChange: (String) -> Unit,
    onToggleObject: (String) -> Unit,
    onToggleScene: (String) -> Unit,
    onTogglePerson: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var datePickerFor by remember { mutableStateOf<DateField?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.search_filters),
                    style = MaterialTheme.typography.titleLarge
                )
                // Arriba y como acción de texto: al fondo, como chip, parecía
                // un filtro más y con listas largas ni se veía.
                TextButton(onClick = onClearAll) {
                    Text(stringResource(Res.string.search_clear_all))
                }
            }

            // OCR (text inside images)
            Text(
                stringResource(Res.string.search_ocr_title),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = state.ocrText,
                onValueChange = onOcrChange,
                placeholder = { Text(stringResource(Res.string.search_ocr_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
            )

            // Date range
            Text(
                stringResource(Res.string.search_date_range),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = { datePickerFor = DateField.From },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        state.from?.let { formatFilterDate(it) }
                            ?: stringResource(Res.string.search_date_from)
                    )
                }
                OutlinedButton(
                    onClick = { datePickerFor = DateField.To },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        state.to?.let { formatFilterDate(it) }
                            ?: stringResource(Res.string.search_date_to)
                    )
                }
                if (state.from != null || state.to != null) {
                    TextButton(onClick = { onDateRangeChange(null, null) }) {
                        Text(stringResource(Res.string.action_clear))
                    }
                }
            }

            // People
            Text(
                stringResource(Res.string.search_people_count, state.people.size),
                style = MaterialTheme.typography.titleMedium
            )
            if (state.facetsLoading && state.people.isEmpty()) {
                Text(
                    stringResource(Res.string.search_filters_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    // El tope evita que una lista larga se coma la hoja, y el
                    // scroll propio hace alcanzable lo que queda por debajo.
                    modifier = Modifier
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (person in state.people) {
                        FilterChip(
                            selected = person.id in state.selectedPersonIds,
                            onClick = { onTogglePerson(person.id) },
                            label = {
                                Text(
                                    person.name
                                        ?: stringResource(Res.string.people_unnamed),
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            // Objects
            Text(
                stringResource(Res.string.search_objects_count, state.objectLabels.size),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (label in state.objectLabels) {
                    FilterChip(
                        selected = label.label in state.selectedObjectLabels,
                        onClick = { onToggleObject(label.label) },
                        label = {
                            Text(
                                "${label.label} (${label.assetCount})",
                                maxLines = 1
                            )
                        }
                    )
                }
            }

            // Scenes
            Text(
                stringResource(Res.string.search_scenes_count, state.sceneLabels.size),
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (label in state.sceneLabels) {
                    FilterChip(
                        selected = label.label in state.selectedSceneLabels,
                        onClick = { onToggleScene(label.label) },
                        label = {
                            Text(
                                "${label.label} (${label.assetCount})",
                                maxLines = 1
                            )
                        }
                    )
                }
            }


        }
    }

    when (datePickerFor) {
        DateField.From -> DateFieldPicker(
            initial = state.from,
            onPick = { picked ->
                datePickerFor = null
                if (picked != null) onDateRangeChange(picked, state.to)
            }
        )
        DateField.To -> DateFieldPicker(
            initial = state.to,
            onPick = { picked ->
                datePickerFor = null
                if (picked != null) onDateRangeChange(state.from, picked)
            }
        )
        null -> Unit
    }
}

private enum class DateField { From, To }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldPicker(
    initial: LocalDate?,
    onPick: (LocalDate?) -> Unit
) {
    val initialMillis = initial?.let {
        // Treat the LocalDate as midnight UTC so the picker shows the same day
        // the user previously selected, independent of their device timezone.
        val tz = TimeZone.UTC
        val days = it.toEpochDays()
        days.toLong() * 86_400_000L
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = { onPick(null) },
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val date = Instant.fromEpochMilliseconds(millis)
                        .toLocalDateTime(TimeZone.UTC).date
                    onPick(date)
                } else {
                    onPick(null)
                }
            }) { Text(stringResource(Res.string.action_accept)) }
        },
        dismissButton = {
            TextButton(onClick = { onPick(null) }) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    ) {
        DatePicker(state = state)
    }
}

/** Fecha corta legible ("12 mar 2024" según la plataforma), no ISO crudo. */
private fun formatFilterDate(date: LocalDate): String =
    com.photonne.app.ui.settings.formatProfileDate(
        date.atStartOfDayIn(TimeZone.UTC)
    )
