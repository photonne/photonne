package com.photonne.app.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_server_max_upload
import com.photonne.app.resources.admin_settings_server_max_upload_hint
import com.photonne.app.resources.admin_settings_server_public_url
import com.photonne.app.resources.admin_settings_server_session_timeout
import com.photonne.app.resources.admin_settings_server_thumbnails_path
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
fun AdminServerSettingsScreen(viewModel: AdminServerSettingsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    AdminSettingsForm(
        isLoading = state.isLoading,
        isSubmitting = state.isSubmitting,
        errorMessage = state.errorMessage,
        successMessage = state.successMessage,
        canSave = state.canSave,
        onSave = viewModel::save
    ) {
        SettingTextField(
            label = stringResource(Res.string.admin_settings_server_public_url),
            value = state.get("ServerSettings.PublicUrl"),
            supporting = "https://photos.example.com"
        ) { viewModel.set("ServerSettings.PublicUrl", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_server_max_upload),
            state.get("ServerSettings.MaxUploadSizeMb"),
            supporting = stringResource(Res.string.admin_settings_server_max_upload_hint)
        ) { viewModel.set("ServerSettings.MaxUploadSizeMb", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_server_session_timeout),
            state.get("ServerSettings.SessionTimeoutMinutes")
        ) { viewModel.set("ServerSettings.SessionTimeoutMinutes", it) }
        SettingTextField(
            label = stringResource(Res.string.admin_settings_server_thumbnails_path),
            value = state.get("ServerSettings.ThumbnailsPath")
        ) { viewModel.set("ServerSettings.ThumbnailsPath", it) }
    }
}
