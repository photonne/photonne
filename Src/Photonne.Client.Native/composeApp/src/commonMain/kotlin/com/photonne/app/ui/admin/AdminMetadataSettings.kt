package com.photonne.app.ui.admin

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_face_settings_workers
import com.photonne.app.resources.admin_face_settings_workers_section
import com.photonne.app.resources.admin_settings_metadata_camera
import com.photonne.app.resources.admin_settings_metadata_datetime
import com.photonne.app.resources.admin_settings_metadata_gps
import com.photonne.app.resources.admin_settings_metadata_iptc
import com.photonne.app.resources.admin_settings_metadata_timezone
import com.photonne.app.resources.admin_settings_metadata_xmp
import org.jetbrains.compose.resources.stringResource

class AdminMetadataSettingsViewModel(
    repository: AdminRepository,
    errorFactory: UiErrorFactory,
) : AdminKeyValueSettingsViewModel(repository, errorFactory) {

    override val keys = listOf(
        "MetadataSettings.ExtractDateTime",
        "MetadataSettings.ExtractGps",
        "MetadataSettings.ExtractCameraInfo",
        "MetadataSettings.ExtractIptc",
        "MetadataSettings.ReadXmpSidecar",
        "MetadataSettings.DefaultTimezone",
        WORKERS_KEY,
    )

    override val defaults = mapOf(
        "MetadataSettings.ExtractDateTime" to "true",
        "MetadataSettings.ExtractGps" to "true",
        "MetadataSettings.ExtractCameraInfo" to "true",
        "MetadataSettings.ExtractIptc" to "true",
        "MetadataSettings.ReadXmpSidecar" to "true",
        "MetadataSettings.DefaultTimezone" to "UTC",
        WORKERS_KEY to "2",
    )

    override val intRanges = mapOf(WORKERS_KEY to WORKERS_RANGE)

    companion object {
        const val WORKERS_KEY = "TaskSettings.MetadataWorkers"

        /** EnrichmentWorker clamps every worker count to this. */
        val WORKERS_RANGE = 1..32
    }
}

@Composable
fun AdminMetadataSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminMetadataSettingsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    AdminSettingsForm(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        state = state,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onSavedShown = viewModel::consumeSaved,
        onDismissError = viewModel::dismissError,
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_datetime),
            checked = state.bool("MetadataSettings.ExtractDateTime")
        ) { viewModel.setBool("MetadataSettings.ExtractDateTime", it) }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_gps),
            checked = state.bool("MetadataSettings.ExtractGps")
        ) { viewModel.setBool("MetadataSettings.ExtractGps", it) }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_camera),
            checked = state.bool("MetadataSettings.ExtractCameraInfo")
        ) { viewModel.setBool("MetadataSettings.ExtractCameraInfo", it) }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_iptc),
            checked = state.bool("MetadataSettings.ExtractIptc")
        ) { viewModel.setBool("MetadataSettings.ExtractIptc", it) }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_metadata_xmp),
            checked = state.bool("MetadataSettings.ReadXmpSidecar")
        ) { viewModel.setBool("MetadataSettings.ReadXmpSidecar", it) }

        HorizontalDivider()
        SettingTimezoneDropdown(
            label = stringResource(Res.string.admin_settings_metadata_timezone),
            value = state.get("MetadataSettings.DefaultTimezone")
        ) { viewModel.set("MetadataSettings.DefaultTimezone", it) }

        SettingSectionHeader(stringResource(Res.string.admin_face_settings_workers_section))
        SettingIntSlider(
            label = stringResource(Res.string.admin_face_settings_workers),
            value = state.int(AdminMetadataSettingsViewModel.WORKERS_KEY, 2),
            range = AdminMetadataSettingsViewModel.WORKERS_RANGE,
            onValueChange = { viewModel.set(AdminMetadataSettingsViewModel.WORKERS_KEY, it.toString()) }
        )
    }
}
