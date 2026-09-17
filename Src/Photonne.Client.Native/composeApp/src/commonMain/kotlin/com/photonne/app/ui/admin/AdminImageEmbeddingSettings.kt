package com.photonne.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_embedding_settings_enabled
import com.photonne.app.resources.admin_embedding_settings_enabled_description
import com.photonne.app.resources.admin_embedding_settings_max_cosine
import com.photonne.app.resources.admin_embedding_settings_max_cosine_hint
import com.photonne.app.resources.admin_embedding_settings_model_version
import com.photonne.app.resources.admin_embedding_settings_model_version_hint
import com.photonne.app.resources.admin_embedding_settings_model_version_warning
import com.photonne.app.resources.admin_face_settings_nightly_section
import com.photonne.app.resources.admin_face_settings_parameters_section
import com.photonne.app.resources.admin_face_settings_prefer_thumb_large
import com.photonne.app.resources.admin_face_settings_prefer_thumb_large_description
import com.photonne.app.resources.admin_face_settings_workers
import com.photonne.app.resources.admin_face_settings_workers_section
import org.jetbrains.compose.resources.stringResource

/**
 * Runtime overrides for CLIP image embeddings live under the `Embedding.*`
 * prefix in the global `Setting` table; the server falls back to
 * `EmbeddingOptions` defaults from appsettings.json when missing.
 *
 * Special case: changing [MODEL_VERSION_KEY] invalidates every existing
 * embedding row — assets with the old version stay encoded with the old
 * model until a backfill with "solo faltantes" disabled is run. The UI
 * surfaces this warning whenever the model version has been edited.
 */
class AdminImageEmbeddingSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        ENABLED_KEY,
        PROVIDER_KEY,
        MODEL_VERSION_KEY,
        MAX_COSINE_DISTANCE_KEY,
        PREFER_THUMBNAIL_LARGE_KEY,
        WORKERS_KEY,
        NIGHTLY_ENABLED_KEY,
        NIGHTLY_MODE_KEY,
    )

    override val defaults = mapOf(
        ENABLED_KEY to "true",
        PROVIDER_KEY to "auto",
        MODEL_VERSION_KEY to "mclip-vit-b32-v1",
        // appsettings.json ships 0.85 and overrides the 0.7 of EmbeddingOptions.
        MAX_COSINE_DISTANCE_KEY to "0.85",
        PREFER_THUMBNAIL_LARGE_KEY to "true",
        WORKERS_KEY to "1",
        NIGHTLY_ENABLED_KEY to "false",
        NIGHTLY_MODE_KEY to "missing",
    )

    override fun normalize(key: String, value: String): String = when (key) {
        MAX_COSINE_DISTANCE_KEY -> normalizeDecimal(value)
        WORKERS_KEY -> value.filter { it.isDigit() }
        else -> value
    }

    companion object {
        const val ENABLED_KEY = "Embedding.Enabled"
        const val PROVIDER_KEY = "Embedding.Provider"
        const val MODEL_VERSION_KEY = "Embedding.ModelVersion"
        const val MAX_COSINE_DISTANCE_KEY = "Embedding.MaxCosineDistance"
        const val PREFER_THUMBNAIL_LARGE_KEY = "Embedding.PreferThumbnailLarge"
        const val WORKERS_KEY = "TaskSettings.ImageEmbeddingWorkers"
        const val NIGHTLY_ENABLED_KEY = "NightlyTaskSettings.ImageEmbedding.Enabled"
        const val NIGHTLY_MODE_KEY = "NightlyTaskSettings.ImageEmbedding.Mode"
    }
}

@Composable
fun AdminImageEmbeddingSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminImageEmbeddingSettingsViewModel,
    onOpenNightly: () -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    val originalModel = state.original[AdminImageEmbeddingSettingsViewModel.MODEL_VERSION_KEY]
        .orEmpty()
    val currentModel = state.get(AdminImageEmbeddingSettingsViewModel.MODEL_VERSION_KEY)
    val modelChanged = originalModel.isNotEmpty() && currentModel != originalModel

    AdminSettingsForm(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        state = state,
        onSave = viewModel::save,
        onRetry = viewModel::load,
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_embedding_settings_enabled),
            description = stringResource(Res.string.admin_embedding_settings_enabled_description),
            icon = Icons.Outlined.ImageSearch,
            checked = state.bool(AdminImageEmbeddingSettingsViewModel.ENABLED_KEY),
        ) { v ->
            viewModel.setBool(AdminImageEmbeddingSettingsViewModel.ENABLED_KEY, v)
        }

        SettingSectionHeader(stringResource(Res.string.admin_face_settings_parameters_section))

        DeviceSettingDropdown(
            value = state.get(AdminImageEmbeddingSettingsViewModel.PROVIDER_KEY),
            onChange = { viewModel.set(AdminImageEmbeddingSettingsViewModel.PROVIDER_KEY, it) },
        )

        // Model version stays a free-form text field — strings like
        // "mclip-vit-b32-v1" aren't slider-friendly, and changing it
        // invalidates every existing embedding so we want the admin to
        // type it deliberately, not nudge it accidentally.
        SettingTextField(
            label = stringResource(Res.string.admin_embedding_settings_model_version),
            value = currentModel,
            supporting = stringResource(Res.string.admin_embedding_settings_model_version_hint),
        ) { viewModel.set(AdminImageEmbeddingSettingsViewModel.MODEL_VERSION_KEY, it) }

        if (modelChanged) {
            ModelVersionWarningCard()
        }

        SettingSlider(
            label = stringResource(Res.string.admin_embedding_settings_max_cosine),
            value = parseFraction(
                state.get(AdminImageEmbeddingSettingsViewModel.MAX_COSINE_DISTANCE_KEY),
                default = 0.85f,
            ),
            description = stringResource(Res.string.admin_embedding_settings_max_cosine_hint),
            onValueChange = {
                viewModel.set(
                    AdminImageEmbeddingSettingsViewModel.MAX_COSINE_DISTANCE_KEY,
                    formatFraction(it),
                )
            }
        )

        SettingSwitch(
            label = stringResource(Res.string.admin_face_settings_prefer_thumb_large),
            description = stringResource(Res.string.admin_face_settings_prefer_thumb_large_description),
            icon = Icons.Outlined.PhotoSizeSelectLarge,
            checked = state.bool(AdminImageEmbeddingSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY),
        ) { v ->
            viewModel.setBool(AdminImageEmbeddingSettingsViewModel.PREFER_THUMBNAIL_LARGE_KEY, v)
        }

        SettingSectionHeader(stringResource(Res.string.admin_face_settings_workers_section))
        SettingIntSlider(
            label = stringResource(Res.string.admin_face_settings_workers),
            value = state.get(AdminImageEmbeddingSettingsViewModel.WORKERS_KEY)
                .toIntOrNull() ?: 1,
            range = 1..32,
            onValueChange = {
                viewModel.set(
                    AdminImageEmbeddingSettingsViewModel.WORKERS_KEY,
                    it.toString(),
                )
            }
        )

        SettingSectionHeader(stringResource(Res.string.admin_face_settings_nightly_section))
        NightlyStateCard(
            enabled = state.bool(AdminImageEmbeddingSettingsViewModel.NIGHTLY_ENABLED_KEY),
            mode = state.get(AdminImageEmbeddingSettingsViewModel.NIGHTLY_MODE_KEY),
            onOpen = onOpenNightly,
        )
    }
}

@Composable
private fun ModelVersionWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.admin_embedding_settings_model_version_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
