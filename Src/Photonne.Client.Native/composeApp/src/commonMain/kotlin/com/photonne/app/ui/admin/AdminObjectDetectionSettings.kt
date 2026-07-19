package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
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
import com.photonne.app.resources.admin_object_settings_enabled
import com.photonne.app.resources.admin_object_settings_enabled_description
import com.photonne.app.resources.admin_object_settings_max_objects
import com.photonne.app.resources.admin_object_settings_max_objects_hint
import com.photonne.app.resources.admin_object_settings_min_normalized_size
import com.photonne.app.resources.admin_object_settings_min_normalized_size_hint
import com.photonne.app.resources.admin_object_settings_min_score
import com.photonne.app.resources.admin_object_settings_min_score_hint
import org.jetbrains.compose.resources.stringResource

/**
 * Runtime overrides for object detection live under the `ObjectDetection.*`
 * prefix in the global `Setting` table; the server falls back to
 * `ObjectDetectionOptions` defaults from appsettings.json when missing. The
 * nightly schedule and mode are read-only here — they live on the Tareas
 * nocturnas page (and stay editable from there).
 */
class AdminObjectDetectionSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        ENABLED_KEY,
        PROVIDER_KEY,
        MIN_SCORE_KEY,
        MIN_NORMALIZED_SIZE_KEY,
        MAX_OBJECTS_PER_ASSET_KEY,
        PREFER_THUMBNAIL_LARGE_KEY,
        WORKERS_KEY,
        NIGHTLY_ENABLED_KEY,
        NIGHTLY_MODE_KEY,
    )

    override val defaults = mapOf(
        ENABLED_KEY to "true",
        PROVIDER_KEY to "auto",
        MIN_SCORE_KEY to "0.25",
        MIN_NORMALIZED_SIZE_KEY to "0.02",
        MAX_OBJECTS_PER_ASSET_KEY to "50",
        PREFER_THUMBNAIL_LARGE_KEY to "true",
        WORKERS_KEY to "1",
        NIGHTLY_ENABLED_KEY to "false",
        NIGHTLY_MODE_KEY to "missing",
    )

    override fun normalize(key: String, value: String): String = when (key) {
        MIN_SCORE_KEY,
        MIN_NORMALIZED_SIZE_KEY -> normalizeDecimal(value)

        MAX_OBJECTS_PER_ASSET_KEY,
        WORKERS_KEY -> value.filter { it.isDigit() }

        else -> value
    }

    companion object {
        const val ENABLED_KEY = "ObjectDetection.Enabled"
        const val PROVIDER_KEY = "ObjectDetection.Provider"
        const val MIN_SCORE_KEY = "ObjectDetection.MinScore"
        const val MIN_NORMALIZED_SIZE_KEY = "ObjectDetection.MinNormalizedSize"
        const val MAX_OBJECTS_PER_ASSET_KEY = "ObjectDetection.MaxObjectsPerAsset"
        const val PREFER_THUMBNAIL_LARGE_KEY = "ObjectDetection.PreferThumbnailLarge"
        const val WORKERS_KEY = "TaskSettings.ObjectDetectionWorkers"
        const val NIGHTLY_ENABLED_KEY = "NightlyTaskSettings.ObjectDetection.Enabled"
        const val NIGHTLY_MODE_KEY = "NightlyTaskSettings.ObjectDetection.Mode"
    }
}

@Composable
fun AdminObjectDetectionSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminObjectDetectionSettingsViewModel,
    onOpenNightly: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
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
        onSave = viewModel::save,
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_object_settings_enabled),
            description = stringResource(Res.string.admin_object_settings_enabled_description),
            icon = Icons.Outlined.Category,
            checked = state.get(AdminObjectDetectionSettingsViewModel.ENABLED_KEY)
                .equals("true", ignoreCase = true),
        ) { v ->
            viewModel.set(
                AdminObjectDetectionSettingsViewModel.ENABLED_KEY,
                if (v) "true" else "false",
            )
        }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_face_settings_parameters_section),
            style = MaterialTheme.typography.titleSmall,
        )

        DeviceSettingDropdown(
            value = state.get(AdminObjectDetectionSettingsViewModel.PROVIDER_KEY),
            onChange = { viewModel.set(AdminObjectDetectionSettingsViewModel.PROVIDER_KEY, it) },
        )

        SettingSlider(
            label = stringResource(Res.string.admin_object_settings_min_score),
            value = parseFraction(
                state.get(AdminObjectDetectionSettingsViewModel.MIN_SCORE_KEY),
                default = 0.25f,
            ),
            description = stringResource(Res.string.admin_object_settings_min_score_hint),
            onValueChange = {
                viewModel.set(
                    AdminObjectDetectionSettingsViewModel.MIN_SCORE_KEY,
                    formatFraction(it),
                )
            }
        )

        SettingSlider(
            label = stringResource(Res.string.admin_object_settings_min_normalized_size),
            value = parseFraction(
                state.get(AdminObjectDetectionSettingsViewModel.MIN_NORMALIZED_SIZE_KEY),
                default = 0.02f,
            ),
            description = stringResource(Res.string.admin_object_settings_min_normalized_size_hint),
            onValueChange = {
                viewModel.set(
                    AdminObjectDetectionSettingsViewModel.MIN_NORMALIZED_SIZE_KEY,
                    formatFraction(it),
                )
            }
        )

        SettingIntSlider(
            label = stringResource(Res.string.admin_object_settings_max_objects),
            value = state.get(AdminObjectDetectionSettingsViewModel.MAX_OBJECTS_PER_ASSET_KEY)
                .toIntOrNull() ?: 50,
            range = 10..200,
            description = stringResource(Res.string.admin_object_settings_max_objects_hint),
            onValueChange = {
                viewModel.set(
                    AdminObjectDetectionSettingsViewModel.MAX_OBJECTS_PER_ASSET_KEY,
                    it.toString(),
                )
            }
        )

        SettingSwitch(
            label = stringResource(Res.string.admin_face_settings_prefer_thumb_large),
            description = stringResource(Res.string.admin_face_settings_prefer_thumb_large_description),
            icon = Icons.Outlined.PhotoSizeSelectLarge,
            checked = state.get(AdminObjectDetectionSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY)
                .equals("true", ignoreCase = true),
        ) { v ->
            viewModel.set(
                AdminObjectDetectionSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY,
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
            value = state.get(AdminObjectDetectionSettingsViewModel.WORKERS_KEY)
                .toIntOrNull() ?: 1,
            range = 1..8,
            onValueChange = {
                viewModel.set(
                    AdminObjectDetectionSettingsViewModel.WORKERS_KEY,
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
            enabled = state.get(AdminObjectDetectionSettingsViewModel.NIGHTLY_ENABLED_KEY)
                .equals("true", ignoreCase = true),
            mode = state.get(AdminObjectDetectionSettingsViewModel.NIGHTLY_MODE_KEY),
            onOpen = onOpenNightly,
        )
    }
}
