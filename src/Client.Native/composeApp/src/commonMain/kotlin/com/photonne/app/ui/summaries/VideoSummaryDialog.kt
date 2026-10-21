package com.photonne.app.ui.summaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.resources.Res
import com.photonne.app.resources.summaries_auto_year
import com.photonne.app.resources.summaries_auto_year_hint
import com.photonne.app.resources.summaries_close
import com.photonne.app.resources.summaries_create
import com.photonne.app.resources.summaries_duration
import com.photonne.app.resources.summaries_duration_option
import com.photonne.app.resources.summaries_failed
import com.photonne.app.resources.summaries_failed_unknown
import com.photonne.app.resources.summaries_hint
import com.photonne.app.resources.summaries_include_photos
import com.photonne.app.resources.summaries_include_photos_hint
import com.photonne.app.resources.summaries_music
import com.photonne.app.resources.summaries_music_remove
import com.photonne.app.resources.summaries_music_web_hint
import com.photonne.app.resources.summaries_no_years
import com.photonne.app.resources.summaries_orientation
import com.photonne.app.resources.summaries_orientation_all
import com.photonne.app.resources.summaries_orientation_all_hint
import com.photonne.app.resources.summaries_orientation_landscape
import com.photonne.app.resources.summaries_orientation_landscape_hint
import com.photonne.app.resources.summaries_orientation_portrait
import com.photonne.app.resources.summaries_orientation_portrait_hint
import com.photonne.app.resources.summaries_pending
import com.photonne.app.resources.summaries_planned
import com.photonne.app.resources.summaries_ready
import com.photonne.app.resources.summaries_regenerate
import com.photonne.app.resources.summaries_regenerate_hint
import com.photonne.app.resources.summaries_render_as_is
import com.photonne.app.resources.summaries_requested
import com.photonne.app.resources.summaries_review
import com.photonne.app.resources.summaries_review_hint
import com.photonne.app.resources.summaries_running
import com.photonne.app.resources.summaries_title_album
import com.photonne.app.resources.summaries_title_year
import com.photonne.app.resources.summaries_titles
import com.photonne.app.resources.summaries_titles_hint
import com.photonne.app.resources.summaries_year
import com.photonne.app.resources.summaries_year_option
import com.photonne.app.ui.main.LocalSnackbarController
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.util.SegmentOption
import com.photonne.app.ui.util.SegmentedChoiceRow
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import com.photonne.app.ui.theme.SheetHeader
import com.photonne.app.ui.theme.CompactPrimaryButton
import com.photonne.app.ui.theme.CompactSecondaryButton
import com.photonne.app.ui.theme.SheetActions

