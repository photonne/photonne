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
import com.photonne.app.resources.admin_settings_feature_mode_all
import com.photonne.app.resources.admin_settings_feature_mode_missing
import com.photonne.app.resources.admin_settings_nightly_clustering
import com.photonne.app.resources.admin_settings_nightly_embedding
import com.photonne.app.resources.admin_settings_nightly_enabled
import com.photonne.app.resources.admin_settings_nightly_face
import com.photonne.app.resources.admin_settings_nightly_features_section
import com.photonne.app.resources.admin_settings_nightly_metadata
import com.photonne.app.resources.admin_settings_nightly_mode
import com.photonne.app.resources.admin_settings_nightly_object
import com.photonne.app.resources.admin_settings_nightly_scene
import com.photonne.app.resources.admin_settings_nightly_schedule
import com.photonne.app.resources.admin_settings_nightly_text
import com.photonne.app.resources.admin_settings_nightly_thumbnails
import com.photonne.app.resources.admin_settings_nightly_timezone
import org.jetbrains.compose.resources.stringResource

class AdminNightlySettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        "NightlyTaskSettings.Enabled",
        "NightlyTaskSettings.ScheduleTime",
        "NightlyTaskSettings.Timezone",
        "NightlyTaskSettings.Metadata.Enabled",
        "NightlyTaskSettings.Metadata.Mode",
        "NightlyTaskSettings.Thumbnails.Enabled",
        "NightlyTaskSettings.Thumbnails.Mode",
        "NightlyTaskSettings.FaceRecognition.Enabled",
        "NightlyTaskSettings.FaceRecognition.Mode",
        "NightlyTaskSettings.ObjectDetection.Enabled",
        "NightlyTaskSettings.ObjectDetection.Mode",
        "NightlyTaskSettings.SceneClassification.Enabled",
        "NightlyTaskSettings.SceneClassification.Mode",
        "NightlyTaskSettings.TextRecognition.Enabled",
        "NightlyTaskSettings.TextRecognition.Mode",
        "NightlyTaskSettings.ImageEmbedding.Enabled",
        "NightlyTaskSettings.ImageEmbedding.Mode",
        "NightlyTaskSettings.FaceClustering.Enabled"
    )

    override val defaults = buildMap {
        put("NightlyTaskSettings.Enabled", "false")
        put("NightlyTaskSettings.ScheduleTime", "02:00")
        put("NightlyTaskSettings.Timezone", "UTC")
        listOf(
            "Metadata", "Thumbnails", "FaceRecognition",
            "ObjectDetection", "SceneClassification", "TextRecognition", "ImageEmbedding"
        ).forEach { feature ->
            put("NightlyTaskSettings.$feature.Enabled", "false")
            put("NightlyTaskSettings.$feature.Mode", "missing")
        }
        put("NightlyTaskSettings.FaceClustering.Enabled", "true")
    }
}

@Composable
fun AdminNightlySettingsScreen(viewModel: AdminNightlySettingsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    val modeOptions = listOf(
        "missing" to stringResource(Res.string.admin_settings_feature_mode_missing),
        "all" to stringResource(Res.string.admin_settings_feature_mode_all)
    )

    AdminSettingsForm(
        isLoading = state.isLoading,
        isSubmitting = state.isSubmitting,
        errorMessage = state.errorMessage,
        successMessage = state.successMessage,
        canSave = state.canSave,
        onSave = viewModel::save
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_nightly_enabled),
            checked = state.get("NightlyTaskSettings.Enabled").equals("true", true)
        ) { viewModel.set("NightlyTaskSettings.Enabled", if (it) "true" else "false") }
        SettingTextField(
            label = stringResource(Res.string.admin_settings_nightly_schedule),
            value = state.get("NightlyTaskSettings.ScheduleTime"),
            supporting = "HH:MM (24h)"
        ) { viewModel.set("NightlyTaskSettings.ScheduleTime", it) }
        SettingTextField(
            label = stringResource(Res.string.admin_settings_nightly_timezone),
            value = state.get("NightlyTaskSettings.Timezone"),
            supporting = "IANA: UTC, Europe/Madrid, …"
        ) { viewModel.set("NightlyTaskSettings.Timezone", it) }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_settings_nightly_features_section),
            style = MaterialTheme.typography.titleSmall
        )

        FeatureRow(state, viewModel, "Metadata", stringResource(Res.string.admin_settings_nightly_metadata), modeOptions)
        FeatureRow(state, viewModel, "Thumbnails", stringResource(Res.string.admin_settings_nightly_thumbnails), modeOptions)
        FeatureRow(state, viewModel, "FaceRecognition", stringResource(Res.string.admin_settings_nightly_face), modeOptions)
        FeatureRow(state, viewModel, "ObjectDetection", stringResource(Res.string.admin_settings_nightly_object), modeOptions)
        FeatureRow(state, viewModel, "SceneClassification", stringResource(Res.string.admin_settings_nightly_scene), modeOptions)
        FeatureRow(state, viewModel, "TextRecognition", stringResource(Res.string.admin_settings_nightly_text), modeOptions)
        FeatureRow(state, viewModel, "ImageEmbedding", stringResource(Res.string.admin_settings_nightly_embedding), modeOptions)

        HorizontalDivider()
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_nightly_clustering),
            checked = state.get("NightlyTaskSettings.FaceClustering.Enabled").equals("true", true)
        ) {
            viewModel.set(
                "NightlyTaskSettings.FaceClustering.Enabled",
                if (it) "true" else "false"
            )
        }
    }
}

@Composable
private fun FeatureRow(
    state: AdminKeyValueUiState,
    viewModel: AdminNightlySettingsViewModel,
    featureKey: String,
    title: String,
    modeOptions: List<Pair<String, String>>
) {
    val enabledKey = "NightlyTaskSettings.$featureKey.Enabled"
    val modeKey = "NightlyTaskSettings.$featureKey.Mode"
    SettingSwitch(
        label = title,
        checked = state.get(enabledKey).equals("true", true)
    ) { viewModel.set(enabledKey, if (it) "true" else "false") }
    SettingDropdown(
        label = stringResource(Res.string.admin_settings_nightly_mode),
        value = state.get(modeKey).ifBlank { "missing" },
        enabled = state.get(enabledKey).equals("true", true),
        options = modeOptions
    ) { viewModel.set(modeKey, it) }
}
