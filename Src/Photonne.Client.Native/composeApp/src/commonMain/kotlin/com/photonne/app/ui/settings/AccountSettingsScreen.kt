package com.photonne.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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
import com.photonne.app.ui.main.SubscreenFloatingChrome
import com.photonne.app.ui.main.SubscreenScroll
import com.photonne.app.ui.main.floatingNavBarReservedHeight
import com.photonne.app.ui.main.subscreenChromeReservedTop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.theme.contentWidth
import com.photonne.app.ui.theme.Spacing

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
    val reservedTop = subscreenChromeReservedTop()
    val hazeState = remember { HazeState() }
    val scrollState = rememberScrollState()
    val entries = listOf(
        SettingsEntry(
            AccountSettingsSection.Profile,
            Res.string.account_section_profile,
            Res.string.account_section_profile_subtitle,
            Icons.Outlined.Person
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .hazeSource(hazeState)
                .contentWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp + reservedTop, bottom = 16.dp + floatingNavBarReservedHeight()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            entries.forEach { entry ->
                SettingsRow(entry = entry, onClick = { onOpen(entry.section) })
            }
            if (activityNotificationsEnabled != null) {
                ActivityNotificationsRow(
                    enabled = activityNotificationsEnabled,
                    onChange = onActivityNotificationsChange
                )
            }
            Spacer(Modifier.height(Spacing.sm))
        }
        SubscreenFloatingChrome(
            title = title,
            onBack = onBack,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { if (scrollState.value > 0) 1 else 0 },
                firstVisibleItemScrollOffset = { scrollState.value },
                isScrollInProgress = { scrollState.isScrollInProgress },
                scrollToTopMinIndex = 1,
                onScrollToTop = { scrollState.animateScrollTo(0) }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange
        )
    }
}

@Composable
private fun SettingsRow(entry: SettingsEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(entry.title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(entry.subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(contentAlignment = Alignment.CenterEnd) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { toggle(!enabled) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.settings_activity_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(Res.string.settings_activity_notifications_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = { toggle(it) })
        }
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
}
