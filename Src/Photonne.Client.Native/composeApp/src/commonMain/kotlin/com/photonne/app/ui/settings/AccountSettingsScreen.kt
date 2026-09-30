package com.photonne.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.photonne.app.data.models.StorageInfoDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.storage_quota_summary
import com.photonne.app.resources.storage_used_unbounded_format
import com.photonne.app.ui.format.humanBytes
import com.photonne.app.ui.theme.PhotonneColors
import com.photonne.app.ui.theme.ProgressHeight
import kotlin.math.roundToInt
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
    /**
     * Uso de `users/me/storage` para resumirlo en la fila de Almacenamiento
     * (mini barra + "62 % de 100 GB"). Null mientras carga o si falló: la
     * fila se queda con su subtítulo de siempre.
     */
    storage: StorageInfoDto? = null,
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
                    val usage = storage.takeIf { entry.section == AccountSettingsSection.Storage }
                    SettingsItem(
                        headline = stringResource(entry.title),
                        supporting = usage?.let { storageSummary(it) }
                            ?: stringResource(entry.subtitle),
                        leadingIcon = entry.icon,
                        onClick = { onOpen(entry.section) },
                        showDivider = index > 0,
                        supportingContent = usage?.let { info ->
                            storageFraction(info)?.let { fraction ->
                                { StorageQuotaBar(fraction) }
                            }
                        }
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

/** Fracción de la cuota usada, o null si no hay cuota. */
private fun storageFraction(info: StorageInfoDto): Float? =
    info.quotaBytes?.takeIf { it > 0 }?.let { quota ->
        (info.usedBytes.toDouble() / quota.toDouble()).toFloat().coerceIn(0f, 1f)
    }

/** "62 % de 100 GB" con cuota; "12,4 GB usados" sin ella. */
@Composable
private fun storageSummary(info: StorageInfoDto): String {
    val quota = info.quotaBytes?.takeIf { it > 0 }
    return if (quota != null) {
        val percent = ((info.usedBytes.toDouble() / quota.toDouble()) * 100)
            .roundToInt().coerceIn(0, 100)
        stringResource(Res.string.storage_quota_summary, percent, humanBytes(quota))
    } else {
        stringResource(Res.string.storage_used_unbounded_format, humanBytes(info.usedBytes))
    }
}

/**
 * Mini barra de cuota bajo el resumen: primaria y en `warning` desde el 90 %,
 * cuando ya conviene liberar espacio antes de que la copia se corte.
 */
@Composable
private fun StorageQuotaBar(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .padding(top = Spacing.xs)
            .fillMaxWidth()
            .height(ProgressHeight.inline),
        color = if (fraction >= 0.9f) PhotonneColors.warning else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surface
    )
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
