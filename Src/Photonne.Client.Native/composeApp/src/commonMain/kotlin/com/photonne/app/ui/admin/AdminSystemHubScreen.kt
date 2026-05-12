package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
            Icons.Filled.Refresh
        ),
        AdminHubEntry(
            AdminSystemEntry.ExtractMetadata.name,
            stringResource(Res.string.admin_system_metadata),
            stringResource(Res.string.admin_system_metadata_subtitle),
            Icons.Filled.Info
        ),
        AdminHubEntry(
            AdminSystemEntry.GenerateThumbnails.name,
            stringResource(Res.string.admin_system_thumbnails),
            stringResource(Res.string.admin_system_thumbnails_subtitle),
            Icons.Filled.Create
        ),
        AdminHubEntry(
            AdminSystemEntry.DetectDuplicates.name,
            stringResource(Res.string.admin_system_duplicates),
            stringResource(Res.string.admin_system_duplicates_subtitle),
            Icons.Filled.List
        ),
        AdminHubEntry(
            AdminSystemEntry.FaceRecognition.name,
            stringResource(Res.string.admin_system_face),
            stringResource(Res.string.admin_system_face_subtitle),
            Icons.Filled.Face
        ),
        AdminHubEntry(
            AdminSystemEntry.ObjectDetection.name,
            stringResource(Res.string.admin_system_object),
            stringResource(Res.string.admin_system_object_subtitle),
            Icons.Filled.Star
        ),
        AdminHubEntry(
            AdminSystemEntry.SceneClassification.name,
            stringResource(Res.string.admin_system_scene),
            stringResource(Res.string.admin_system_scene_subtitle),
            Icons.Filled.LocationOn
        ),
        AdminHubEntry(
            AdminSystemEntry.TextRecognition.name,
            stringResource(Res.string.admin_system_text),
            stringResource(Res.string.admin_system_text_subtitle),
            Icons.Filled.Email
        ),
        AdminHubEntry(
            AdminSystemEntry.ImageEmbedding.name,
            stringResource(Res.string.admin_system_embedding),
            stringResource(Res.string.admin_system_embedding_subtitle),
            Icons.Filled.Search
        ),
        AdminHubEntry(
            AdminSystemEntry.Maintenance.name,
            stringResource(Res.string.admin_system_maintenance),
            stringResource(Res.string.admin_system_maintenance_subtitle),
            Icons.Filled.Build
        ),
        AdminHubEntry(
            AdminSystemEntry.Backup.name,
            stringResource(Res.string.admin_system_backup),
            stringResource(Res.string.admin_system_backup_subtitle),
            Icons.Filled.Lock
        )
    )

    AdminHubList(
        entries = entries,
        onClick = { key -> onOpen(AdminSystemEntry.valueOf(key)) }
    )
}
