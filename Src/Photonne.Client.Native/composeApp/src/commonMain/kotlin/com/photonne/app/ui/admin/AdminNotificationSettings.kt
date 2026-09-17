package com.photonne.app.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_notifications_categories
import com.photonne.app.resources.admin_settings_notifications_enabled
import com.photonne.app.resources.admin_settings_notifications_job_completed
import com.photonne.app.resources.admin_settings_notifications_job_failed
import com.photonne.app.resources.admin_settings_notifications_max_per_user
import com.photonne.app.resources.admin_settings_notifications_max_per_user_hint
import com.photonne.app.resources.admin_settings_notifications_retention_days
import com.photonne.app.resources.admin_settings_notifications_retention_hint
import com.photonne.app.resources.admin_settings_notifications_share_viewed
import org.jetbrains.compose.resources.stringResource

class AdminNotificationSettingsViewModel(
    repository: AdminRepository,
    errorFactory: UiErrorFactory,
) : AdminKeyValueSettingsViewModel(repository, errorFactory) {

    override val keys = listOf(
        "NotificationSettings.Enabled",
        "NotificationSettings.RetentionDays",
        "NotificationSettings.MaxPerUser",
        "NotificationSettings.JobCompleted.Enabled",
        "NotificationSettings.JobFailed.Enabled",
        "NotificationSettings.ShareViewed.Enabled"
    )

    // MaxPerUser is 0 on the server when unset: no cap. This form used to
    // show 1000 for it, a limit nobody had set and nothing enforced.
    override val defaults = mapOf(
        "NotificationSettings.Enabled" to "true",
        RETENTION_KEY to "30",
        MAX_PER_USER_KEY to "0",
        "NotificationSettings.JobCompleted.Enabled" to "true",
        "NotificationSettings.JobFailed.Enabled" to "true",
        "NotificationSettings.ShareViewed.Enabled" to "true"
    )

    override val intRanges = mapOf(
        RETENTION_KEY to 0..3650,
        MAX_PER_USER_KEY to 0..1_000_000,
    )

    companion object {
        const val RETENTION_KEY = "NotificationSettings.RetentionDays"
        const val MAX_PER_USER_KEY = "NotificationSettings.MaxPerUser"
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
        state = state,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onSavedShown = viewModel::consumeSaved,
        onDismissError = viewModel::dismissError,
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_notifications_enabled),
            checked = state.bool("NotificationSettings.Enabled")
        ) { viewModel.setBool("NotificationSettings.Enabled", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_notifications_retention_days),
            state.get(AdminNotificationSettingsViewModel.RETENTION_KEY),
            supporting = stringResource(Res.string.admin_settings_notifications_retention_hint),
            range = viewModel.intRanges[AdminNotificationSettingsViewModel.RETENTION_KEY]
        ) { viewModel.set(AdminNotificationSettingsViewModel.RETENTION_KEY, it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_notifications_max_per_user),
            state.get(AdminNotificationSettingsViewModel.MAX_PER_USER_KEY),
            supporting = stringResource(Res.string.admin_settings_notifications_max_per_user_hint),
            range = viewModel.intRanges[AdminNotificationSettingsViewModel.MAX_PER_USER_KEY]
        ) { viewModel.set(AdminNotificationSettingsViewModel.MAX_PER_USER_KEY, it) }

        SettingSectionHeader(stringResource(Res.string.admin_settings_notifications_categories))
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_notifications_job_completed),
            checked = state.bool("NotificationSettings.JobCompleted.Enabled")
        ) {
            viewModel.setBool("NotificationSettings.JobCompleted.Enabled", it)
        }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_notifications_job_failed),
            checked = state.bool("NotificationSettings.JobFailed.Enabled")
        ) {
            viewModel.setBool("NotificationSettings.JobFailed.Enabled", it)
        }
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_notifications_share_viewed),
            checked = state.bool("NotificationSettings.ShareViewed.Enabled")
        ) {
            viewModel.setBool("NotificationSettings.ShareViewed.Enabled", it)
        }
    }
}
