package com.photonne.app.ui.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_feature_enabled
import com.photonne.app.resources.admin_settings_feature_enabled_description
import com.photonne.app.resources.admin_settings_feature_mode
import com.photonne.app.resources.admin_settings_feature_mode_all
import com.photonne.app.resources.admin_settings_feature_mode_description
import com.photonne.app.resources.admin_settings_feature_mode_missing
import com.photonne.app.resources.admin_settings_feature_nightly_section
import org.jetbrains.compose.resources.stringResource

/** Each ML feature in Ajustes (Reconocimiento facial, objetos, escenas,
 *  OCR, embeddings) toggles whether the nightly task runs for it and
 *  whether it processes only-missing or all-assets. The runtime feature
 *  flag itself lives in appsettings.json on the server. */
enum class AdminMlFeature(val enabledKey: String, val modeKey: String) {
    FaceRecognition(
        enabledKey = "NightlyTaskSettings.FaceRecognition.Enabled",
        modeKey = "NightlyTaskSettings.FaceRecognition.Mode"
    ),
    ObjectDetection(
        enabledKey = "NightlyTaskSettings.ObjectDetection.Enabled",
        modeKey = "NightlyTaskSettings.ObjectDetection.Mode"
    ),
    SceneClassification(
        enabledKey = "NightlyTaskSettings.SceneClassification.Enabled",
        modeKey = "NightlyTaskSettings.SceneClassification.Mode"
    ),
    TextRecognition(
        enabledKey = "NightlyTaskSettings.TextRecognition.Enabled",
        modeKey = "NightlyTaskSettings.TextRecognition.Mode"
    ),
    ImageEmbedding(
        enabledKey = "NightlyTaskSettings.ImageEmbedding.Enabled",
        modeKey = "NightlyTaskSettings.ImageEmbedding.Mode"
    )
}

class AdminMlFeatureSettingsViewModel(
    repository: AdminRepository,
    feature: AdminMlFeature
) : AdminKeyValueSettingsViewModel(repository) {

    private val featureKeys = listOf(feature.enabledKey, feature.modeKey)

    override val keys: List<String> = featureKeys

    override val defaults: Map<String, String> = mapOf(
        feature.enabledKey to "false",
        feature.modeKey to "missing"
    )

    val enabledKey: String = feature.enabledKey
    val modeKey: String = feature.modeKey
}

@Composable
fun AdminMlFeatureSettingsScreen(viewModel: AdminMlFeatureSettingsViewModel) {
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
        Text(
            stringResource(Res.string.admin_settings_feature_nightly_section),
            style = MaterialTheme.typography.titleSmall
        )
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_feature_enabled),
            description = stringResource(Res.string.admin_settings_feature_enabled_description),
            checked = state.get(viewModel.enabledKey).equals("true", ignoreCase = true)
        ) { viewModel.set(viewModel.enabledKey, if (it) "true" else "false") }

        SettingDropdown(
            label = stringResource(Res.string.admin_settings_feature_mode),
            value = state.get(viewModel.modeKey).ifBlank { "missing" },
            options = modeOptions
        ) { viewModel.set(viewModel.modeKey, it) }

        Text(
            stringResource(Res.string.admin_settings_feature_mode_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
