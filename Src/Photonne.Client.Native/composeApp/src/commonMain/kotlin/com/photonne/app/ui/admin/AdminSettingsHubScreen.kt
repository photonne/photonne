package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_face_recognition
import com.photonne.app.resources.admin_settings_face_recognition_subtitle
import com.photonne.app.resources.admin_settings_image
import com.photonne.app.resources.admin_settings_image_embedding
import com.photonne.app.resources.admin_settings_image_embedding_subtitle
import com.photonne.app.resources.admin_settings_image_subtitle
import com.photonne.app.resources.admin_settings_metadata
import com.photonne.app.resources.admin_settings_metadata_subtitle
import com.photonne.app.resources.admin_settings_nightly
import com.photonne.app.resources.admin_settings_nightly_subtitle
import com.photonne.app.resources.admin_settings_notifications
import com.photonne.app.resources.admin_settings_notifications_subtitle
import com.photonne.app.resources.admin_settings_object_detection
import com.photonne.app.resources.admin_settings_object_detection_subtitle
import com.photonne.app.resources.admin_settings_scene_classification
import com.photonne.app.resources.admin_settings_scene_classification_subtitle
import com.photonne.app.resources.admin_settings_server
import com.photonne.app.resources.admin_settings_server_subtitle
import com.photonne.app.resources.admin_settings_tasks
import com.photonne.app.resources.admin_settings_tasks_subtitle
import com.photonne.app.resources.admin_settings_text_recognition
import com.photonne.app.resources.admin_settings_text_recognition_subtitle
import com.photonne.app.resources.admin_settings_trash
import com.photonne.app.resources.admin_settings_trash_subtitle
import com.photonne.app.resources.admin_settings_user_defaults
import com.photonne.app.resources.admin_settings_user_defaults_subtitle
import com.photonne.app.resources.admin_settings_version
import com.photonne.app.resources.admin_settings_version_subtitle
import org.jetbrains.compose.resources.stringResource

enum class AdminSettingsEntry {
    Tasks,
    FaceRecognition,
    ObjectDetection,
    SceneClassification,
    TextRecognition,
    ImageEmbedding,
    ImageSettings,
    Metadata,
    NightlyTasks,
    Notifications,
    Server,
    Trash,
    UserDefaults,
    VersionCheck
}

@Composable
fun AdminSettingsHubScreen(onOpen: (AdminSettingsEntry) -> Unit) {
    val entries = listOf(
        AdminHubEntry(
            AdminSettingsEntry.Tasks.name,
            stringResource(Res.string.admin_settings_tasks),
            stringResource(Res.string.admin_settings_tasks_subtitle),
            Icons.Filled.Build
        ),
        AdminHubEntry(
            AdminSettingsEntry.FaceRecognition.name,
            stringResource(Res.string.admin_settings_face_recognition),
            stringResource(Res.string.admin_settings_face_recognition_subtitle),
            Icons.Filled.Face
        ),
        AdminHubEntry(
            AdminSettingsEntry.ObjectDetection.name,
            stringResource(Res.string.admin_settings_object_detection),
            stringResource(Res.string.admin_settings_object_detection_subtitle),
            Icons.Filled.Star
        ),
        AdminHubEntry(
            AdminSettingsEntry.SceneClassification.name,
            stringResource(Res.string.admin_settings_scene_classification),
            stringResource(Res.string.admin_settings_scene_classification_subtitle),
            Icons.Filled.Image
        ),
        AdminHubEntry(
            AdminSettingsEntry.TextRecognition.name,
            stringResource(Res.string.admin_settings_text_recognition),
            stringResource(Res.string.admin_settings_text_recognition_subtitle),
            Icons.Filled.Email
        ),
        AdminHubEntry(
            AdminSettingsEntry.ImageEmbedding.name,
            stringResource(Res.string.admin_settings_image_embedding),
            stringResource(Res.string.admin_settings_image_embedding_subtitle),
            Icons.Filled.Search
        ),
        AdminHubEntry(
            AdminSettingsEntry.ImageSettings.name,
            stringResource(Res.string.admin_settings_image),
            stringResource(Res.string.admin_settings_image_subtitle),
            Icons.Filled.Image
        ),
        AdminHubEntry(
            AdminSettingsEntry.Metadata.name,
            stringResource(Res.string.admin_settings_metadata),
            stringResource(Res.string.admin_settings_metadata_subtitle),
            Icons.Filled.Place
        ),
        AdminHubEntry(
            AdminSettingsEntry.NightlyTasks.name,
            stringResource(Res.string.admin_settings_nightly),
            stringResource(Res.string.admin_settings_nightly_subtitle),
            Icons.Filled.DateRange
        ),
        AdminHubEntry(
            AdminSettingsEntry.Notifications.name,
            stringResource(Res.string.admin_settings_notifications),
            stringResource(Res.string.admin_settings_notifications_subtitle),
            Icons.Filled.Email
        ),
        AdminHubEntry(
            AdminSettingsEntry.Server.name,
            stringResource(Res.string.admin_settings_server),
            stringResource(Res.string.admin_settings_server_subtitle),
            Icons.Filled.Settings
        ),
        AdminHubEntry(
            AdminSettingsEntry.Trash.name,
            stringResource(Res.string.admin_settings_trash),
            stringResource(Res.string.admin_settings_trash_subtitle),
            Icons.Filled.Delete
        ),
        AdminHubEntry(
            AdminSettingsEntry.UserDefaults.name,
            stringResource(Res.string.admin_settings_user_defaults),
            stringResource(Res.string.admin_settings_user_defaults_subtitle),
            Icons.Filled.Person
        ),
        AdminHubEntry(
            AdminSettingsEntry.VersionCheck.name,
            stringResource(Res.string.admin_settings_version),
            stringResource(Res.string.admin_settings_version_subtitle),
            Icons.Filled.Info
        )
    )

    AdminHubList(
        entries = entries,
        onClick = { key -> onOpen(AdminSettingsEntry.valueOf(key)) }
    )
}
