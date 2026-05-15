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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.photonne.app.resources.admin_settings_server_thumbnails_path
import com.photonne.app.ui.theme.actionButtonHeight
import org.jetbrains.compose.resources.stringResource

class AdminServerSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        "ServerSettings.PublicUrl",
        "ServerSettings.MaxUploadSizeMb",
        "ServerSettings.SessionTimeoutMinutes",
        "ServerSettings.ThumbnailsPath"
    )

    override val defaults = mapOf(
        "ServerSettings.PublicUrl" to "",
        "ServerSettings.MaxUploadSizeMb" to "0",
        "ServerSettings.SessionTimeoutMinutes" to "60",
        "ServerSettings.ThumbnailsPath" to "/data/thumbnails"
    )

    override fun normalize(key: String, value: String): String = when (key) {
        "ServerSettings.MaxUploadSizeMb", "ServerSettings.SessionTimeoutMinutes" ->
            value.filter { it.isDigit() }
        else -> value
    }
}

@Composable
fun AdminServerSettingsScreen(
    viewModel: AdminServerSettingsViewModel,
    deviceConnectionViewModel: DeviceConnectionViewModel
) {
    val serverState by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) { deviceConnectionViewModel.reload() }

    if (serverState.isLoading) {
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
        SettingTextField(
            label = stringResource(Res.string.admin_settings_server_public_url),
            value = serverState.get("ServerSettings.PublicUrl"),
            supporting = "https://photos.example.com"
        ) { viewModel.set("ServerSettings.PublicUrl", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_server_max_upload),
            serverState.get("ServerSettings.MaxUploadSizeMb"),
            supporting = stringResource(Res.string.admin_settings_server_max_upload_hint)
        ) { viewModel.set("ServerSettings.MaxUploadSizeMb", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_server_session_timeout),
            serverState.get("ServerSettings.SessionTimeoutMinutes")
        ) { viewModel.set("ServerSettings.SessionTimeoutMinutes", it) }
        SettingTextField(
            label = stringResource(Res.string.admin_settings_server_thumbnails_path),
            value = serverState.get("ServerSettings.ThumbnailsPath")
        ) { viewModel.set("ServerSettings.ThumbnailsPath", it) }

        serverState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        serverState.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (serverState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
            }
            Button(
                onClick = viewModel::save,
                enabled = serverState.canSave,
                modifier = Modifier.actionButtonHeight()
            ) {
                Text(stringResource(Res.string.action_save))
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        DeviceConnectionSection(deviceConnectionViewModel)
    }
}

@Composable
private fun DeviceConnectionSection(viewModel: DeviceConnectionViewModel) {
    val state by viewModel.state.collectAsState()

    Text(
        stringResource(Res.string.admin_settings_device_section),
        style = MaterialTheme.typography.titleMedium
    )
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
        supporting = "https://photos.example.com"
    ) { viewModel.onPublicUrlChange(it) }

    SettingTextField(
        label = stringResource(Res.string.admin_settings_device_local_url),
        value = state.localUrl,
        enabled = !state.isSaving && !state.isProbing,
        supporting = stringResource(Res.string.admin_settings_device_local_url_hint)
    ) { viewModel.onLocalUrlChange(it) }

    state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

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
