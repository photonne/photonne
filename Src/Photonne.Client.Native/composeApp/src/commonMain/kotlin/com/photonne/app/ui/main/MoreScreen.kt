package com.photonne.app.ui.main

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.IconButton
import com.photonne.app.ui.theme.PhotonneColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photonne.app.PhotonneVersion
import com.photonne.app.data.models.Attribution
import com.photonne.app.data.models.UserDto
import com.photonne.app.resources.Res
import com.photonne.app.resources.compat_card_client_too_old
import com.photonne.app.resources.compat_card_download
import com.photonne.app.resources.compat_card_server_too_old
import com.photonne.app.resources.compat_card_title
import com.photonne.app.resources.account_section_profile
import com.photonne.app.resources.account_settings_title
import com.photonne.app.resources.action_logout
import com.photonne.app.resources.administration_title
import com.photonne.app.resources.archive_title
import com.photonne.app.resources.backup_pending_count
import com.photonne.app.resources.device_backup_title
import com.photonne.app.resources.more_section_actions
import com.photonne.app.resources.more_section_manage
import com.photonne.app.resources.notifications_title
import com.photonne.app.resources.tab_more
import com.photonne.app.resources.upload_title
import com.photonne.app.resources.favorites_title
import com.photonne.app.resources.trash_title
import com.photonne.app.resources.my_links_title
import com.photonne.app.resources.utilities_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.photonne.app.ui.util.PlatformVerticalScrollbar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import com.photonne.app.data.version.AppVersionStore
import com.photonne.app.data.version.ServerCompatibility
import com.photonne.app.data.version.clientUpdateUrl
import com.photonne.app.data.version.isNewerVersion
import com.photonne.app.ui.util.openExternalUrl
import org.koin.compose.koinInject
import com.photonne.app.ui.theme.Spacing
import com.photonne.app.ui.theme.contentWidth
import com.photonne.app.ui.theme.IconCircle
import com.photonne.app.ui.theme.SectionHeader
import com.photonne.app.ui.theme.SettingsGroup
import com.photonne.app.ui.theme.SettingsItem
import com.photonne.app.ui.theme.SettingsTrailing

/**
 * A destination on the More tab. Each entry resolves to a subscreen in [App]
 * (Favorites, Archive, Trash, …). `badgeCount` > 0 renders a Material badge
 * before the chevron — or, when [countLabelRes] is set, that text with the
 * count ("12 pendientes").
 */
private data class MoreShortcut(
    val key: String,
    val labelRes: StringResource,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val badgeCount: Int = 0,
    val countLabelRes: StringResource? = null
)

