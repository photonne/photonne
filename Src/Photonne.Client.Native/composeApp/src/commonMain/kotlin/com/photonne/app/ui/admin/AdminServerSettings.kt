package com.photonne.app.ui.admin

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.resources.admin_settings_server_map_key
import com.photonne.app.resources.admin_settings_server_map_key_hint
import com.photonne.app.ui.theme.SecondaryActionButton
import com.photonne.app.ui.theme.PrimaryActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
        "ServerSettings.SessionTimeoutMinutes",
        MAP_TILE_KEY
    )

    // AuthService falls back to a day when the timeout was never stored, and
    // clamps whatever is stored to five minutes … thirty days.
    override val defaults = mapOf(
        "ServerSettings.PublicUrl" to "",
        MAX_UPLOAD_KEY to "0",
        SESSION_TIMEOUT_KEY to "1440",
        // Vacía = teselas sin clave (CARTO las sirve con marca de agua).
        MAP_TILE_KEY to ""
    )

    override val intRanges = mapOf(
        MAX_UPLOAD_KEY to 0..1_000_000,
        SESSION_TIMEOUT_KEY to 5..43200,
    )

    companion object {
        const val MAX_UPLOAD_KEY = "ServerSettings.MaxUploadSizeMb"
        const val SESSION_TIMEOUT_KEY = "ServerSettings.SessionTimeoutMinutes"

        /**
         * Clave de API de las teselas del mapa. Es un ajuste del servidor
         * (no del binario) porque cada instalación de Photonne usa la suya:
         * CARTO las reparte por cliente y pide no compartirlas.
         */
        const val MAP_TILE_KEY = "ServerSettings.MapTileApiKey"
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
    val serverState by viewModel.state.collectAsStateWithLifecycle()
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
        SettingTextField(
            label = stringResource(Res.string.admin_settings_server_map_key),
            value = serverState.get(AdminServerSettingsViewModel.MAP_TILE_KEY),
            supporting = stringResource(Res.string.admin_settings_server_map_key_hint)
        ) { viewModel.set(AdminServerSettingsViewModel.MAP_TILE_KEY, it) }
    }
}

/** Example address shown inside an empty URL field. Not translatable. */
private const val URL_PLACEHOLDER = "https://photos.example.com"

@Composable
private fun DeviceConnectionSection(viewModel: DeviceConnectionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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

    SecondaryActionButton(
        label = stringResource(Res.string.admin_settings_device_probe_button),
        enabled = !state.isSaving && state.localUrl.isNotBlank(),
        isLoading = state.isProbing,
        onClick = viewModel::testLocalConnection
    )
    PrimaryActionButton(
        label = stringResource(Res.string.action_save),
        enabled = !state.isProbing && state.publicUrl.isNotBlank(),
        isLoading = state.isSaving,
        onClick = viewModel::save
    )
}
