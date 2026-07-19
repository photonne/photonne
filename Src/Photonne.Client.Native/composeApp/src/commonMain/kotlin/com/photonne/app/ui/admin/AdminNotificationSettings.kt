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
import com.photonne.app.resources.admin_settings_notifications_categories
import com.photonne.app.resources.admin_settings_notifications_enabled
import com.photonne.app.resources.admin_settings_notifications_job_completed
import com.photonne.app.resources.admin_settings_notifications_job_failed
import com.photonne.app.resources.admin_settings_notifications_max_per_user
import com.photonne.app.resources.admin_settings_notifications_retention_days
import com.photonne.app.resources.admin_settings_notifications_share_viewed
import org.jetbrains.compose.resources.stringResource

class AdminNotificationSettingsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

    override val keys = listOf(
        "NotificationSettings.Enabled",
        "NotificationSettings.RetentionDays",
        "NotificationSettings.MaxPerUser",
        "NotificationSettings.JobCompleted.Enabled",
        "NotificationSettings.JobFailed.Enabled",
        "NotificationSettings.ShareViewed.Enabled"
    )

    override val defaults = mapOf(
        "NotificationSettings.Enabled" to "true",
        "NotificationSettings.RetentionDays" to "30",
        "NotificationSettings.MaxPerUser" to "1000",
        "NotificationSettings.JobCompleted.Enabled" to "true",
        "NotificationSettings.JobFailed.Enabled" to "true",
        "NotificationSettings.ShareViewed.Enabled" to "true"
    )

    override fun normalize(key: String, value: String): String = when (key) {
        "NotificationSettings.RetentionDays", "NotificationSettings.MaxPerUser" ->
            value.filter { it.isDigit() }
        else -> value
    }
}

@Composable
fun AdminNotificationSettingsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminNotificationSettingsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    AdminSettingsForm(
        title = title,
        onBack = onBack,
        onChromeVisibleChange = onChromeVisibleChange,
        isLoading = state.isLoading,
        isSubmitting = state.isSubmitting,
        errorMessage = state.errorMessage,
        successMessage = state.successMessage,
        canSave = state.canSave,
        onSave = viewModel::save
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_notifications_enabled),
            checked = state.get("NotificationSettings.Enabled").equals("true", true)
        ) { viewModel.set("NotificationSettings.Enabled", if (it) "true" else "false") }
        SettingNumberField(
            stringResource(Res.string.admin_settings_notifications_retention_days),
            state.get("NotificationSettings.RetentionDays")
        ) { viewModel.set("NotificationSettings.RetentionDays", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_notifications_max_per_user),
            state.get("NotificationSettings.MaxPerUser")
        ) { viewModel.set("NotificationSettings.MaxPerUser", it) }

        HorizontalDivider()
        Text(
            stringResource(Res.string.admin_settings_notifications_categories),
            style = MaterialTheme.typography.titleSmall
        )
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_notifications_job_completed),
            checked = state.get("NotificationSettings.JobCompleted.Enabled").equals("true", true)
        ) {
            viewModel.set(
                "NotificationSettings.JobCompleted.Enabled",
                if (it) "true" else "false"
            )
        }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_notifications_job_failed),
            checked = state.get("NotificationSettings.JobFailed.Enabled").equals("true", true)
        ) {
            viewModel.set(
                "NotificationSettings.JobFailed.Enabled",
                if (it) "true" else "false"
            )
        }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_notifications_share_viewed),
            checked = state.get("NotificationSettings.ShareViewed.Enabled").equals("true", true)
        ) {
            viewModel.set(
                "NotificationSettings.ShareViewed.Enabled",
                if (it) "true" else "false"
            )
        }
    }
}