/**
 * The summary video dialog: the same options as on the web. [album] null asks
 * for the summary of a year. Reviewing the clips one by one happens on the web;
 * here a "planned" summary can be rendered as the server chose it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSummaryDialog(
    album: SummarySource.Album?,
    onDismiss: () -> Unit,
    viewModel: VideoSummaryViewModel = koinViewModel(),
) {
    LaunchedEffect(album) { viewModel.open(album) }
    DisposableEffect(Unit) { onDispose { viewModel.close() } }
    val state by viewModel.state.collectAsStateWithLifecycle()

    val snackbar = LocalSnackbarController.current
    val requestedMessage = state.requested?.let { stringResource(Res.string.summaries_requested, it) }
    LaunchedEffect(requestedMessage) {
        if (requestedMessage != null) {
            snackbar?.show(requestedMessage)
            viewModel.requestedShown()
        }
    }

    val current = state.current
    val done = current != null && current.status in setOf("completed", "failed", "planned")

    // Hoja, como el resto de formularios con opciones: antes era un AlertDialog
    // con scroll propio y dos TextButton.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SheetHeader(
                stringResource(
                    if (album == null) Res.string.summaries_title_year else Res.string.summaries_title_album
                )
            )
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Hint(stringResource(Res.string.summaries_hint))

                if (state.isLoading) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                    return@Column
                }

                if (state.pickYear) {
                    if (state.noYears) {
                        Hint(stringResource(Res.string.summaries_no_years))
                    } else {
                        val selected = (state.source as? SummarySource.Year)?.year
                        val label: @Composable (Int) -> String = { year ->
                            val videos = state.years.firstOrNull { it.year == year }?.videos ?: 0
                            pluralStringResource(Res.plurals.summaries_year_option, videos, year.toString(), videos)
                        }
                        Picker(
                            title = stringResource(Res.string.summaries_year),
                            value = selected?.let { label(it) }.orEmpty(),
                            options = state.years.map { it.year },
                            optionLabel = label,
                            enabled = !state.isSubmitting,
                            onSelect = viewModel::selectYear,
                        )
                    }
                }

                val options = state.options
                val editable = !state.active && !state.isSubmitting
                val minutes: @Composable (Int) -> String = {
                    pluralStringResource(Res.plurals.summaries_duration_option, it, it)
                }
                Picker(
                    title = stringResource(Res.string.summaries_duration),
                    value = minutes(options.durationMinutes),
                    options = SummaryOptions.DURATIONS,
                    optionLabel = minutes,
                    enabled = editable,
                    onSelect = { value -> viewModel.updateOptions { it.copy(durationMinutes = value) } },
                )

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(Res.string.summaries_orientation), style = MaterialTheme.typography.labelLarge)
                    SegmentedChoiceRow(
                        options = listOf(
                            SegmentOption(SummaryOrientation.All, stringResource(Res.string.summaries_orientation_all)),
                            SegmentOption(
                                SummaryOrientation.Landscape,
                                stringResource(Res.string.summaries_orientation_landscape)
                            ),
                            SegmentOption(
                                SummaryOrientation.Portrait,
                                stringResource(Res.string.summaries_orientation_portrait)
                            ),
                        ),
                        selected = options.orientation,
                        onSelect = { value ->
                            if (editable) viewModel.updateOptions { it.copy(orientation = value) }
                        },
                        showSelectionIcon = false,
                    )
                    Hint(
                        stringResource(
                            when (options.orientation) {
                                SummaryOrientation.All -> Res.string.summaries_orientation_all_hint
                                SummaryOrientation.Landscape -> Res.string.summaries_orientation_landscape_hint
                                SummaryOrientation.Portrait -> Res.string.summaries_orientation_portrait_hint
                            }
                        )
                    )
                }

                CheckRow(
                    title = stringResource(Res.string.summaries_titles),
                    hint = stringResource(Res.string.summaries_titles_hint),
                    checked = options.showTitles,
                    enabled = editable,
                    onChange = { value -> viewModel.updateOptions { it.copy(showTitles = value) } },
                )

                options.music?.let { music ->
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(stringResource(Res.string.summaries_music), style = MaterialTheme.typography.labelLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(music.name, modifier = Modifier.weight(1f), maxLines = 1)
                            CompactSecondaryButton(
                                label = stringResource(Res.string.summaries_music_remove),
                                onClick = { viewModel.updateOptions { it.copy(music = null) } },
                                enabled = editable,
                            )
                        }
                        Hint(stringResource(Res.string.summaries_music_web_hint))
                    }
                }

                CheckRow(
                    title = stringResource(Res.string.summaries_include_photos),
                    hint = stringResource(Res.string.summaries_include_photos_hint),
                    checked = options.includePhotos,
                    enabled = editable,
                    onChange = { value -> viewModel.updateOptions { it.copy(includePhotos = value) } },
                )
                CheckRow(
                    title = stringResource(Res.string.summaries_review),
                    hint = stringResource(Res.string.summaries_review_hint),
                    checked = options.review,
                    enabled = editable,
                    onChange = { value -> viewModel.updateOptions { it.copy(review = value) } },
                )
                if (state.pickYear) {
                    CheckRow(
                        title = stringResource(Res.string.summaries_auto_year),
                        hint = stringResource(Res.string.summaries_auto_year_hint),
                        checked = state.autoYear == true,
                        enabled = state.autoYear != null,
                        onChange = viewModel::setAutoYear,
                    )
                }

                if (current != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        when (current.status) {
                            "pending" -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(stringResource(Res.string.summaries_pending))
                            }
                            "running" -> {
                                LinearProgressIndicator(
                                    progress = { current.percent / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(stringResource(Res.string.summaries_running, current.percent))
                            }
                            "planned" -> {
                                Text(stringResource(Res.string.summaries_planned, current.title))
                                CompactSecondaryButton(
                                    label = stringResource(Res.string.summaries_render_as_is),
                                    onClick = viewModel::renderAsPlanned,
                                    enabled = !state.isSubmitting,
                                )
                            }
                            "completed" -> Text(stringResource(Res.string.summaries_ready, current.title))
                            "failed" -> Text(
                                current.error?.let { stringResource(Res.string.summaries_failed, it) }
                                    ?: stringResource(Res.string.summaries_failed_unknown),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (done) Hint(stringResource(Res.string.summaries_regenerate_hint))
                    }
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            SheetActions {
                CompactSecondaryButton(
                    label = stringResource(Res.string.summaries_close),
                    onClick = onDismiss,
                )
                CompactPrimaryButton(
                    label = stringResource(if (done) Res.string.summaries_regenerate else Res.string.summaries_create),
                    onClick = viewModel::submit,
                    enabled = state.canSubmit && !state.isLoading,
                    isLoading = state.isSubmitting,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A checkbox with its title and a line of explanation; the whole row toggles. */
@Composable
private fun CheckRow(
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(Modifier.padding(start = Spacing.sm, top = Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Hint(hint)
        }
    }
}

/** A labelled button that opens a menu of [options]. */
@Composable
private fun <T> Picker(
    title: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(value, modifier = Modifier.weight(1f))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            open = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}
