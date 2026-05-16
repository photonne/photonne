package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_face_settings_nightly_section
import com.photonne.app.resources.admin_face_settings_parameters_section
import com.photonne.app.resources.admin_face_settings_prefer_thumb_large
import com.photonne.app.resources.admin_face_settings_prefer_thumb_large_description
import com.photonne.app.resources.admin_face_settings_workers
import com.photonne.app.resources.admin_face_settings_workers_section
import com.photonne.app.resources.admin_text_settings_enabled
import com.photonne.app.resources.admin_text_settings_enabled_description
import com.photonne.app.resources.admin_text_settings_max_lines
import com.photonne.app.resources.admin_text_settings_max_lines_hint
import com.photonne.app.resources.admin_text_settings_min_score
import com.photonne.app.resources.admin_text_settings_min_score_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Runtime overrides for OCR live under the `TextRecognition.*` prefix in the
 * global `Setting` table; the server falls back to `TextRecognitionOptions`
 * defaults from appsettings.json when missing.
 */
class AdminTextRecognitionSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        ENABLED_KEY,
        MIN_SCORE_KEY,
        MAX_LINES_PER_ASSET_KEY,
        PREFER_THUMBNAIL_LARGE_KEY,
        WORKERS_KEY,
        NIGHTLY_ENABLED_KEY,
        NIGHTLY_MODE_KEY,
    )

    override val defaults = mapOf(
        ENABLED_KEY to "true",
        MIN_SCORE_KEY to "0.5",
        MAX_LINES_PER_ASSET_KEY to "200",
        PREFER_THUMBNAIL_LARGE_KEY to "true",
        WORKERS_KEY to "1",
        NIGHTLY_ENABLED_KEY to "false",
        NIGHTLY_MODE_KEY to "missing",
    )

    override fun normalize(key: String, value: String): String = when (key) {
        MIN_SCORE_KEY -> normalizeDecimal(value)
        MAX_LINES_PER_ASSET_KEY, WORKERS_KEY -> value.filter { it.isDigit() }
        else -> value
    }

    companion object {
        const val ENABLED_KEY = "TextRecognition.Enabled"
        const val MIN_SCORE_KEY = "TextRecognition.MinScore"
        const val MAX_LINES_PER_ASSET_KEY = "TextRecognition.MaxLinesPerAsset"
        const val PREFER_THUMBNAIL_LARGE_KEY = "TextRecognition.PreferThumbnailLarge"
        const val WORKERS_KEY = "TaskSettings.TextRecognitionWorkers"
        const val NIGHTLY_ENABLED_KEY = "NightlyTaskSettings.TextRecognition.Enabled"
        const val NIGHTLY_MODE_KEY = "NightlyTaskSettings.TextRecognition.Mode"
    }
}

@Composable
fun AdminTextRecognitionSettingsScreen(
    viewModel: AdminTextRecognitionSettingsViewModel,
    onOpenNightly: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    AdminSettingsForm(
        isLoading = state.isLoading,
        isSubmitting = state.isSubmitting,
        errorMessage = state.errorMessage,
        successMessage = state.successMessage,
        canSave = state.canSave,
        onSave = viewModel::save,
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_text_settings_enabled),
            description = stringResource(Res.string.admin_text_settings_enabled_description),
            icon = Icons.Outlined.TextFields,
            checked = state.get(AdminTextRecognitionSettingsViewModel.ENABLED_KEY)
                .equals("true", ignoreCase = true),
        ) { v ->
            viewModel.set(
                AdminTextRecognitionSettingsViewModel.ENABLED_KEY,
                if (v) "true" else "false",
            )
        }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_face_settings_parameters_section),
            style = MaterialTheme.typography.titleSmall,
        )

        SettingSlider(
            label = stringResource(Res.string.admin_text_settings_min_score),
            value = parseFraction(
                state.get(AdminTextRecognitionSettingsViewModel.MIN_SCORE_KEY),
                default = 0.5f,
            ),
            description = stringResource(Res.string.admin_text_settings_min_score_hint),
            onValueChange = {
                viewModel.set(
                    AdminTextRecognitionSettingsViewModel.MIN_SCORE_KEY,
                    formatFraction(it),
                )
            }
        )

        SettingIntSlider(
            label = stringResource(Res.string.admin_text_settings_max_lines),
            value = state.get(AdminTextRecognitionSettingsViewModel.MAX_LINES_PER_ASSET_KEY)
                .toIntOrNull() ?: 200,
            range = 50..500,
            description = stringResource(Res.string.admin_text_settings_max_lines_hint),
            onValueChange = {
                viewModel.set(
                    AdminTextRecognitionSettingsViewModel.MAX_LINES_PER_ASSET_KEY,
                    it.toString(),
                )
            }
        )

        SettingSwitch(
            label = stringResource(Res.string.admin_face_settings_prefer_thumb_large),
            description = stringResource(Res.string.admin_face_settings_prefer_thumb_large_description),
            icon = Icons.Outlined.PhotoSizeSelectLarge,
            checked = state.get(AdminTextRecognitionSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY)
                .equals("true", ignoreCase = true),
        ) { v ->
            viewModel.set(
                AdminTextRecognitionSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY,
                if (v) "true" else "false",
            )
        }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_face_settings_workers_section),
            style = MaterialTheme.typography.titleSmall,
        )
        SettingIntSlider(
            label = stringResource(Res.string.admin_face_settings_workers),
            value = state.get(AdminTextRecognitionSettingsViewModel.WORKERS_KEY)
                .toIntOrNull() ?: 1,
            range = 1..8,
            onValueChange = {
                viewModel.set(
                    AdminTextRecognitionSettingsViewModel.WORKERS_KEY,
                    it.toString(),
                )
            }
        )

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_face_settings_nightly_section),
            style = MaterialTheme.typography.titleSmall,
        )
        NightlyStateCard(
            enabled = state.get(AdminTextRecognitionSettingsViewModel.NIGHTLY_ENABLED_KEY)
                .equals("true", ignoreCase = true),
            mode = state.get(AdminTextRecognitionSettingsViewModel.NIGHTLY_MODE_KEY),
            onOpen = onOpenNightly,
        )
    }
}
