package com.photonne.app.ui.admin

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.data.error.UiErrorFactory
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_user_defaults_active
import com.photonne.app.resources.admin_settings_user_defaults_quota_gb
import com.photonne.app.resources.admin_settings_user_defaults_quota_hint
import com.photonne.app.resources.admin_settings_user_defaults_role
import com.photonne.app.resources.admin_user_role_admin
import com.photonne.app.resources.admin_user_role_user
import org.jetbrains.compose.resources.stringResource

class AdminUserDefaultsViewModel(
    repository: AdminRepository,
    errorFactory: UiErrorFactory,
) : AdminKeyValueSettingsViewModel(repository, errorFactory) {

    override val keys = listOf(
        "UserSettings.DefaultIsActive",
        "UserSettings.DefaultRole",
        "UserSettings.DefaultStorageQuotaGb"
    )

    override val defaults = mapOf(
        "UserSettings.DefaultIsActive" to "true",
        "UserSettings.DefaultRole" to "User",
        "UserSettings.DefaultStorageQuotaGb" to "0"
    )

    override val intRanges = mapOf("UserSettings.DefaultStorageQuotaGb" to 0..1_000_000)
}

@Composable
fun AdminUserDefaultsScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: AdminUserDefaultsViewModel,
    onChromeVisibleChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val roleOptions = listOf(
        "User" to stringResource(Res.string.admin_user_role_user),
        "Admin" to stringResource(Res.string.admin_user_role_admin)
    )

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
            label = stringResource(Res.string.admin_settings_user_defaults_active),
            checked = state.bool("UserSettings.DefaultIsActive")
        ) { viewModel.setBool("UserSettings.DefaultIsActive", it) }
        SettingDropdown(
            label = stringResource(Res.string.admin_settings_user_defaults_role),
            value = state.get("UserSettings.DefaultRole").ifBlank { "User" },
            options = roleOptions
        ) { viewModel.set("UserSettings.DefaultRole", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_user_defaults_quota_gb),
            state.get("UserSettings.DefaultStorageQuotaGb"),
            supporting = stringResource(Res.string.admin_settings_user_defaults_quota_hint),
            range = viewModel.intRanges["UserSettings.DefaultStorageQuotaGb"]
        ) { viewModel.set("UserSettings.DefaultStorageQuotaGb", it) }
    }
}
