package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
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
import com.photonne.app.resources.admin_scene_settings_enabled
import com.photonne.app.resources.admin_scene_settings_enabled_description
import com.photonne.app.resources.admin_scene_settings_max_scenes
import com.photonne.app.resources.admin_scene_settings_max_scenes_hint
import com.photonne.app.resources.admin_scene_settings_min_score
import com.photonne.app.resources.admin_scene_settings_min_score_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Runtime overrides for scene classification live under the
 * `SceneClassification.*` prefix in the global `Setting` table; the server
 * falls back to `SceneClassificationOptions` defaults from appsettings.json
 * when missing.
 */
class AdminSceneClassificationSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        ENABLED_KEY,
        PROVIDER_KEY,
        MIN_SCORE_KEY,
        MAX_SCENES_PER_ASSET_KEY,
        PREFER_THUMBNAIL_LARGE_KEY,
        WORKERS_KEY,
        NIGHTLY_ENABLED_KEY,
        NIGHTLY_MODE_KEY,
    )

    override val defaults = mapOf(
        ENABLED_KEY to "true",
        PROVIDER_KEY to "auto",
        MIN_SCORE_KEY to "0.15",
        MAX_SCENES_PER_ASSET_KEY to "5",
        PREFER_THUMBNAIL_LARGE_KEY to "true",
        WORKERS_KEY to "1",
        NIGHTLY_ENABLED_KEY to "false",
        NIGHTLY_MODE_KEY to "missing",
    )

    override fun normalize(key: String, value: String): String = when (key) {
        MIN_SCORE_KEY -> normalizeDecimal(value)
        MAX_SCENES_PER_ASSET_KEY, WORKERS_KEY -> value.filter { it.isDigit() }
        else -> value
    }

    companion object {
        const val ENABLED_KEY = "SceneClassification.Enabled"
        const val PROVIDER_KEY = "SceneClassification.Provider"
        const val MIN_SCORE_KEY = "SceneClassification.MinScore"
        const val MAX_SCENES_PER_ASSET_KEY = "SceneClassification.MaxScenesPerAsset"
        const val PREFER_THUMBNAIL_LARGE_KEY = "SceneClassification.PreferThumbnailLarge"
        const val WORKERS_KEY = "TaskSettings.SceneClassificationWorkers"
        const val NIGHTLY_ENABLED_KEY = "NightlyTaskSettings.SceneClassification.Enabled"
        const val NIGHTLY_MODE_KEY = "NightlyTaskSettings.SceneClassification.Mode"
    }
}

@Composable
fun AdminSceneClassificationSettingsScreen(
    viewModel: AdminSceneClassificationSettingsViewModel,
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
            label = stringResource(Res.string.admin_scene_settings_enabled),
            description = stringResource(Res.string.admin_scene_settings_enabled_description),
            icon = Icons.Outlined.Landscape,
            checked = state.get(AdminSceneClassificationSettingsViewModel.ENABLED_KEY)
                .equals("true", ignoreCase = true),
        ) { v ->
            viewModel.set(
                AdminSceneClassificationSettingsViewModel.ENABLED_KEY,
                if (v) "true" else "false",
            )
        }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_face_settings_parameters_section),
            style = MaterialTheme.typography.titleSmall,
        )

        DeviceSettingDropdown(
            value = state.get(AdminSceneClassificationSettingsViewModel.PROVIDER_KEY),
            onChange = { viewModel.set(AdminSceneClassificationSettingsViewModel.PROVIDER_KEY, it) },
        )

        SettingSlider(
            label = stringResource(Res.string.admin_scene_settings_min_score),
            value = parseFraction(
                state.get(AdminSceneClassificationSettingsViewModel.MIN_SCORE_KEY),
                default = 0.15f,
            ),
            description = stringResource(Res.string.admin_scene_settings_min_score_hint),
            onValueChange = {
                viewModel.set(
                    AdminSceneClassificationSettingsViewModel.MIN_SCORE_KEY,
                    formatFraction(it),
                )
            }
        )

        SettingIntSlider(
            label = stringResource(Res.string.admin_scene_settings_max_scenes),
            value = state.get(AdminSceneClassificationSettingsViewModel.MAX_SCENES_PER_ASSET_KEY)
                .toIntOrNull() ?: 5,
            range = 1..20,
            description = stringResource(Res.string.admin_scene_settings_max_scenes_hint),
            onValueChange = {
                viewModel.set(
                    AdminSceneClassificationSettingsViewModel.MAX_SCENES_PER_ASSET_KEY,
                    it.toString(),
                )
            }
        )

        SettingSwitch(
            label = stringResource(Res.string.admin_face_settings_prefer_thumb_large),
            description = stringResource(Res.string.admin_face_settings_prefer_thumb_large_description),
            icon = Icons.Outlined.PhotoSizeSelectLarge,
            checked = state.get(AdminSceneClassificationSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY)
                .equals("true", ignoreCase = true),
        ) { v ->
            viewModel.set(
                AdminSceneClassificationSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY,
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
            value = state.get(AdminSceneClassificationSettingsViewModel.WORKERS_KEY)
                .toIntOrNull() ?: 1,
            range = 1..8,
            onValueChange = {
                viewModel.set(
                    AdminSceneClassificationSettingsViewModel.WORKERS_KEY,
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
            enabled = state.get(AdminSceneClassificationSettingsViewModel.NIGHTLY_ENABLED_KEY)
                .equals("true", ignoreCase = true),
            mode = state.get(AdminSceneClassificationSettingsViewModel.NIGHTLY_MODE_KEY),
            onOpen = onOpenNightly,
        )
    }
}
