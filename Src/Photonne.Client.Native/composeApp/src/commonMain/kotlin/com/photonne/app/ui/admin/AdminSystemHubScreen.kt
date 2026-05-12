package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.runtime.Composable
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_system_backup
import com.photonne.app.resources.admin_system_backup_subtitle
import com.photonne.app.resources.admin_system_duplicates
import com.photonne.app.resources.admin_system_duplicates_subtitle
import com.photonne.app.resources.admin_system_embedding
import com.photonne.app.resources.admin_system_embedding_subtitle
import com.photonne.app.resources.admin_system_face
import com.photonne.app.resources.admin_system_face_subtitle
import com.photonne.app.resources.admin_system_index
import com.photonne.app.resources.admin_system_index_subtitle
import com.photonne.app.resources.admin_system_maintenance
import com.photonne.app.resources.admin_system_maintenance_subtitle
import com.photonne.app.resources.admin_system_metadata
import com.photonne.app.resources.admin_system_metadata_subtitle
import com.photonne.app.resources.admin_system_object
import com.photonne.app.resources.admin_system_object_subtitle
import com.photonne.app.resources.admin_system_scene
import com.photonne.app.resources.admin_system_scene_subtitle
import com.photonne.app.resources.admin_system_text
import com.photonne.app.resources.admin_system_text_subtitle
import com.photonne.app.resources.admin_system_thumbnails
import com.photonne.app.resources.admin_system_thumbnails_subtitle
import org.jetbrains.compose.resources.stringResource

enum class AdminSystemEntry {
    IndexAssets,
    ExtractMetadata,
    GenerateThumbnails,
    DetectDuplicates,
    FaceRecognition,
    ObjectDetection,
    SceneClassification,
    TextRecognition,
    ImageEmbedding,
    Maintenance,
    Backup
}

@Composable
fun AdminSystemHubScreen(onOpen: (AdminSystemEntry) -> Unit) {
    val entries = listOf(
        AdminHubEntry(
            AdminSystemEntry.IndexAssets.name,
            stringResource(Res.string.admin_system_index),
            stringResource(Res.string.admin_system_index_subtitle),
            Icons.Outlined.Sync
        ),
        AdminHubEntry(
            AdminSystemEntry.ExtractMetadata.name,
            stringResource(Res.string.admin_system_metadata),
            stringResource(Res.string.admin_system_metadata_subtitle),
            Icons.Outlined.Info
        ),
        AdminHubEntry(
            AdminSystemEntry.GenerateThumbnails.name,
            stringResource(Res.string.admin_system_thumbnails),
            stringResource(Res.string.admin_system_thumbnails_subtitle),
            Icons.Outlined.Image
        ),
        AdminHubEntry(
            AdminSystemEntry.DetectDuplicates.name,
            stringResource(Res.string.admin_system_duplicates),
            stringResource(Res.string.admin_system_duplicates_subtitle),
            Icons.Outlined.ContentCopy
        ),
        AdminHubEntry(
            AdminSystemEntry.FaceRecognition.name,
            stringResource(Res.string.admin_system_face),
            stringResource(Res.string.admin_system_face_subtitle),
            Icons.Outlined.Face
        ),
        AdminHubEntry(
            AdminSystemEntry.ObjectDetection.name,
            stringResource(Res.string.admin_system_object),
            stringResource(Res.string.admin_system_object_subtitle),
            Icons.Outlined.Category
        ),
        AdminHubEntry(
            AdminSystemEntry.SceneClassification.name,
            stringResource(Res.string.admin_system_scene),
            stringResource(Res.string.admin_system_scene_subtitle),
            Icons.Outlined.Landscape
        ),
        AdminHubEntry(
            AdminSystemEntry.TextRecognition.name,
            stringResource(Res.string.admin_system_text),
            stringResource(Res.string.admin_system_text_subtitle),
            Icons.Outlined.TextFields
        ),
        AdminHubEntry(
            AdminSystemEntry.ImageEmbedding.name,
            stringResource(Res.string.admin_system_embedding),
            stringResource(Res.string.admin_system_embedding_subtitle),
            Icons.Outlined.ImageSearch
        ),
        AdminHubEntry(
            AdminSystemEntry.Maintenance.name,
            stringResource(Res.string.admin_system_maintenance),
            stringResource(Res.string.admin_system_maintenance_subtitle),
            Icons.Outlined.Build
        ),
        AdminHubEntry(
            AdminSystemEntry.Backup.name,
            stringResource(Res.string.admin_system_backup),
            stringResource(Res.string.admin_system_backup_subtitle),
            Icons.Outlined.Backup
        )
    )

    AdminHubList(
        entries = entries,
        onClick = { key -> onOpen(AdminSystemEntry.valueOf(key)) }
    )
}
