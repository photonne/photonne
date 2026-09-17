package com.photonne.app.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_cancel
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_face_settings_nightly_mode_all
import com.photonne.app.resources.admin_face_settings_nightly_mode_missing
import com.photonne.app.resources.admin_face_settings_nightly_open
import com.photonne.app.resources.admin_face_settings_nightly_state_disabled
import com.photonne.app.resources.admin_face_settings_nightly_state_enabled
import com.photonne.app.resources.admin_settings_decrease
import com.photonne.app.resources.admin_settings_device_auto
import com.photonne.app.resources.admin_settings_device_cpu
import com.photonne.app.resources.admin_settings_device_gpu
import com.photonne.app.resources.admin_settings_device_hint
import com.photonne.app.resources.admin_settings_device_label
import com.photonne.app.resources.admin_settings_device_ocr_warning
import com.photonne.app.resources.admin_settings_discard_confirm
import com.photonne.app.resources.admin_settings_discard_message
import com.photonne.app.resources.admin_settings_discard_title
import com.photonne.app.resources.admin_settings_increase
import com.photonne.app.resources.admin_settings_load_failed
import com.photonne.app.resources.admin_settings_range_format
import com.photonne.app.resources.admin_settings_saved
import com.photonne.app.resources.error_banner_retry
import com.photonne.app.ui.error.ErrorBanner
import com.photonne.app.ui.library.ConfirmActionDialog
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import com.photonne.app.ui.navigation.PlatformBackHandler
import com.photonne.app.ui.theme.EmptyState
import com.photonne.app.ui.theme.actionButtonHeight
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Vertically scrolling form shell shared by every Ajustes subpage. Draws its
 * own floating subscreen chrome (static capsule over the solid form
 * background) so every settings leaf is visually consistent with the rest of
 * the app.
 *
 * Owns the three things every leaf used to get wrong on its own: a load that
 * failed shows the error with a retry instead of a form full of defaults;
 * Save sits at the end of [content] and waits for every field to be valid;
 * and leaving with unsaved edits asks first, on the capsule's back button and
 * on the system gesture alike. [footer] is for what lives on the same page
 * but isn't part of this form's Save (the device's own connection, the trash
 * usage): it goes under the button, after a divider.
 */
@Composable
fun AdminSettingsForm(
    title: String,
    onBack: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    state: AdminKeyValueUiState,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onSavedShown: () -> Unit,
    onDismissError: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    AdminResultSnackbar(
        message = stringResource(Res.string.admin_settings_saved).takeIf { state.saved },
        onShown = onSavedShown
    )

    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    var confirmDiscard by remember { mutableStateOf(false) }
    val guardedBack = { if (state.isDirty) confirmDiscard = true else onBack() }
    PlatformBackHandler(enabled = state.isDirty) { confirmDiscard = true }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.loadFailed -> EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = stringResource(Res.string.admin_settings_load_failed),
                subtitle = state.error?.userMessage,
                actionLabel = stringResource(Res.string.error_banner_retry),
                onAction = onRetry
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .hazeSource(hazeState)
                    // El padding es el modificador interno del scroll, así que hace de
                    // content-padding: reservar el hueco del cromo flotante arriba y de
                    // la nav flotante abajo deja que el formulario pase a sangre por
                    // debajo de las cápsulas (mismo estilo que Timeline/Álbumes).
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp + subscreenChromeReservedTop(),
                        bottom = 16.dp + floatingNavBarReservedHeight()
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()

                ErrorBanner(error = state.error, onDismiss = onDismissError)

                Spacer(Modifier.height(8.dp))
                AdminPrimaryActionRow(
                    label = stringResource(Res.string.action_save),
                    enabled = state.canSave,
                    isSubmitting = state.isSubmitting,
                    onClick = onSave
                )

                footer?.invoke(this)
            }
        }
        SubscreenFloatingChrome(
            title = title,
            onBack = guardedBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { if (scrollState.value > 0) 1 else 0 },
                firstVisibleItemScrollOffset = { scrollState.value },
                isScrollInProgress = { scrollState.isScrollInProgress },
                scrollToTopMinIndex = 1,
                onScrollToTop = { scrollState.animateScrollTo(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }

    if (confirmDiscard) {
        ConfirmActionDialog(
            title = stringResource(Res.string.admin_settings_discard_title),
            message = stringResource(Res.string.admin_settings_discard_message),
            confirmLabel = stringResource(Res.string.admin_settings_discard_confirm),
            isDestructive = true,
            isSubmitting = false,
            onDismiss = { confirmDiscard = false },
            onConfirm = {
                confirmDiscard = false
                onBack()
            }
        )
    }
}

/** Title of a group of settings, under a divider unless it opens the form. */
@Composable
fun SettingSectionHeader(title: String, divider: Boolean = true) {
    if (divider) HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleSmall)
}

