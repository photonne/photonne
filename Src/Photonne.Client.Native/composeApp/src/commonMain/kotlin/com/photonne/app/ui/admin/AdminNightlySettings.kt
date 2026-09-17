package com.photonne.app.ui.admin

import androidx.compose.material3.HorizontalDivider
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
import com.photonne.app.resources.admin_settings_nightly_batch_size_hint
import com.photonne.app.resources.admin_settings_nightly_thumbnails
import com.photonne.app.resources.admin_settings_task_backfill_batch
import com.photonne.app.resources.admin_settings_nightly_timezone
import com.photonne.app.resources.admin_settings_nightly_indexing_coverage
import com.photonne.app.resources.admin_settings_nightly_trash_cleanup
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
        "NightlyTaskSettings.FaceClustering.Enabled",
        "NightlyTaskSettings.TrashCleanup.Enabled",
        "NightlyTaskSettings.IndexingCoverage.Enabled",
        BACKFILL_BATCH_SIZE_KEY,
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
        put("NightlyTaskSettings.TrashCleanup.Enabled", "false")
        put("NightlyTaskSettings.IndexingCoverage.Enabled", "false")
        put(BACKFILL_BATCH_SIZE_KEY, "500")
    }

    override val intRanges = mapOf(BACKFILL_BATCH_SIZE_KEY to BATCH_SIZE_RANGE)

    companion object {
        const val BACKFILL_BATCH_SIZE_KEY = "TaskSettings.BackfillBatchSize"

        /** MlBackfillEndpoints clamps the batch to this. */
        val BATCH_SIZE_RANGE = 1..5000
    }
}

@Composable
fun AdminNightlySettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminNightlySettingsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    val modeOptions = listOf(
        "missing" to stringResource(Res.string.admin_settings_feature_mode_missing),
        "all" to stringResource(Res.string.admin_settings_feature_mode_all)
    )

    AdminSettingsForm(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        state = state,
        onSave = viewModel::save,
        onRetry = viewModel::load,
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_nightly_enabled),
            checked = state.bool("NightlyTaskSettings.Enabled")
        ) { viewModel.setBool("NightlyTaskSettings.Enabled", it) }
        SettingTimeField(
            label = stringResource(Res.string.admin_settings_nightly_schedule),
            value = state.get("NightlyTaskSettings.ScheduleTime")
        ) { viewModel.set("NightlyTaskSettings.ScheduleTime", it) }
        SettingTimezoneDropdown(
            label = stringResource(Res.string.admin_settings_nightly_timezone),
            value = state.get("NightlyTaskSettings.Timezone")
        ) { viewModel.set("NightlyTaskSettings.Timezone", it) }

        SettingSectionHeader(stringResource(Res.string.admin_settings_nightly_features_section))

        FeatureRow(state, viewModel, "Metadata", stringResource(Res.string.admin_settings_nightly_metadata), modeOptions)
        FeatureRow(state, viewModel, "Thumbnails", stringResource(Res.string.admin_settings_nightly_thumbnails), modeOptions)
        FeatureRow(state, viewModel, "FaceRecognition", stringResource(Res.string.admin_settings_nightly_face), modeOptions)
        FeatureRow(state, viewModel, "ObjectDetection", stringResource(Res.string.admin_settings_nightly_object), modeOptions)
        FeatureRow(state, viewModel, "SceneClassification", stringResource(Res.string.admin_settings_nightly_scene), modeOptions)
        FeatureRow(state, viewModel, "TextRecognition", stringResource(Res.string.admin_settings_nightly_text), modeOptions)
        FeatureRow(state, viewModel, "ImageEmbedding", stringResource(Res.string.admin_settings_nightly_embedding), modeOptions)

        HorizontalDivider()
        // Dragged in hundreds; a batch set by hand below that still shows as
        // it is, and the − / + buttons walk from wherever it stands.
        SettingIntSlider(
            label = stringResource(Res.string.admin_settings_task_backfill_batch),
            value = state.int(AdminNightlySettingsViewModel.BACKFILL_BATCH_SIZE_KEY, 500),
            range = 100..5000,
            step = 100,
            description = stringResource(Res.string.admin_settings_nightly_batch_size_hint),
            onValueChange = { viewModel.set(AdminNightlySettingsViewModel.BACKFILL_BATCH_SIZE_KEY, it.toString()) }
        )

        HorizontalDivider()
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_nightly_clustering),
            checked = state.bool("NightlyTaskSettings.FaceClustering.Enabled")
        ) {
            viewModel.setBool("NightlyTaskSettings.FaceClustering.Enabled", it)
        }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_nightly_trash_cleanup),
            checked = state.bool("NightlyTaskSettings.TrashCleanup.Enabled")
        ) {
            viewModel.setBool("NightlyTaskSettings.TrashCleanup.Enabled", it)
        }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_nightly_indexing_coverage),
            checked = state.bool("NightlyTaskSettings.IndexingCoverage.Enabled")
        ) {
            viewModel.setBool("NightlyTaskSettings.IndexingCoverage.Enabled", it)
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
        checked = state.bool(enabledKey)
    ) { viewModel.setBool(enabledKey, it) }
    // The mode only means something while the feature runs at night; hidden
    // otherwise, it halves a page that was fourteen controls in a column.
    if (state.bool(enabledKey)) {
        SettingDropdown(
            label = stringResource(Res.string.admin_settings_nightly_mode),
            value = state.get(modeKey).ifBlank { "missing" },
            options = modeOptions
        ) { viewModel.set(modeKey, it) }
    }
}
