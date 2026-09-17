package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.action_save
import com.photonne.app.resources.admin_settings_device_local_url
import com.photonne.app.resources.admin_settings_device_local_url_hint
import com.photonne.app.resources.admin_settings_device_probe_button
import com.photonne.app.resources.admin_settings_device_probe_reachable
import com.photonne.app.resources.admin_settings_device_probe_unreachable
import com.photonne.app.resources.admin_settings_device_public_url
import com.photonne.app.resources.admin_settings_device_saved
import com.photonne.app.resources.admin_settings_device_section
import com.photonne.app.resources.admin_settings_device_section_hint
import com.photonne.app.resources.admin_settings_device_status_local
import com.photonne.app.resources.admin_settings_device_status_public
import com.photonne.app.resources.admin_settings_device_status_public_no_local
import com.photonne.app.resources.admin_settings_server_max_upload
import com.photonne.app.resources.admin_settings_server_max_upload_hint
import com.photonne.app.resources.admin_settings_server_public_url
import com.photonne.app.resources.admin_settings_server_session_timeout
import com.photonne.app.resources.admin_settings_server_session_timeout_hint
import com.photonne.app.ui.theme.actionButtonHeight
import com.photonne.app.resources.admin_settings_device_error_local_missing
import com.photonne.app.resources.admin_settings_device_error_local_invalid
import com.photonne.app.resources.admin_settings_device_error_public_invalid
import com.photonne.app.resources.admin_settings_device_error_public_unreachable
import org.jetbrains.compose.resources.stringResource

class AdminServerSettingsViewModel(
    repository: AdminRepository,
    errorFactory: UiErrorFactory,
) : AdminKeyValueSettingsViewModel(repository, errorFactory) {

    override val keys = listOf(
        "ServerSettings.PublicUrl",
        "ServerSettings.MaxUploadSizeMb",
        "ServerSettings.SessionTimeoutMinutes"
    )

    // AuthService falls back to a day when the timeout was never stored, and
    // clamps whatever is stored to five minutes … thirty days.
    override val defaults = mapOf(
        "ServerSettings.PublicUrl" to "",
        MAX_UPLOAD_KEY to "0",
        SESSION_TIMEOUT_KEY to "1440"
    )

    override val intRanges = mapOf(
        MAX_UPLOAD_KEY to 0..1_000_000,
        SESSION_TIMEOUT_KEY to 5..43200,
    )

    companion object {
        const val MAX_UPLOAD_KEY = "ServerSettings.MaxUploadSizeMb"
        const val SESSION_TIMEOUT_KEY = "ServerSettings.SessionTimeoutMinutes"
    }
}

@Composable
fun AdminServerSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminServerSettingsViewModel,
    deviceConnectionViewModel: DeviceConnectionViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val serverState by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) { deviceConnectionViewModel.reload() }

    AdminSettingsForm(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        state = serverState,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onSavedShown = viewModel::consumeSaved,
        onDismissError = viewModel::dismissError,
        // This phone's own addresses: stored on the device, not on the server,
        // with their own Save. Below the server's so neither button can be
        // taken for the other's.
        footer = { DeviceConnectionSection(deviceConnectionViewModel) },
    ) {
        SettingTextField(
            label = stringResource(Res.string.admin_settings_server_public_url),
            value = serverState.get("ServerSettings.PublicUrl"),
            placeholder = URL_PLACEHOLDER
        ) { viewModel.set("ServerSettings.PublicUrl", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_server_max_upload),
            serverState.get(AdminServerSettingsViewModel.MAX_UPLOAD_KEY),
            supporting = stringResource(Res.string.admin_settings_server_max_upload_hint),
            range = viewModel.intRanges[AdminServerSettingsViewModel.MAX_UPLOAD_KEY]
        ) { viewModel.set(AdminServerSettingsViewModel.MAX_UPLOAD_KEY, it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_server_session_timeout),
            serverState.get(AdminServerSettingsViewModel.SESSION_TIMEOUT_KEY),
            supporting = stringResource(Res.string.admin_settings_server_session_timeout_hint),
            range = viewModel.intRanges[AdminServerSettingsViewModel.SESSION_TIMEOUT_KEY]
        ) { viewModel.set(AdminServerSettingsViewModel.SESSION_TIMEOUT_KEY, it) }
    }
}

/** Example address shown inside an empty URL field. Not translatable. */
private const val URL_PLACEHOLDER = "https://photos.example.com"

@Composable
private fun DeviceConnectionSection(viewModel: DeviceConnectionViewModel) {
    val state by viewModel.state.collectAsState()

    SettingSectionHeader(stringResource(Res.string.admin_settings_device_section))
    Text(
        stringResource(Res.string.admin_settings_device_section_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val statusText = when {
        state.localReachable && state.localUrl.isNotBlank() ->
            stringResource(Res.string.admin_settings_device_status_local)
        state.localUrl.isBlank() ->
            stringResource(Res.string.admin_settings_device_status_public_no_local)
        else ->
            stringResource(Res.string.admin_settings_device_status_public)
    }
    Text(
        statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state.localReachable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
    )

    SettingTextField(
        label = stringResource(Res.string.admin_settings_device_public_url),
        value = state.publicUrl,
        enabled = !state.isSaving && !state.isProbing,
        placeholder = URL_PLACEHOLDER
    ) { viewModel.onPublicUrlChange(it) }

    SettingTextField(
        label = stringResource(Res.string.admin_settings_device_local_url),
        value = state.localUrl,
        enabled = !state.isSaving && !state.isProbing,
        supporting = stringResource(Res.string.admin_settings_device_local_url_hint)
    ) { viewModel.onLocalUrlChange(it) }

    val errorText: String? = when (state.errorMessage) {
        DeviceConnectionViewModel.ERROR_LOCAL_MISSING ->
            stringResource(Res.string.admin_settings_device_error_local_missing)
        DeviceConnectionViewModel.ERROR_LOCAL_INVALID ->
            stringResource(Res.string.admin_settings_device_error_local_invalid)
        DeviceConnectionViewModel.ERROR_PUBLIC_INVALID ->
            stringResource(Res.string.admin_settings_device_error_public_invalid)
        DeviceConnectionViewModel.ERROR_PUBLIC_UNREACHABLE ->
            stringResource(Res.string.admin_settings_device_error_public_unreachable)
        else -> state.errorMessage
    }
    errorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    val infoText: String? = when (state.infoMessage) {
        DeviceConnectionViewModel.PROBE_REACHABLE ->
            stringResource(Res.string.admin_settings_device_probe_reachable)
        DeviceConnectionViewModel.PROBE_UNREACHABLE ->
            stringResource(Res.string.admin_settings_device_probe_unreachable)
        DeviceConnectionViewModel.SAVED ->
            stringResource(Res.string.admin_settings_device_saved)
        else -> null
    }
    infoText?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isProbing || state.isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
        }
        OutlinedButton(
            onClick = viewModel::testLocalConnection,
            enabled = !state.isProbing && !state.isSaving && state.localUrl.isNotBlank(),
            modifier = Modifier.actionButtonHeight()
        ) {
            Text(stringResource(Res.string.admin_settings_device_probe_button))
        }
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = viewModel::save,
            enabled = !state.isProbing && !state.isSaving && state.publicUrl.isNotBlank(),
            modifier = Modifier.actionButtonHeight()
        ) {
            Text(stringResource(Res.string.action_save))
        }
    }
}