/**
 * The tile every single-line setting is drawn on: optional leading [icon],
 * label with an optional description under it, and the control itself in
 * [trailing]. One shape for switches, numbers, pickers and times, so a form
 * reads as one list instead of cards alternating with bare outlined fields.
 */
@Composable
private fun SettingTile(
    label: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { mod -> if (onClick != null) mod.clickable(enabled = enabled, onClick = onClick) else mod },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                description?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing()
        }
    }
}

/**
 * Tile-style switch row used across every admin settings form. An optional
 * leading [icon] anchors the meaning of the toggle visually, and tapping
 * anywhere on the tile flips the switch — matches Material 3 list-item
 * toggle patterns without depending on `ListItem` (it doesn't surface a
 * trailing Switch slot in this Compose version).
 */
@Composable
fun SettingSwitch(
    label: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit
) {
    SettingTile(
        label = label,
        description = description,
        icon = icon,
        enabled = enabled,
        onClick = { onChange(!checked) }
    ) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * Typed whole number, for the values a slider can't hold: no upper bound
 * (quotas, the kNN switchover), a 0 that means "off", or a range too wide to
 * drag across (a session of 5 to 43 200 minutes). Anything short and bounded
 * belongs on a [SettingIntSlider].
 *
 * With a [range] the tile turns red and says what the server accepts as soon
 * as the value leaves it; the view model's `intRanges` is what actually holds
 * Save back. Nine digits is as far as the field goes: past that the server's
 * int parse fails and it quietly uses its default.
 */
@Composable
fun SettingNumberField(
    label: String,
    value: String,
    enabled: Boolean = true,
    supporting: String? = null,
    range: IntRange? = null,
    onChange: (String) -> Unit
) {
    val isError = range != null && (value.toIntOrNull()?.let { it !in range } ?: true)
    val rangeHint = range?.let {
        stringResource(Res.string.admin_settings_range_format, it.first, it.last)
    }
    SettingTile(
        label = label,
        description = if (isError) rangeHint else supporting ?: rangeHint,
        enabled = enabled,
        isError = isError
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(9)) },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(112.dp)
        )
    }
}

/** Free text (a URL, a model id): too long for a tile's trailing slot, so the
 *  field gets the card's full width under its label. */
@Composable
fun SettingTextField(
    label: String,
    value: String,
    enabled: Boolean = true,
    supporting: String? = null,
    placeholder: String? = null,
    onChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                enabled = enabled,
                placeholder = placeholder?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Read-only summary of a feature's nightly state with a button that lets
 *  the admin jump to the Tareas nocturnas screen to actually edit it.
 *  Shared by every ML feature settings page; uses the `admin_face_settings_
 *  nightly_*` string keys which read as feature-agnostic copy. */
@Composable
fun NightlyStateCard(
    enabled: Boolean,
    mode: String,
    onOpen: () -> Unit,
) {
    val enabledText = stringResource(
        if (enabled) Res.string.admin_face_settings_nightly_state_enabled
        else Res.string.admin_face_settings_nightly_state_disabled,
    )
    val modeText = stringResource(
        if (mode.equals("all", ignoreCase = true)) Res.string.admin_face_settings_nightly_mode_all
        else Res.string.admin_face_settings_nightly_mode_missing,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(enabledText, style = MaterialTheme.typography.bodyMedium)
                Text(
                    modeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) {
                Text(stringResource(Res.string.admin_face_settings_nightly_open))
            }
        }
    }
}

