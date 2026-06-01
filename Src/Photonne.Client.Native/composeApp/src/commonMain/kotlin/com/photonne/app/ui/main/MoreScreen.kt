package com.photonne.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.photonne.app.PhotonneVersion
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_settings_title
import com.photonne.app.resources.action_logout
import com.photonne.app.resources.administration_title
import com.photonne.app.resources.archive_title
import com.photonne.app.resources.device_backup_title
import com.photonne.app.resources.explore_title
import com.photonne.app.resources.map_title
import com.photonne.app.resources.notifications_title
import com.photonne.app.resources.favorites_title
import com.photonne.app.resources.people_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.unsupported_files_title
import com.photonne.app.resources.upload_title
import com.photonne.app.resources.utilities_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Library shortcut shown on the More tab. Each entry resolves to a
 * subscreen in [App] (Upload, Favorites, Archive, Trash, …).
 * `badgeCount` renders a Material badge on the tile when > 0 — currently
 * only used by the Notifications tile to mirror the bottom-nav badge.
 */
private data class MoreShortcut(
    val key: String,
    val labelRes: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val badgeCount: Int = 0
)

@Composable
fun MoreScreen(
    user: UserDto,
    onLogout: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenExplore: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenUtilities: () -> Unit,
    onOpenUnsupportedFiles: () -> Unit,
    onOpenDeviceBackup: () -> Unit,
    onOpenNotifications: () -> Unit,
    notificationsUnreadCount: Int = 0,
    onOpenAccountSettings: () -> Unit,
    onOpenAdministration: (() -> Unit)? = null
) {
    val shortcuts = remember(
        onOpenUpload,
        onOpenMap,
        onOpenFavorites,
        onOpenPeople,
        onOpenExplore,
        onOpenArchived,
        onOpenTrash,
        onOpenUtilities,
        onOpenUnsupportedFiles,
        onOpenNotifications,
        notificationsUnreadCount
    ) {
        // 3×3 destination grid. Configuration entries (Copia de seguridad,
        // Configuración cuenta, Administración) live as rows below — that
        // keeps "grid = content destinations, list = configuration".
        listOf(
            // Row 1: discovery / collections
            MoreShortcut("favorites", Res.string.favorites_title, Icons.Outlined.FavoriteBorder, onOpenFavorites),
            MoreShortcut("people", Res.string.people_title, Icons.Outlined.People, onOpenPeople),
            MoreShortcut("map", Res.string.map_title, Icons.Outlined.Map, onOpenMap),
            // Row 2: library buckets
            MoreShortcut("explore", Res.string.explore_title, Icons.Outlined.Explore, onOpenExplore),
            MoreShortcut("archive", Res.string.archive_title, Icons.Outlined.Archive, onOpenArchived),
            MoreShortcut("trash", Res.string.trash_title, Icons.Outlined.Delete, onOpenTrash),
            // Row 3: ingestion / awareness
            MoreShortcut("upload", Res.string.upload_title, Icons.Outlined.AddPhotoAlternate, onOpenUpload),
            MoreShortcut(
                "notifications",
                Res.string.notifications_title,
                Icons.Outlined.Notifications,
                onOpenNotifications,
                badgeCount = notificationsUnreadCount
            ),
            MoreShortcut("utilities", Res.string.utilities_title, Icons.Outlined.Build, onOpenUtilities),
            MoreShortcut("unsupported-files", Res.string.unsupported_files_title, Icons.Outlined.FolderOff, onOpenUnsupportedFiles)
        )
    }

    // Lay the shortcuts out three-per-row by hand so the whole screen lives in
    // a single LazyColumn — otherwise a nested LazyVerticalGrid swallows the
    // page scroll and the logout/version footer is unreachable on small
    // screens.
    val shortcutRows = remember(shortcuts) { shortcuts.chunked(3) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item("header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = user.firstName?.takeIf { it.isNotBlank() } ?: user.username,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        items(shortcutRows, key = { row -> row.joinToString { it.key } }) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { shortcut ->
                    MoreShortcutCard(
                        label = stringResource(shortcut.labelRes),
                        icon = shortcut.icon,
                        onClick = shortcut.onClick,
                        badgeCount = shortcut.badgeCount,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep the last row balanced when the number of shortcuts doesn't fill it.
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        item("devicebackup") {
            Spacer(Modifier.height(4.dp))
            SettingsLikeRow(
                icon = Icons.Outlined.CloudUpload,
                label = stringResource(Res.string.device_backup_title),
                onClick = onOpenDeviceBackup
            )
        }

        item("account-settings") {
            SettingsLikeRow(
                icon = Icons.Outlined.Settings,
                label = stringResource(Res.string.account_settings_title),
                onClick = onOpenAccountSettings
            )
        }

        onOpenAdministration?.let { handler ->
            item("administration") {
                SettingsLikeRow(
                    icon = Icons.Outlined.AdminPanelSettings,
                    label = stringResource(Res.string.administration_title),
                    onClick = handler
                )
            }
        }

        item("logout") {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(onClick = onLogout) { Text(stringResource(Res.string.action_logout)) }
            }
        }

        item("version") {
            Text(
                text = "Photonne v$PhotonneVersion",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
private fun SettingsLikeRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconPill(icon = icon)
            Spacer(Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreShortcutCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge {
                            Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                        }
                    }
                ) {
                    IconPill(icon = icon)
                }
            } else {
                IconPill(icon = icon)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/** Circular tinted badge for shortcut and settings icons — mirrors the PWA's brand-accented icons. */
@Composable
private fun IconPill(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp)
        )
    }
}
