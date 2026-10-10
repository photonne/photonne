package com.photonne.app.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_face_recognition
import com.photonne.app.resources.admin_settings_image_embedding
import com.photonne.app.resources.admin_settings_nightly_batch_size_hint
import com.photonne.app.resources.admin_settings_nightly_metadata
import com.photonne.app.resources.admin_settings_nightly_thumbnails
import com.photonne.app.resources.admin_settings_object_detection
import com.photonne.app.resources.admin_settings_performance_backfill
import com.photonne.app.resources.admin_settings_performance_io
import com.photonne.app.resources.admin_settings_performance_ml
import com.photonne.app.resources.admin_settings_scene_classification
import com.photonne.app.resources.admin_settings_task_backfill_batch
import com.photonne.app.resources.admin_settings_text_recognition
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Rendimiento: how many photos each kind of task processes at once, and the
 * reprocessing batch. One page for every worker count, as on the web, instead
 * of a slider at the bottom of each feature's page.
 */
class AdminPerformanceSettingsViewModel(
    repository: AdminRepository,
    errorFactory: UiErrorFactory,
) : AdminKeyValueSettingsViewModel(repository, errorFactory) {

    override val keys = WORKERS.map { it.key } + BACKFILL_BATCH_SIZE_KEY

    // What the server falls back to when a key was never stored.
    override val defaults = WORKERS.associate { it.key to it.default.toString() } +
        (BACKFILL_BATCH_SIZE_KEY to "500")

    override val intRanges = WORKERS.associate { it.key to it.range } +
        (BACKFILL_BATCH_SIZE_KEY to BATCH_SIZE_RANGE)

    data class Workers(val key: String, val default: Int, val range: IntRange, val label: StringResource)

    companion object {
        const val BACKFILL_BATCH_SIZE_KEY = "TaskSettings.BackfillBatchSize"

        /** MlBackfillEndpoints clamps the batch to this. */
        val BATCH_SIZE_RANGE = 1..5000

        /** Scanning and extraction. ThumbnailGeneratorService allows up to 16. */
        val IO_WORKERS = listOf(
            Workers("TaskSettings.ThumbnailWorkers", 2, 1..16, Res.string.admin_settings_nightly_thumbnails),
            Workers("TaskSettings.MetadataWorkers", 2, 1..32, Res.string.admin_settings_nightly_metadata),
        )

        /** Each one holds a connection to the ML service. EnrichmentWorker clamps to 32. */
        val ML_WORKERS = listOf(
            Workers("TaskSettings.FaceRecognitionWorkers", 1, 1..32, Res.string.admin_settings_face_recognition),
            Workers("TaskSettings.ObjectDetectionWorkers", 1, 1..32, Res.string.admin_settings_object_detection),
            Workers("TaskSettings.SceneClassificationWorkers", 1, 1..32, Res.string.admin_settings_scene_classification),
            Workers("TaskSettings.TextRecognitionWorkers", 1, 1..32, Res.string.admin_settings_text_recognition),
            Workers("TaskSettings.ImageEmbeddingWorkers", 1, 1..32, Res.string.admin_settings_image_embedding),
        )

        val WORKERS = IO_WORKERS + ML_WORKERS
    }
}

@Composable
fun AdminPerformanceSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminPerformanceSettingsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
        SettingSectionHeader(stringResource(Res.string.admin_settings_performance_io), divider = false)
        AdminPerformanceSettingsViewModel.IO_WORKERS.forEach { WorkersSlider(it, state, viewModel) }

        SettingSectionHeader(stringResource(Res.string.admin_settings_performance_ml))
        AdminPerformanceSettingsViewModel.ML_WORKERS.forEach { WorkersSlider(it, state, viewModel) }

        SettingSectionHeader(stringResource(Res.string.admin_settings_performance_backfill))
        // Dragged in hundreds; a batch set by hand below that still shows as
        // it is, and the − / + buttons walk from wherever it stands.
        SettingIntSlider(
            label = stringResource(Res.string.admin_settings_task_backfill_batch),
            value = state.int(AdminPerformanceSettingsViewModel.BACKFILL_BATCH_SIZE_KEY, 500),
            range = 100..5000,
            step = 100,
            description = stringResource(Res.string.admin_settings_nightly_batch_size_hint),
            onValueChange = {
                viewModel.set(AdminPerformanceSettingsViewModel.BACKFILL_BATCH_SIZE_KEY, it.toString())
            }
        )
    }
}

@Composable
private fun WorkersSlider(
    workers: AdminPerformanceSettingsViewModel.Workers,
    state: AdminKeyValueUiState,
    viewModel: AdminPerformanceSettingsViewModel
) {
    SettingIntSlider(
        label = stringResource(workers.label),
        value = state.int(workers.key, workers.default),
        range = workers.range,
        onValueChange = { viewModel.set(workers.key, it.toString()) }
    )
}
