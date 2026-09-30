package com.photonne.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_section_appearance
import com.photonne.app.resources.account_section_connection
import com.photonne.app.resources.account_section_connection_subtitle
import com.photonne.app.resources.account_section_appearance_subtitle
import com.photonne.app.resources.account_section_profile
import com.photonne.app.resources.account_section_profile_subtitle
import com.photonne.app.resources.account_section_security
import com.photonne.app.resources.account_section_security_subtitle
import com.photonne.app.resources.account_section_storage
import com.photonne.app.resources.account_section_storage_subtitle
import com.photonne.app.resources.backup_notifications_allow
import com.photonne.app.resources.backup_notifications_open_settings
import com.photonne.app.resources.settings_activity_notifications
import com.photonne.app.resources.settings_activity_notifications_denied
import com.photonne.app.resources.settings_activity_notifications_subtitle
import com.photonne.app.data.devicebackup.rememberNotificationPermission
import com.photonne.app.ui.main.FormPageScaffold
import com.photonne.app.ui.theme.PhotonneIcons
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.SettingsItem
import com.photonne.app.ui.theme.SettingsTrailing
import com.photonne.app.ui.theme.Spacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class AccountSettingsSection { Profile, Security, Appearance, Storage, Connection }

private data class SettingsEntry(
    val section: AccountSettingsSection,
    val title: StringResource,
    val subtitle: StringResource,
    val icon: ImageVector
)

@Composable
fun AccountSettingsScreen(
    title: String,
    onBack: () -> Unit,
    onOpen: (AccountSettingsSection) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
    /**
     * "Avisos de actividad" (notificación del sistema con lo nuevo del servidor
     * con la app cerrada). Null donde la plataforma no lo tiene: no sale.
     */
    activityNotificationsEnabled: Boolean? = null,
    onActivityNotificationsChange: (Boolean) -> Unit = {},
) {
    val entries = listOf(
        SettingsEntry(
            AccountSettingsSection.Profile,
            Res.string.account_section_profile,
            Res.string.account_section_profile_subtitle,
            PhotonneIcons.Person
        ),
        SettingsEntry(
            AccountSettingsSection.Security,
            Res.string.account_section_security,
            Res.string.account_section_security_subtitle,
            Icons.Outlined.Lock
        ),
        SettingsEntry(
            AccountSettingsSection.Appearance,
            Res.string.account_section_appearance,
            Res.string.account_section_appearance_subtitle,
            Icons.Outlined.Palette
        ),
        SettingsEntry(
            AccountSettingsSection.Storage,
            Res.string.account_section_storage,
            Res.string.account_section_storage_subtitle,
            Icons.Outlined.Storage
        ),
        SettingsEntry(
            AccountSettingsSection.Connection,
            Res.string.account_section_connection,
            Res.string.account_section_connection_subtitle,
            Icons.Outlined.Dns
        )
    )

    FormPageScaffold(title = title, onBack = onBack, onChromeVisibleChange = onChromeVisibleChange) { page ->
        page {
            SettingsGroup {
                entries.forEachIndexed { index, entry ->
                    SettingsItem(
                        headline = stringResource(entry.title),
                        supporting = stringResource(entry.subtitle),
                        leadingIcon = entry.icon,
                        onClick = { onOpen(entry.section) },
                        showDivider = index > 0
                    )
                }
                if (activityNotificationsEnabled != null) {
                    ActivityNotificationsRow(
                        enabled = activityNotificationsEnabled,
                        onChange = onActivityNotificationsChange
                    )
                }
            }
        }
    }
}

/**
 * Interruptor en la misma tarjeta que el resto de secciones. Activarlo pide el
 * permiso de notificaciones (Android 13+) en ese momento; si falta, lo dice
 * debajo con el botón para concederlo.
 */
@Composable
private fun ActivityNotificationsRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val permission = rememberNotificationPermission()
    val toggle = { value: Boolean ->
        onChange(value)
        if (value && !permission.isGranted) permission.request()
    }
    SettingsItem(
        headline = stringResource(Res.string.settings_activity_notifications),
        supporting = stringResource(Res.string.settings_activity_notifications_subtitle),
        leadingIcon = Icons.Outlined.NotificationsActive,
        trailing = SettingsTrailing.Toggle(checked = enabled, onCheckedChange = toggle),
        showDivider = true
    )
    if (enabled && !permission.isGranted) {
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm)
        ) {
            Text(
                stringResource(Res.string.settings_activity_notifications_denied),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            val openSettings = permission.openSystemSettings
            TextButton(onClick = openSettings ?: permission.request) {
                Text(
                    stringResource(
                        if (openSettings != null) Res.string.backup_notifications_open_settings
                        else Res.string.backup_notifications_allow
                    )
                )
            }
        }
    }
}
