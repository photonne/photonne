package com.photonne.app.ui.admin

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
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
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

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

    override fun normalize(key: String, value: String): String =
        if (key == WORKERS_KEY) value.filter { it.isDigit() } else value

    companion object {
        const val WORKERS_KEY = "TaskSettings.MetadataWorkers"
    }
}

/**
 * Curated IANA timezone ids for the DefaultTimezone dropdown — the zone the
 * server assumes when turning absolute timestamps (filesystem/video mvhd) into
 * local wall-clock and when computing "on this day". Not exhaustive; a custom
 * stored value is always appended so it stays selectable.
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
        run {
            val current = state.get("MetadataSettings.DefaultTimezone").ifBlank { "UTC" }
            // Keep a stored custom value selectable even if it isn't in the
            // curated list, so switching screens never silently drops it.
            val options = (COMMON_TIMEZONES + current).distinct().map { it to it }
            SettingDropdown(
                label = stringResource(Res.string.admin_settings_metadata_timezone),
                value = current,
                options = options
            ) { viewModel.set("MetadataSettings.DefaultTimezone", it) }
        }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_face_settings_workers_section),
            style = MaterialTheme.typography.titleSmall,
        )
        SettingNumberField(
            label = stringResource(Res.string.admin_face_settings_workers),
            value = state.get(AdminMetadataSettingsViewModel.WORKERS_KEY),
        ) { viewModel.set(AdminMetadataSettingsViewModel.WORKERS_KEY, it) }
    }
}
