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
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.PhotonneVersion
import com.photonne.app.data.models.Attribution
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.account_settings_title
import com.photonne.app.resources.action_logout
import com.photonne.app.resources.administration_title
import com.photonne.app.resources.archive_title
import com.photonne.app.resources.device_backup_title
import com.photonne.app.resources.more_section_actions
import com.photonne.app.resources.more_section_manage
import com.photonne.app.resources.notifications_title
import com.photonne.app.resources.favorites_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.unsupported_files_title
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

/** A titled group of [MoreShortcut]s rendered as its own card grid with a
 *  fixed number of [columns] (so each section can balance its own row). */
private data class MoreSection(
    val key: String,
    val titleRes: StringResource,
    val columns: Int,
    val shortcuts: List<MoreShortcut>
)

@Composable
fun MoreScreen(
    user: UserDto,
    onLogout: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenUtilities: () -> Unit,
    onOpenUnsupportedFiles: () -> Unit,
    onOpenDeviceBackup: () -> Unit,
    onOpenNotifications: () -> Unit,
    notificationsUnreadCount: Int = 0,
    onOpenAccountSettings: () -> Unit,
    onOpenAdministration: (() -> Unit)? = null,
    /** Third-party data notices from the server, shown under the version. Empty
     *  when the server bundles none — see [com.photonne.app.data.models.Attribution]. */
    attributions: List<Attribution> = emptyList()
) {
    // Shortcuts grouped into titled sections (Gestión / Acciones) so the grid
    // reads cleanly instead of one ragged block; each section lays its own
    // cards 3-per-row. Upload lives in the top bar (it's an action, not a
    // destination), and "Otros archivos" is a link under the Gestión grid.
    val sections = remember(
        onOpenFavorites,
        onOpenArchived,
        onOpenTrash,
        onOpenUtilities,
        onOpenDeviceBackup,
        onOpenNotifications,
        notificationsUnreadCount
    ) {
        listOf(
            // Browsing by grouping (People / Map / Scenes / Objects) now lives
            // in the Albums tab's "Explorar" row, so the More menu is just
            // management + actions + settings.
            MoreSection(
                key = "manage",
                titleRes = Res.string.more_section_manage,
                columns = 3,
                shortcuts = listOf(
                    MoreShortcut("favorites", Res.string.favorites_title, Icons.Outlined.FavoriteBorder, onOpenFavorites),
                    MoreShortcut("archive", Res.string.archive_title, Icons.Outlined.Archive, onOpenArchived),
                    MoreShortcut("trash", Res.string.trash_title, Icons.Outlined.Delete, onOpenTrash)
                )
            ),
            MoreSection(
                key = "actions",
                titleRes = Res.string.more_section_actions,
                columns = 3,
                shortcuts = listOf(
                    MoreShortcut("device-backup", Res.string.device_backup_title, Icons.Outlined.CloudUpload, onOpenDeviceBackup),
                    MoreShortcut(
                        "notifications",
                        Res.string.notifications_title,
                        Icons.Outlined.Notifications,
                        onOpenNotifications,
                        badgeCount = notificationsUnreadCount
                    ),
                    MoreShortcut("utilities", Res.string.utilities_title, Icons.Outlined.Build, onOpenUtilities)
                )
            )
        )
    }

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
            Spacer(Modifier.height(8.dp))
        }

        sections.forEach { section ->
            item("section-${section.key}") {
                Text(
                    text = stringResource(section.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            val columns = section.columns
            val rows = section.shortcuts.chunked(columns)
            items(rows, key = { row -> row.joinToString { it.key } }) { row ->
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
                            // 4+ columns make the cards narrow, so shrink the label
                            // typography/padding to keep words from being clipped.
                            compact = columns >= 4,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Balance the last row when it doesn't fill all columns.
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            // "Otros archivos" reads as a lightweight horizontal row under the
            // Gestión grid — its label is too long for a narrow square tile.
            if (section.key == "manage") {
                item("unsupported-files-link") {
                    ManageLinkRow(
                        icon = Icons.Outlined.FolderOff,
                        label = stringResource(Res.string.unsupported_files_title),
                        onClick = onOpenUnsupportedFiles
                    )
                }
            }
        }

        item("account-settings") {
            Spacer(Modifier.height(4.dp))
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

        // Third-party data credits. Not decoration: the server image bundles
        // GeoNames' cities500 under CC BY 4.0, and redistributing it is only
        // permitted because it's attributed. The notices are written by the
        // server — it's the one that knows what it actually ships.
        if (attributions.isNotEmpty()) {
            item("attributions") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (attribution in attributions) {
                        Text(
                            text = attribution.notice,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Full-width horizontal row under the Gestión grid (icon + label + chevron).
 *  Used for entries whose labels don't fit a narrow square tile. */
@Composable
private fun ManageLinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    badgeCount: Int = 0,
    compact: Boolean = false
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
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 8.dp else 12.dp),
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
                    IconPill(icon = icon, compact = compact)
                }
            } else {
                IconPill(icon = icon, compact = compact)
            }
            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelSmall
                        else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Circular tinted badge for shortcut and settings icons — mirrors the PWA's brand-accented icons. */
@Composable
private fun IconPill(icon: ImageVector, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier
            .size(if (compact) 34.dp else 40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(if (compact) 18.dp else 22.dp)
        )
    }
}
