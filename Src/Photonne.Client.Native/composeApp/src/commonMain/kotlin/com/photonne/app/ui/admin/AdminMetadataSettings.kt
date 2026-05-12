package com.photonne.app.ui.admin

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_metadata_camera
import com.photonne.app.resources.admin_settings_metadata_datetime
import com.photonne.app.resources.admin_settings_metadata_gps
import com.photonne.app.resources.admin_settings_metadata_iptc
import com.photonne.app.resources.admin_settings_metadata_timezone
import com.photonne.app.resources.admin_settings_metadata_xmp
import org.jetbrains.compose.resources.stringResource

class AdminMetadataSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        "MetadataSettings.ExtractDateTime",
        "MetadataSettings.ExtractGps",
        "MetadataSettings.ExtractCameraInfo",
        "MetadataSettings.ExtractIptc",
        "MetadataSettings.ReadXmpSidecar",
        "MetadataSettings.DefaultTimezone"
    )

    override val defaults = mapOf(
        "MetadataSettings.ExtractDateTime" to "true",
        "MetadataSettings.ExtractGps" to "true",
        "MetadataSettings.ExtractCameraInfo" to "true",
        "MetadataSettings.ExtractIptc" to "true",
        "MetadataSettings.ReadXmpSidecar" to "true",
        "MetadataSettings.DefaultTimezone" to "UTC"
    )
}

@Composable
fun AdminMetadataSettingsScreen(viewModel: AdminMetadataSettingsViewModel) {
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
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_datetime),
            checked = state.get("MetadataSettings.ExtractDateTime").equals("true", true)
        ) { viewModel.set("MetadataSettings.ExtractDateTime", if (it) "true" else "false") }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_gps),
            checked = state.get("MetadataSettings.ExtractGps").equals("true", true)
        ) { viewModel.set("MetadataSettings.ExtractGps", if (it) "true" else "false") }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_camera),
            checked = state.get("MetadataSettings.ExtractCameraInfo").equals("true", true)
        ) { viewModel.set("MetadataSettings.ExtractCameraInfo", if (it) "true" else "false") }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_iptc),
            checked = state.get("MetadataSettings.ExtractIptc").equals("true", true)
        ) { viewModel.set("MetadataSettings.ExtractIptc", if (it) "true" else "false") }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_xmp),
            checked = state.get("MetadataSettings.ReadXmpSidecar").equals("true", true)
        ) { viewModel.set("MetadataSettings.ReadXmpSidecar", if (it) "true" else "false") }

        HorizontalDivider()
        SettingTextField(
            label = stringResource(Res.string.admin_settings_metadata_timezone),
            value = state.get("MetadataSettings.DefaultTimezone"),
            supporting = "IANA: UTC, Europe/Madrid, …"
        ) { viewModel.set("MetadataSettings.DefaultTimezone", it) }
    }
}
