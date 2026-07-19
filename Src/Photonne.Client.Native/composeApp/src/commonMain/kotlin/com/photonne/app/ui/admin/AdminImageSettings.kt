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
import com.photonne.app.resources.admin_settings_image_format
import com.photonne.app.resources.admin_settings_image_format_jpeg
import com.photonne.app.resources.admin_settings_image_format_webp
import com.photonne.app.resources.admin_settings_image_quality_large
import com.photonne.app.resources.admin_settings_image_quality_medium
import com.photonne.app.resources.admin_settings_image_quality_section
import com.photonne.app.resources.admin_settings_image_quality_small
import org.jetbrains.compose.resources.stringResource

class AdminImageSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        "TaskSettings.ThumbnailFormat",
        "TaskSettings.ThumbnailQuality.Small",
        "TaskSettings.ThumbnailQuality.Medium",
        "TaskSettings.ThumbnailQuality.Large",
        WORKERS_KEY,
    )

    override val defaults = mapOf(
        "TaskSettings.ThumbnailFormat" to "jpeg",
        "TaskSettings.ThumbnailQuality.Small" to "60",
        "TaskSettings.ThumbnailQuality.Medium" to "75",
        "TaskSettings.ThumbnailQuality.Large" to "85",
        WORKERS_KEY to "2",
    )

    override fun normalize(key: String, value: String): String =
        if (key == "TaskSettings.ThumbnailFormat") value else value.filter { it.isDigit() }

    companion object {
        const val WORKERS_KEY = "TaskSettings.ThumbnailWorkers"
    }
}

@Composable
fun AdminImageSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminImageSettingsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    val formatOptions = listOf(
        "jpeg" to stringResource(Res.string.admin_settings_image_format_jpeg),
        "webp" to stringResource(Res.string.admin_settings_image_format_webp)
    )

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
        SettingDropdown(
            label = stringResource(Res.string.admin_settings_image_format),
            value = state.get("TaskSettings.ThumbnailFormat").ifBlank { "jpeg" },
            options = formatOptions
        ) { viewModel.set("TaskSettings.ThumbnailFormat", it) }

        Text(
            stringResource(Res.string.admin_settings_image_quality_section),
            style = MaterialTheme.typography.titleSmall
        )
        SettingNumberField(
            stringResource(Res.string.admin_settings_image_quality_small),
            state.get("TaskSettings.ThumbnailQuality.Small"),
            supporting = "0 – 100"
        ) { viewModel.set("TaskSettings.ThumbnailQuality.Small", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_image_quality_medium),
            state.get("TaskSettings.ThumbnailQuality.Medium"),
            supporting = "0 – 100"
        ) { viewModel.set("TaskSettings.ThumbnailQuality.Medium", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_image_quality_large),
            state.get("TaskSettings.ThumbnailQuality.Large"),
            supporting = "0 – 100"
        ) { viewModel.set("TaskSettings.ThumbnailQuality.Large", it) }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_face_settings_workers_section),
            style = MaterialTheme.typography.titleSmall,
        )
        SettingNumberField(
            label = stringResource(Res.string.admin_face_settings_workers),
            value = state.get(AdminImageSettingsViewModel.WORKERS_KEY),
        ) { viewModel.set(AdminImageSettingsViewModel.WORKERS_KEY, it) }
    }
}
