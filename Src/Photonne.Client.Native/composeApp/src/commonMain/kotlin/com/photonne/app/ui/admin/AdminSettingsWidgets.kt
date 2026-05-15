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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.photonne.app.ui.theme.actionButtonHeight
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_face_settings_nightly_mode_all
import com.photonne.app.resources.admin_face_settings_nightly_mode_missing
import com.photonne.app.resources.admin_face_settings_nightly_open
import com.photonne.app.resources.admin_face_settings_nightly_state_disabled
import com.photonne.app.resources.admin_face_settings_nightly_state_enabled
import org.jetbrains.compose.resources.stringResource

/** Vertically scrolling form shell shared by every Ajustes subpage. */
@Composable
fun AdminSettingsForm(
    isLoading: Boolean,
    isSubmitting: Boolean,
    errorMessage: String?,
    successMessage: String?,
    canSave: Boolean,
    onSave: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()

        errorMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error)
        }
        successMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
            }
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.actionButtonHeight()
            ) {
                Text(stringResource(Res.string.action_save))
            }
        }
    }
}

@Composable
fun SettingSwitch(
    label: String,
    description: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SettingNumberField(
    label: String,
    value: String,
    enabled: Boolean = true,
    supporting: String? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SettingTextField(
    label: String,
    value: String,
    enabled: Boolean = true,
    supporting: String? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth()
    )
}

/** OutlinedTextField that accepts digits and a single dot — used for the
 *  cosine-distance thresholds and similar [0.0–1.0] decimal fields shared
 *  by every ML feature settings page. */
@Composable
fun SettingDecimalField(
    label: String,
    value: String,
    enabled: Boolean = true,
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
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

@Composable
fun SettingDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == value }?.second ?: value
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            enabled = enabled,
            trailingIcon = {
                IconButton(
                    onClick = { if (enabled) expanded = true },
                    enabled = enabled
                ) {
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true }
        )
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