/**
 * One-of-a-few picker. The stored value is matched to an option ignoring
 * case: the web client writes "JPEG" where this one used to write "jpeg", and
 * a strict match showed the raw value and counted re-picking it as a change.
 */
@Composable
fun SettingDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    description: String? = null,
    isError: Boolean = false,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first.equals(value, ignoreCase = true) }?.second ?: value
    SettingTile(
        label = label,
        description = description,
        enabled = enabled,
        isError = isError,
        onClick = { expanded = true }
    ) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    display,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 168.dp)
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.second) },
                        onClick = {
                            onChange(option.first)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Curated IANA timezone ids — the zone the server assumes when turning
 * absolute timestamps (filesystem/video mvhd) into local wall-clock, when
 * computing "on this day", and when deciding what "02:00" means for the
 * nightly run. Not exhaustive; a custom stored value is always appended so it
 * stays selectable.
 */
private val COMMON_TIMEZONES = listOf(
    "UTC",
    "Europe/Madrid", "Europe/Lisbon", "Europe/London", "Europe/Paris",
    "Europe/Berlin", "Europe/Rome", "Europe/Amsterdam", "Europe/Brussels",
    "Europe/Zurich", "Europe/Vienna", "Europe/Warsaw", "Europe/Athens",
    "Europe/Istanbul", "Europe/Moscow",
    "Atlantic/Canary",
    "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
    "America/Toronto", "America/Mexico_City", "America/Bogota", "America/Sao_Paulo",
    "America/Argentina/Buenos_Aires",
    "Africa/Casablanca", "Africa/Cairo", "Africa/Johannesburg",
    "Asia/Dubai", "Asia/Kolkata", "Asia/Shanghai", "Asia/Hong_Kong",
    "Asia/Singapore", "Asia/Tokyo", "Asia/Seoul", "Asia/Jakarta",
    "Australia/Perth", "Australia/Sydney", "Pacific/Auckland",
)

/** Timezone picker over [COMMON_TIMEZONES]. A typed id was one typo away from
 *  a nightly run in the wrong zone, with nothing to say so. */
@Composable
fun SettingTimezoneDropdown(
    label: String,
    value: String,
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    val current = value.ifBlank { "UTC" }
    // Keep a stored custom value selectable even if it isn't in the curated
    // list, so switching screens never silently drops it.
    val options = (COMMON_TIMEZONES + current).distinct().map { it to it }
    SettingDropdown(label = label, value = current, options = options, enabled = enabled, onChange = onChange)
}

/** A time of day stored as "HH:mm". Tapping the tile opens the clock input,
 *  so what gets saved is always something the server's TimeOnly can parse. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingTimeField(
    label: String,
    value: String,
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val hour = value.substringBefore(':').toIntOrNull()?.coerceIn(0, 23) ?: 0
    val minute = value.substringAfter(':', "").take(2).toIntOrNull()?.coerceIn(0, 59) ?: 0
    SettingTile(label = label, enabled = enabled, onClick = { open = true }) {
        ValueChip(text = formatTime(hour, minute))
    }
    if (open) {
        val picker = remember { TimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true) }
        AlertDialog(
            onDismissRequest = { open = false },
            text = { TimeInput(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(formatTime(picker.hour, picker.minute))
                    open = false
                }) {
                    Text(stringResource(Res.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String =
    hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

/** The three compute-device values persisted under each `<Feature>.Provider`
 *  setting; kept in sync with the server-side MlProviders.Device* constants. */
object ComputeDevice {
    const val AUTO = "auto"
    const val GPU = "cuda"
    const val CPU = "cpu"
}

/**
 * Compute-device selector shared by every ML feature settings page. Writes the
 * `<Feature>.Provider` key (auto|cuda|cpu); the server maps that to an ONNX
 * provider and hot-reloads the task on the ML service. Pass [showOcrWarning] on
 * the OCR page, where GPU has a known VRAM-blowup caveat.
 */
@Composable
fun DeviceSettingDropdown(
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    showOcrWarning: Boolean = false,
) {
    val options = listOf(
        ComputeDevice.AUTO to stringResource(Res.string.admin_settings_device_auto),
        ComputeDevice.GPU to stringResource(Res.string.admin_settings_device_gpu),
        ComputeDevice.CPU to stringResource(Res.string.admin_settings_device_cpu),
    )
    val warn = showOcrWarning && value.equals(ComputeDevice.GPU, ignoreCase = true)
    SettingDropdown(
        label = stringResource(Res.string.admin_settings_device_label),
        value = value.ifBlank { ComputeDevice.AUTO },
        options = options,
        enabled = enabled,
        description = stringResource(
            if (warn) Res.string.admin_settings_device_ocr_warning
            else Res.string.admin_settings_device_hint
        ),
        isError = warn,
        onChange = onChange,
    )
}

/**
 * Slider for a bounded numeric setting. The current value is shown as a chip
 * on the right of the label so the admin sees the live number as they drag,
 * flanked by − and + for the exact value a thumb can't land on.
 *
 * The chip shows the stored value even when it falls outside [range] (set
 * from the web, or by hand): the thumb pins to the nearest end, the number
 * doesn't lie. The track is continuous — callers round what they store — as
 * a tick per step turned anything past thirty positions into a solid band.
 *
 * Wrapped in a card to match [SettingSwitch] visually; every individual
 * setting becomes a self-contained tile inside the form.
 */
@Composable
fun SettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    stepSize: Float = 0.01f,
    description: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    valueFormat: (Float) -> String = { formatFraction(it) }
) {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onValueChange((clamped - stepSize).coerceIn(range.start, range.endInclusive)) },
                    enabled = enabled && clamped > range.start,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Remove,
                        contentDescription = stringResource(Res.string.admin_settings_decrease),
                        modifier = Modifier.size(18.dp)
                    )
                }
                ValueChip(text = valueFormat(value))
                IconButton(
                    onClick = { onValueChange((clamped + stepSize).coerceIn(range.start, range.endInclusive)) },
                    enabled = enabled && clamped < range.endInclusive,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = stringResource(Res.string.admin_settings_increase),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Slider(
                value = clamped,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Integer-only variant of [SettingSlider]. [step] is the grain of both the
 * drag and the − / + buttons, for ranges where single units are noise (a
 * batch of 100 to 5000). Pass [valueSuffix] for units like "workers" or
 * "vecinos" to render next to the number in the chip.
 */
@Composable
fun SettingIntSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    step: Int = 1,
    description: String? = null,
    enabled: Boolean = true,
    valueSuffix: String = ""
) {
    val positions = (range.last - range.first) / step + 1
    SettingSlider(
        label = label,
        value = value.toFloat(),
        onValueChange = { onValueChange(snapToStep(it, range, step)) },
        range = range.first.toFloat()..range.last.toFloat(),
        // Ticks only while they are still readable as ticks.
        steps = if (positions in 3..33) positions - 2 else 0,
        stepSize = step.toFloat(),
        description = description,
        enabled = enabled,
        valueFormat = { f ->
            val n = f.roundToInt()
            if (valueSuffix.isNotBlank()) "$n $valueSuffix" else n.toString()
        }
    )
}

/** Nearest multiple of [step] counted from the start of [range], inside it. */
internal fun snapToStep(raw: Float, range: IntRange, step: Int): Int {
    val stepsFromStart = ((raw - range.first) / step).roundToInt()
    return (range.first + stepsFromStart * step).coerceIn(range)
}

@Composable
private fun ValueChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
