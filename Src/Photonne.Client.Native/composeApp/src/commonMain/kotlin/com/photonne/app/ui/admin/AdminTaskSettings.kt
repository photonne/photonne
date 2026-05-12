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
import com.photonne.app.resources.admin_settings_task_backfill_batch
import com.photonne.app.resources.admin_settings_task_workers_embedding
import com.photonne.app.resources.admin_settings_task_workers_face
import com.photonne.app.resources.admin_settings_task_workers_metadata
import com.photonne.app.resources.admin_settings_task_workers_object
import com.photonne.app.resources.admin_settings_task_workers_scene
import com.photonne.app.resources.admin_settings_task_workers_text
import com.photonne.app.resources.admin_settings_task_workers_thumbnails
import com.photonne.app.resources.admin_settings_workers_section
import org.jetbrains.compose.resources.stringResource

class AdminTaskSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        "TaskSettings.MetadataWorkers",
        "TaskSettings.ThumbnailWorkers",
        "TaskSettings.FaceRecognitionWorkers",
        "TaskSettings.ObjectDetectionWorkers",
        "TaskSettings.SceneClassificationWorkers",
        "TaskSettings.TextRecognitionWorkers",
        "TaskSettings.ImageEmbeddingWorkers",
        "TaskSettings.BackfillBatchSize"
    )

    override val defaults = mapOf(
        "TaskSettings.MetadataWorkers" to "2",
        "TaskSettings.ThumbnailWorkers" to "2",
        "TaskSettings.FaceRecognitionWorkers" to "1",
        "TaskSettings.ObjectDetectionWorkers" to "1",
        "TaskSettings.SceneClassificationWorkers" to "1",
        "TaskSettings.TextRecognitionWorkers" to "1",
        "TaskSettings.ImageEmbeddingWorkers" to "1",
        "TaskSettings.BackfillBatchSize" to "500"
    )

    override fun normalize(key: String, value: String): String = value.filter { it.isDigit() }
}

@Composable
fun AdminTaskSettingsScreen(viewModel: AdminTaskSettingsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    AdminSettingsForm(
        isLoading = state.isLoading,
        isSubmitting = state.isSubmitting,
        errorMessage = state.errorMessage,
        successMessage = state.successMessage,
        canSave = state.canSave,
        onSave = viewModel::save
    ) {
        Text(
            stringResource(Res.string.admin_settings_workers_section),
            style = MaterialTheme.typography.titleSmall
        )
        SettingNumberField(
            stringResource(Res.string.admin_settings_task_workers_metadata),
            state.get("TaskSettings.MetadataWorkers")
        ) { viewModel.set("TaskSettings.MetadataWorkers", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_task_workers_thumbnails),
            state.get("TaskSettings.ThumbnailWorkers")
        ) { viewModel.set("TaskSettings.ThumbnailWorkers", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_task_workers_face),
            state.get("TaskSettings.FaceRecognitionWorkers")
        ) { viewModel.set("TaskSettings.FaceRecognitionWorkers", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_task_workers_object),
            state.get("TaskSettings.ObjectDetectionWorkers")
        ) { viewModel.set("TaskSettings.ObjectDetectionWorkers", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_task_workers_scene),
            state.get("TaskSettings.SceneClassificationWorkers")
        ) { viewModel.set("TaskSettings.SceneClassificationWorkers", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_task_workers_text),
            state.get("TaskSettings.TextRecognitionWorkers")
        ) { viewModel.set("TaskSettings.TextRecognitionWorkers", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_task_workers_embedding),
            state.get("TaskSettings.ImageEmbeddingWorkers")
        ) { viewModel.set("TaskSettings.ImageEmbeddingWorkers", it) }

        HorizontalDivider()

        SettingNumberField(
            stringResource(Res.string.admin_settings_task_backfill_batch),
            state.get("TaskSettings.BackfillBatchSize"),
            supporting = "1 – 5000"
        ) { viewModel.set("TaskSettings.BackfillBatchSize", it) }
    }
}
