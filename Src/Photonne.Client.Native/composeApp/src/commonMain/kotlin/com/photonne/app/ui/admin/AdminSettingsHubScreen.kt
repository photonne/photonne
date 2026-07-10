package com.photonne.app.ui.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Update
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
import com.photonne.app.resources.admin_shared_trash
import com.photonne.app.resources.admin_shared_trash_subtitle
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
            AdminSettingsEntry.FaceRecognition.name,
            stringResource(Res.string.admin_settings_face_recognition),
            stringResource(Res.string.admin_settings_face_recognition_subtitle),
            Icons.Outlined.Face
        ),
        AdminHubEntry(
            AdminSettingsEntry.ObjectDetection.name,
            stringResource(Res.string.admin_settings_object_detection),
            stringResource(Res.string.admin_settings_object_detection_subtitle),
            Icons.Outlined.Category
        ),
        AdminHubEntry(
            AdminSettingsEntry.SceneClassification.name,
            stringResource(Res.string.admin_settings_scene_classification),
            stringResource(Res.string.admin_settings_scene_classification_subtitle),
            Icons.Outlined.Landscape
        ),
        AdminHubEntry(
            AdminSettingsEntry.TextRecognition.name,
            stringResource(Res.string.admin_settings_text_recognition),
            stringResource(Res.string.admin_settings_text_recognition_subtitle),
            Icons.Outlined.TextFields
        ),
        AdminHubEntry(
            AdminSettingsEntry.ImageEmbedding.name,
            stringResource(Res.string.admin_settings_image_embedding),
            stringResource(Res.string.admin_settings_image_embedding_subtitle),
            Icons.Outlined.ImageSearch
        ),
        AdminHubEntry(
            AdminSettingsEntry.ImageSettings.name,
            stringResource(Res.string.admin_settings_image),
            stringResource(Res.string.admin_settings_image_subtitle),
            Icons.Outlined.Image
        ),
        AdminHubEntry(
            AdminSettingsEntry.Metadata.name,
            stringResource(Res.string.admin_settings_metadata),
            stringResource(Res.string.admin_settings_metadata_subtitle),
            Icons.Outlined.Info
        ),
        AdminHubEntry(
            AdminSettingsEntry.NightlyTasks.name,
            stringResource(Res.string.admin_settings_nightly),
            stringResource(Res.string.admin_settings_nightly_subtitle),
            Icons.Outlined.NightsStay
        ),
        AdminHubEntry(
            AdminSettingsEntry.Notifications.name,
            stringResource(Res.string.admin_settings_notifications),
            stringResource(Res.string.admin_settings_notifications_subtitle),
            Icons.Outlined.NotificationsNone
        ),
        AdminHubEntry(
            AdminSettingsEntry.Server.name,
            stringResource(Res.string.admin_settings_server),
            stringResource(Res.string.admin_settings_server_subtitle),
            Icons.Outlined.Dns
        ),
        AdminHubEntry(
            AdminSettingsEntry.Trash.name,
            stringResource(Res.string.admin_settings_trash),
            stringResource(Res.string.admin_settings_trash_subtitle),
            Icons.Outlined.Delete
        ),
        AdminHubEntry(
            AdminSettingsEntry.UserDefaults.name,
            stringResource(Res.string.admin_settings_user_defaults),
            stringResource(Res.string.admin_settings_user_defaults_subtitle),
            Icons.Outlined.ManageAccounts
        ),
        AdminHubEntry(
            AdminSettingsEntry.VersionCheck.name,
            stringResource(Res.string.admin_settings_version),
            stringResource(Res.string.admin_settings_version_subtitle),
            Icons.Outlined.Update
        )
    )

    AdminHubList(
        entries = entries,
        onClick = { key -> onOpen(AdminSettingsEntry.valueOf(key)) }
    )
}
