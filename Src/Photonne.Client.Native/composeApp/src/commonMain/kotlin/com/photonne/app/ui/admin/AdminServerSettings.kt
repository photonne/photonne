package com.photonne.app.ui.admin

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.resources.admin_settings_server_map_key
import com.photonne.app.resources.admin_settings_server_map_key_hint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_server_max_upload
import com.photonne.app.resources.admin_settings_server_max_upload_hint
import com.photonne.app.resources.admin_settings_server_public_url
import com.photonne.app.resources.admin_settings_server_public_url_hint
import com.photonne.app.resources.admin_settings_server_session_timeout
import com.photonne.app.resources.admin_settings_server_session_timeout_hint
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
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val serverState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    AdminSettingsForm(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        state = serverState,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onSavedShown = viewModel::consumeSaved,
        onDismissError = viewModel::dismissError,
        // Las direcciones de ESTE dispositivo (se guardan en el teléfono, no en
        // el servidor) viven ahora en Cuenta → Conexión, al alcance de
        // cualquier usuario (Lote N10). Aquí solo queda lo global del servidor.
    ) {
        SettingTextField(
            label = stringResource(Res.string.admin_settings_server_public_url),
            value = serverState.get("ServerSettings.PublicUrl"),
            placeholder = URL_PLACEHOLDER,
            supporting = stringResource(Res.string.admin_settings_server_public_url_hint)
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
internal const val URL_PLACEHOLDER = "https://photos.example.com"
