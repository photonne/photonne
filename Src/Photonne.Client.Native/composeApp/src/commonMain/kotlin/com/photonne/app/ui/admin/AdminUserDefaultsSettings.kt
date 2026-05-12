package com.photonne.app.ui.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.photonne.app.data.admin.AdminRepository
import com.photonne.app.resources.Res
import com.photonne.app.resources.admin_settings_user_defaults_active
import com.photonne.app.resources.admin_settings_user_defaults_quota_gb
import com.photonne.app.resources.admin_settings_user_defaults_quota_hint
import com.photonne.app.resources.admin_settings_user_defaults_role
import com.photonne.app.resources.admin_user_role_admin
import com.photonne.app.resources.admin_user_role_user
import org.jetbrains.compose.resources.stringResource

class AdminUserDefaultsViewModel(
    repository: AdminRepository
) : AdminKeyValueSettingsViewModel(repository) {

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

    override fun normalize(key: String, value: String): String =
        if (key == "UserSettings.DefaultStorageQuotaGb") value.filter { it.isDigit() } else value
}

@Composable
fun AdminUserDefaultsScreen(viewModel: AdminUserDefaultsViewModel) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    val roleOptions = listOf(
        "User" to stringResource(Res.string.admin_user_role_user),
        "Admin" to stringResource(Res.string.admin_user_role_admin)
    )

    AdminSettingsForm(
        isLoading = state.isLoading,
        isSubmitting = state.isSubmitting,
        errorMessage = state.errorMessage,
        successMessage = state.successMessage,
        canSave = state.canSave,
        onSave = viewModel::save
    ) {
        SettingSwitch(
            label = stringResource(Res.string.admin_settings_user_defaults_active),
            checked = state.get("UserSettings.DefaultIsActive").equals("true", true)
        ) { viewModel.set("UserSettings.DefaultIsActive", if (it) "true" else "false") }
        SettingDropdown(
            label = stringResource(Res.string.admin_settings_user_defaults_role),
            value = state.get("UserSettings.DefaultRole").ifBlank { "User" },
            options = roleOptions
        ) { viewModel.set("UserSettings.DefaultRole", it) }
        SettingNumberField(
            stringResource(Res.string.admin_settings_user_defaults_quota_gb),
            state.get("UserSettings.DefaultStorageQuotaGb"),
            supporting = stringResource(Res.string.admin_settings_user_defaults_quota_hint)
        ) { viewModel.set("UserSettings.DefaultStorageQuotaGb", it) }
    }
}
