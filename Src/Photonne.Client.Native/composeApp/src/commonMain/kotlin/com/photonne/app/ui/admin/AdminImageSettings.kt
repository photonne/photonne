package com.photonne.app.ui.admin

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiErrorFactory
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
    repository: AdminRepository,
    errorFactory: UiErrorFactory,
) : AdminKeyValueSettingsViewModel(repository, errorFactory) {

    override val keys = listOf(FORMAT_KEY, QUALITY_SMALL_KEY, QUALITY_MEDIUM_KEY, QUALITY_LARGE_KEY, WORKERS_KEY)

    // What ThumbnailGeneratorService falls back to when a key was never
    // stored. They have to match: an unset key shows its default here, and a
    // default that isn't the server's is a number on screen that nothing uses.
    override val defaults = mapOf(
        FORMAT_KEY to FORMAT_JPEG,
        QUALITY_SMALL_KEY to "75",
        QUALITY_MEDIUM_KEY to "80",
        QUALITY_LARGE_KEY to "85",
        WORKERS_KEY to "2",
    )

    override val intRanges = mapOf(
        QUALITY_SMALL_KEY to QUALITY_RANGE,
        QUALITY_MEDIUM_KEY to QUALITY_RANGE,
        QUALITY_LARGE_KEY to QUALITY_RANGE,
        WORKERS_KEY to WORKERS_RANGE,
    )

    companion object {
        const val FORMAT_KEY = "TaskSettings.ThumbnailFormat"
        const val QUALITY_SMALL_KEY = "TaskSettings.ThumbnailQuality.Small"
        const val QUALITY_MEDIUM_KEY = "TaskSettings.ThumbnailQuality.Medium"
        const val QUALITY_LARGE_KEY = "TaskSettings.ThumbnailQuality.Large"
        const val WORKERS_KEY = "TaskSettings.ThumbnailWorkers"

        // Spelled the way the web client writes them. The server compares
        // ignoring case, the web's radio buttons don't.
        const val FORMAT_JPEG = "JPEG"
        const val FORMAT_WEBP = "WebP"

        /** The server clamps to these when it reads the settings. */
        val QUALITY_RANGE = 1..100
        val WORKERS_RANGE = 1..16
    }
}

@Composable
fun AdminImageSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminImageSettingsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val formatOptions = listOf(
        AdminImageSettingsViewModel.FORMAT_JPEG to stringResource(Res.string.admin_settings_image_format_jpeg),
        AdminImageSettingsViewModel.FORMAT_WEBP to stringResource(Res.string.admin_settings_image_format_webp)
    )

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
        SettingDropdown(
            label = stringResource(Res.string.admin_settings_image_format),
            value = state.get(AdminImageSettingsViewModel.FORMAT_KEY)
                .ifBlank { AdminImageSettingsViewModel.FORMAT_JPEG },
            options = formatOptions
        ) { viewModel.set(AdminImageSettingsViewModel.FORMAT_KEY, it) }

        SettingSectionHeader(stringResource(Res.string.admin_settings_image_quality_section))
        QualitySlider(state, viewModel, AdminImageSettingsViewModel.QUALITY_SMALL_KEY, 75,
            stringResource(Res.string.admin_settings_image_quality_small))
        QualitySlider(state, viewModel, AdminImageSettingsViewModel.QUALITY_MEDIUM_KEY, 80,
            stringResource(Res.string.admin_settings_image_quality_medium))
        QualitySlider(state, viewModel, AdminImageSettingsViewModel.QUALITY_LARGE_KEY, 85,
            stringResource(Res.string.admin_settings_image_quality_large))

        SettingSectionHeader(stringResource(Res.string.admin_face_settings_workers_section))
        SettingIntSlider(
            label = stringResource(Res.string.admin_face_settings_workers),
            value = state.int(AdminImageSettingsViewModel.WORKERS_KEY, 2),
            range = AdminImageSettingsViewModel.WORKERS_RANGE,
            onValueChange = { viewModel.set(AdminImageSettingsViewModel.WORKERS_KEY, it.toString()) }
        )
    }
}

@Composable
private fun QualitySlider(
    state: AdminKeyValueUiState,
    viewModel: AdminImageSettingsViewModel,
    key: String,
    default: Int,
    label: String,
) {
    SettingIntSlider(
        label = label,
        value = state.int(key, default),
        range = AdminImageSettingsViewModel.QUALITY_RANGE,
        onValueChange = { viewModel.set(key, it.toString()) }
    )
}