/** A titled group of [MoreShortcut]s rendered as rows inside one card. */
private data class MoreSection(
    val key: String,
    val titleRes: StringResource,
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
    onOpenMyLinks: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenDeviceBackup: () -> Unit,
    /** Files still to back up, shown on the row so a stalled backup is
     *  visible without opening the screen. */
    backupPendingCount: Int = 0,
    onOpenNotifications: () -> Unit,
    notificationsUnreadCount: Int = 0,
    onOpenAccountSettings: () -> Unit,
    onOpenAdministration: (() -> Unit)? = null,
    onOpenUpload: () -> Unit = {},
    onChromeVisibleChange: (Boolean) -> Unit = {},
    /** Third-party data notices from the server, shown under the version. Empty
     *  when the server bundles none — see [com.photonne.app.data.models.Attribution]. */
    attributions: List<Attribution> = emptyList()
) {
    // Library destinations as a 2×2 grid of wide tiles — always full, so no
    // entry has to fall out of the grid. Browsing by grouping (People / Map /
    // Scenes / Objects) lives in the Albums tab's "Explorar" row.
    val library = remember(onOpenFavorites, onOpenMyLinks, onOpenArchived, onOpenTrash) {
        listOf(
            MoreShortcut("favorites", Res.string.favorites_title, Icons.Outlined.FavoriteBorder, onOpenFavorites),
            MoreShortcut("my-links", Res.string.my_links_title, Icons.Outlined.Share, onOpenMyLinks),
            MoreShortcut("archive", Res.string.archive_title, Icons.Outlined.Archive, onOpenArchived),
            MoreShortcut("trash", Res.string.trash_title, Icons.Outlined.Delete, onOpenTrash)
        )
    }
    // Everything else is a row inside a titled card. Upload lives in the top
    // bar (it's an action, not a destination).
    val sections = remember(
        onOpenUtilities,
        onOpenDeviceBackup,
        backupPendingCount,
        onOpenNotifications,
        notificationsUnreadCount,
        onOpenAccountSettings,
        onOpenAdministration
    ) {
        listOf(
            MoreSection(
                key = "actions",
                titleRes = Res.string.more_section_actions,
                shortcuts = listOf(
                    MoreShortcut(
                        "device-backup",
                        Res.string.device_backup_title,
                        Icons.Outlined.CloudUpload,
                        onOpenDeviceBackup,
                        badgeCount = backupPendingCount,
                        countLabelRes = Res.string.backup_pending_count
                    ),
                    MoreShortcut(
                        "notifications",
                        Res.string.notifications_title,
                        Icons.Outlined.Notifications,
                        onOpenNotifications,
                        badgeCount = notificationsUnreadCount
                    ),
                    MoreShortcut("utilities", Res.string.utilities_title, Icons.Outlined.Build, onOpenUtilities)
                )
            ),
            MoreSection(
                key = "manage",
                titleRes = Res.string.more_section_manage,
                shortcuts = listOfNotNull(
                    MoreShortcut(
                        "account-settings",
                        Res.string.account_settings_title,
                        Icons.Outlined.Settings,
                        onOpenAccountSettings
                    ),
                    onOpenAdministration?.let { handler ->
                        MoreShortcut(
                            "administration",
                            Res.string.administration_title,
                            Icons.Outlined.AdminPanelSettings,
                            handler
                        )
                    }
                )
            )
        )
    }

    val hazeState = remember { HazeState() }
    val listState = rememberLazyListState()
    val reservedTop = subscreenChromeReservedTop()
    // Aviso de actualización: hay una release publicada (con instaladores) más
    // nueva que este cliente. No vale comparar con el servidor: se despliega en
    // cada push y va por delante de las releases. Solo en las plataformas con
    // URL de descarga (escritorio).
    val versionStore: AppVersionStore = koinInject()
    val latestRelease by versionStore.latestRelease.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (clientUpdateUrl != null) versionStore.refreshLatestRelease()
    }
    val updateAvailable = clientUpdateUrl != null &&
        isNewerVersion(latestRelease?.latestVersion, PhotonneVersion)
    val updateUrl = latestRelease?.releaseUrl?.takeIf { it.isNotBlank() } ?: clientUpdateUrl
    val compatibility by versionStore.compatibility.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        // Igual que Fotos/Álbumes/Carpetas: la lista dibuja a sangre y reserva el
        // cromo flotante arriba y el hueco de la nav flotante abajo.
        contentPadding = PaddingValues(
            top = 24.dp + reservedTop,
            bottom = 24.dp + floatingNavBarReservedHeight()
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        item("header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                // Toda la cabecera abre el perfil: antes era texto inerte y el
                // perfil quedaba dos pantallas más allá.
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(
                            onClickLabel = stringResource(Res.string.account_section_profile),
                            onClick = onOpenProfile
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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
            }
        }

        library.chunked(2).forEach { row ->
            item("library-${row.joinToString { it.key }}") {
                Row(
                    modifier = Modifier
                        .contentWidth()
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    row.forEach { shortcut ->
                        MoreLibraryTile(
                            label = stringResource(shortcut.labelRes),
                            icon = shortcut.icon,
                            onClick = shortcut.onClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        sections.forEach { section ->
            item("section-${section.key}") {
                SectionHeader(
                    text = stringResource(section.titleRes),
                    modifier = Modifier
                        .contentWidth()
                        .padding(horizontal = Spacing.lg)
                )
            }
            item("group-${section.key}") {
                MoreRowGroup(shortcuts = section.shortcuts)
            }
        }

        // Detalle de la píldora global de incompatibilidad: qué versión tiene
        // cada lado y cuál hay que actualizar.
        val incompatible = compatibility
        if (incompatible != ServerCompatibility.Compatible) {
            item("compatibility") {
                Card(
                    modifier = Modifier
                        .contentWidth()
                        .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = PhotonneColors.warningContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = stringResource(Res.string.compat_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = PhotonneColors.onWarningContainer
                        )
                        Text(
                            text = when (incompatible) {
                                is ServerCompatibility.ClientTooOld -> stringResource(
                                    Res.string.compat_card_client_too_old,
                                    incompatible.minClientVersion,
                                    incompatible.clientVersion
                                )
                                is ServerCompatibility.ServerTooOld -> stringResource(
                                    Res.string.compat_card_server_too_old,
                                    incompatible.minServerVersion,
                                    incompatible.serverVersion
                                )
                                ServerCompatibility.Compatible -> ""
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = PhotonneColors.onWarningContainer
                        )
                        if (incompatible is ServerCompatibility.ClientTooOld && updateUrl != null) {
                            TextButton(
                                onClick = { openExternalUrl(updateUrl) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = PhotonneColors.onWarningContainer
                                )
                            ) {
                                Text(stringResource(Res.string.compat_card_download))
                            }
                        }
                    }
                }
            }
        }

        if (updateAvailable) {
            item("update") {
                Card(
                    modifier = Modifier
                        .contentWidth()
                        .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Hay una versión nueva de Photonne (v${latestRelease?.latestVersion.orEmpty()})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { updateUrl?.let(::openExternalUrl) }) {
                            Text("Descargar actualización")
                        }
                    }
                }
            }
        }

        item("logout") {
            // Enlace discreto en rojo, separado de los destinos de arriba y de
            // la versión de abajo: salir no es un destino más.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg, bottom = Spacing.xl),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = onLogout,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(stringResource(Res.string.action_logout))
                }
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
                    .padding(horizontal = Spacing.xl)
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
                        .padding(horizontal = Spacing.xl, vertical = 6.dp),
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
        PlatformVerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )

        SubscreenFloatingChrome(
            title = stringResource(Res.string.tab_more),
            onBack = null,
            scroll = SubscreenScroll(
                firstVisibleItemIndex = { listState.firstVisibleItemIndex },
                firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
                isScrollInProgress = { listState.isScrollInProgress },
                scrollToTopMinIndex = 4,
                onScrollToTop = {
                    if (listState.firstVisibleItemIndex > 8) listState.scrollToItem(8)
                    listState.animateScrollToItem(0)
                }
            ),
            hazeState = hazeState,
            onChromeVisibleChange = onChromeVisibleChange,
            actions = {
                IconButton(onClick = onOpenUpload) {
                    Icon(
                        Icons.Outlined.AddPhotoAlternate,
                        contentDescription = stringResource(Res.string.upload_title)
                    )
                }
            }
        )
    }
}

/** Wide library tile (icon pill + label on one line) for the 2×2 grid. */
@Composable
private fun MoreLibraryTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconCircle(icon = icon, compact = true)
            Spacer(Modifier.size(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One card holding a section's rows, split by inset dividers. */
@Composable
private fun MoreRowGroup(shortcuts: List<MoreShortcut>) {
    SettingsGroup(
        modifier = Modifier
            .contentWidth()
            .padding(horizontal = Spacing.lg)
    ) {
        shortcuts.forEachIndexed { index, shortcut ->
            SettingsItem(
                headline = stringResource(shortcut.labelRes),
                leadingIcon = shortcut.icon,
                onClick = shortcut.onClick,
                headlineMaxLines = 1,
                showDivider = index > 0,
                trailing = if (shortcut.badgeCount > 0) {
                    val countLabelRes = shortcut.countLabelRes
                    if (countLabelRes != null) {
                        SettingsTrailing.Value(stringResource(countLabelRes, shortcut.badgeCount))
                    } else {
                        SettingsTrailing.Custom {
                            Badge {
                                Text(if (shortcut.badgeCount > 99) "99+" else shortcut.badgeCount.toString())
                            }
                        }
                    }
                } else null
            )
        }
    }
}
