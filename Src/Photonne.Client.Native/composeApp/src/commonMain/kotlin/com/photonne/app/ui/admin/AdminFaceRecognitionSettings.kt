package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import kotlin.math.roundToInt
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_face_settings_clustering_threshold
import com.photonne.app.resources.admin_face_settings_enabled
import com.photonne.app.resources.admin_face_settings_enabled_description
import com.photonne.app.resources.admin_face_settings_knn_neighbors
import com.photonne.app.resources.admin_face_settings_knn_neighbors_hint
import com.photonne.app.resources.admin_face_settings_knn_switchover
import com.photonne.app.resources.admin_face_settings_knn_switchover_hint
import com.photonne.app.resources.admin_face_settings_min_detection_score
import com.photonne.app.resources.admin_face_settings_min_detection_score_hint
import com.photonne.app.resources.admin_face_settings_min_faces_for_cluster
import com.photonne.app.resources.admin_face_settings_nightly_section
import com.photonne.app.resources.admin_face_settings_parameters_section
import com.photonne.app.resources.admin_face_settings_prefer_thumb_large
import com.photonne.app.resources.admin_face_settings_prefer_thumb_large_description
import com.photonne.app.resources.admin_face_settings_suggestion_threshold
import com.photonne.app.resources.admin_face_settings_suggestion_threshold_hint
import com.photonne.app.resources.admin_face_settings_threshold_hint
import com.photonne.app.resources.admin_face_settings_workers
import com.photonne.app.resources.admin_face_settings_workers_section
import org.jetbrains.compose.resources.stringResource

/**
 * All face-recognition runtime overrides live in the global `Setting` table
 * under the `FaceRecognition.*` prefix; the server falls back to
 * `FaceRecognitionOptions` defaults from appsettings.json when a key is
 * missing. Workers and nightly toggle are stored under their own prefixes
 * (`TaskSettings.*`, `NightlyTaskSettings.*`) but shown here for context;
 * the nightly section is read-only with a link to the scheduling page.
 */
class AdminFaceRecognitionSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        ENABLED_KEY,
        MIN_DETECTION_SCORE_KEY,
        CLUSTERING_THRESHOLD_KEY,
        SUGGESTION_THRESHOLD_KEY,
        MIN_FACES_FOR_CLUSTER_KEY,
        KNN_SWITCHOVER_THRESHOLD_KEY,
        KNN_NEIGHBORS_KEY,
        PREFER_THUMBNAIL_LARGE_KEY,
        WORKERS_KEY,
        NIGHTLY_ENABLED_KEY,
        NIGHTLY_MODE_KEY,
    )

    override val defaults = mapOf(
        ENABLED_KEY to "true",
        MIN_DETECTION_SCORE_KEY to "0.5",
        CLUSTERING_THRESHOLD_KEY to "0.42",
        SUGGESTION_THRESHOLD_KEY to "0.55",
        MIN_FACES_FOR_CLUSTER_KEY to "2",
        KNN_SWITCHOVER_THRESHOLD_KEY to "1500",
        KNN_NEIGHBORS_KEY to "20",
        PREFER_THUMBNAIL_LARGE_KEY to "true",
        WORKERS_KEY to "1",
        NIGHTLY_ENABLED_KEY to "false",
        NIGHTLY_MODE_KEY to "missing",
    )

    override fun normalize(key: String, value: String): String = when (key) {
        MIN_DETECTION_SCORE_KEY,
        CLUSTERING_THRESHOLD_KEY,
        SUGGESTION_THRESHOLD_KEY -> normalizeDecimal(value)

        MIN_FACES_FOR_CLUSTER_KEY,
        KNN_SWITCHOVER_THRESHOLD_KEY,
        KNN_NEIGHBORS_KEY,
        WORKERS_KEY -> value.filter { it.isDigit() }

        else -> value
    }

    companion object {
        const val ENABLED_KEY = "FaceRecognition.Enabled"
        const val MIN_DETECTION_SCORE_KEY = "FaceRecognition.MinDetectionScore"
        const val CLUSTERING_THRESHOLD_KEY = "FaceRecognition.ClusteringThreshold"
        const val SUGGESTION_THRESHOLD_KEY = "FaceRecognition.SuggestionThreshold"
        const val MIN_FACES_FOR_CLUSTER_KEY = "FaceRecognition.MinFacesForCluster"
        const val KNN_SWITCHOVER_THRESHOLD_KEY = "FaceRecognition.KnnSwitchoverThreshold"
        const val KNN_NEIGHBORS_KEY = "FaceRecognition.KnnNeighbors"
        const val PREFER_THUMBNAIL_LARGE_KEY = "FaceRecognition.PreferThumbnailLarge"
        const val WORKERS_KEY = "TaskSettings.FaceRecognitionWorkers"
        const val NIGHTLY_ENABLED_KEY = "NightlyTaskSettings.FaceRecognition.Enabled"
        const val NIGHTLY_MODE_KEY = "NightlyTaskSettings.FaceRecognition.Mode"
    }
}

@Composable
fun AdminFaceRecognitionSettingsScreen(
    viewModel: AdminFaceRecognitionSettingsViewModel,
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
        // Master kill switch — the runtime override checked by
        // FaceRecognitionService.IsRuntimeEnabledAsync.
        SettingSwitch(
            label = stringResource(Res.string.admin_face_settings_enabled),
            description = stringResource(Res.string.admin_face_settings_enabled_description),
            icon = Icons.Outlined.Face,
            checked = state.get(AdminFaceRecognitionSettingsViewModel.ENABLED_KEY)
                .equals("true", ignoreCase = true),
        ) { v ->
            viewModel.set(
                AdminFaceRecognitionSettingsViewModel.ENABLED_KEY,
                if (v) "true" else "false",
            )
        }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_face_settings_parameters_section),
            style = MaterialTheme.typography.titleSmall,
        )

        // Three confidence thresholds in [0.0, 1.0] — sliders here let the
        // admin feel out the value with the live chip, whereas the typed
        // decimal field forced them to commit a number blind.
        SettingSlider(
            label = stringResource(Res.string.admin_face_settings_min_detection_score),
            value = parseFraction(
                state.get(AdminFaceRecognitionSettingsViewModel.MIN_DETECTION_SCORE_KEY),
                default = 0.5f,
            ),
            description = stringResource(Res.string.admin_face_settings_min_detection_score_hint),
            onValueChange = {
                viewModel.set(
                    AdminFaceRecognitionSettingsViewModel.MIN_DETECTION_SCORE_KEY,
                    formatFraction(it),
                )
            }
        )

        SettingSlider(
            label = stringResource(Res.string.admin_face_settings_clustering_threshold),
            value = parseFraction(
                state.get(AdminFaceRecognitionSettingsViewModel.CLUSTERING_THRESHOLD_KEY),
                default = 0.42f,
            ),
            description = stringResource(Res.string.admin_face_settings_threshold_hint),
            onValueChange = {
                viewModel.set(
                    AdminFaceRecognitionSettingsViewModel.CLUSTERING_THRESHOLD_KEY,
                    formatFraction(it),
                )
            }
        )

        SettingSlider(
            label = stringResource(Res.string.admin_face_settings_suggestion_threshold),
            value = parseFraction(
                state.get(AdminFaceRecognitionSettingsViewModel.SUGGESTION_THRESHOLD_KEY),
                default = 0.55f,
            ),
            description = stringResource(Res.string.admin_face_settings_suggestion_threshold_hint),
            onValueChange = {
                viewModel.set(
                    AdminFaceRecognitionSettingsViewModel.SUGGESTION_THRESHOLD_KEY,
                    formatFraction(it),
                )
            }
        )

        SettingIntSlider(
            label = stringResource(Res.string.admin_face_settings_min_faces_for_cluster),
            value = state.get(AdminFaceRecognitionSettingsViewModel.MIN_FACES_FOR_CLUSTER_KEY)
                .toIntOrNull() ?: 2,
            range = 1..10,
            onValueChange = {
                viewModel.set(
                    AdminFaceRecognitionSettingsViewModel.MIN_FACES_FOR_CLUSTER_KEY,
                    it.toString(),
                )
            }
        )

        // Switchover is unbounded (1500 default, can go much higher on big
        // libraries) so a slider would be wrong — keep the typed field.
        SettingNumberField(
            label = stringResource(Res.string.admin_face_settings_knn_switchover),
            value = state.get(AdminFaceRecognitionSettingsViewModel.KNN_SWITCHOVER_THRESHOLD_KEY),
            supporting = stringResource(Res.string.admin_face_settings_knn_switchover_hint),
        ) { viewModel.set(AdminFaceRecognitionSettingsViewModel.KNN_SWITCHOVER_THRESHOLD_KEY, it) }

        SettingIntSlider(
            label = stringResource(Res.string.admin_face_settings_knn_neighbors),
            value = state.get(AdminFaceRecognitionSettingsViewModel.KNN_NEIGHBORS_KEY)
                .toIntOrNull() ?: 20,
            range = 5..50,
            description = stringResource(Res.string.admin_face_settings_knn_neighbors_hint),
            onValueChange = {
                viewModel.set(
                    AdminFaceRecognitionSettingsViewModel.KNN_NEIGHBORS_KEY,
                    it.toString(),
                )
            }
        )

        SettingSwitch(
            label = stringResource(Res.string.admin_face_settings_prefer_thumb_large),
            description = stringResource(Res.string.admin_face_settings_prefer_thumb_large_description),
            icon = Icons.Outlined.PhotoSizeSelectLarge,
            checked = state.get(AdminFaceRecognitionSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY)
                .equals("true", ignoreCase = true),
        ) { v ->
            viewModel.set(
                AdminFaceRecognitionSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY,
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
            value = state.get(AdminFaceRecognitionSettingsViewModel.WORKERS_KEY)
                .toIntOrNull() ?: 1,
            range = 1..8,
            onValueChange = {
                viewModel.set(
                    AdminFaceRecognitionSettingsViewModel.WORKERS_KEY,
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
            enabled = state.get(AdminFaceRecognitionSettingsViewModel.NIGHTLY_ENABLED_KEY)
                .equals("true", ignoreCase = true),
            mode = state.get(AdminFaceRecognitionSettingsViewModel.NIGHTLY_MODE_KEY),
            onOpen = onOpenNightly,
        )
    }
}

/** Parses a setting value to a [0,1] Float, tolerating an empty string
 *  (returns [default]) and out-of-range overrides from the server. */
internal fun parseFraction(raw: String, default: Float): Float =
    raw.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 1f) ?: default

/** Two-decimal rendering matching what `normalizeDecimal` and the server
 *  accept. The slider has 100 steps so values are already aligned to 0.01. */
internal fun formatFraction(value: Float): String {
    val rounded = (value * 100).roundToInt()
    val whole = rounded / 100
    val cents = rounded % 100
    return "$whole." + cents.toString().padStart(2, '0')
}

internal fun normalizeDecimal(raw: String): String {
    val swapped = raw.replace(',', '.')
    val firstDot = swapped.indexOf('.')
    val sb = StringBuilder()
    for ((i, ch) in swapped.withIndex()) {
        if (ch.isDigit()) sb.append(ch)
        else if (ch == '.' && i == firstDot) sb.append(ch)
    }
    return sb.toString()
}
